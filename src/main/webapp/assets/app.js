// HabitFlow browser interactions. Database-backed actions still use the Java servlets.
(function () {
    'use strict';

    const body = document.body;
    const base = body.dataset.base || '/';
    const cards = Array.from(document.querySelectorAll('.habit-card'));
    const history = {};
    const metadata = {};
    const focusData = { totalMinutes: 0, byHabit: {}, byDay: {} };
    const csrfToken = document.querySelector('meta[name="csrf-token"]')?.content || '';
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const todayKey = dateKey(today);
    const weekDates = Array.from({ length: 7 }, function (_, i) {
        const date = new Date(today);
        date.setDate(today.getDate() - (6 - i));
        return date;
    });
    let activeCategory = 'All';
    let activeTime = 'All';
    let matrixWindow = 7;
    let timerMinutes = 25;
    let timerRemaining = timerMinutes * 60;
    let timerElapsed = 0;
    let timerRunning = false;
    let timerHandle = null;
    function setSync(message, state) {
        const node = document.getElementById('sync-status');
        if (!node) return;
        node.textContent = message;
        node.dataset.state = state || 'ok';
    }
    async function saveRemote(payload) {
        setSync('Saving…', 'busy');
        try {
            const response = await fetch(base + 'api/workspace', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': csrfToken }, body: JSON.stringify(payload) });
            const result = await response.json().catch(function () { return {}; });
            if (!response.ok) { setSync('Not saved', 'error'); announce(result.error || 'Your change could not be saved.'); return false; }
            setSync('All changes saved', 'ok');
            return true;
        } catch (error) { setSync('Sync unavailable', 'error'); announce('Could not reach HabitFlow. Your change was not saved.'); return false; }
    }
    async function loadRemote() {
        setSync('Syncing…', 'busy');
        try {
            const response = await fetch(base + 'api/workspace', { headers: { 'Accept': 'application/json' }, cache: 'no-store' });
            if (!response.ok) throw new Error('sync failed');
            const data = await response.json();
            Object.keys(data.done || {}).forEach(function (key) { history[key] = true; });
            Object.keys(data.metadata || {}).forEach(function (id) { metadata[id] = data.metadata[id] || {}; const target = metadata[id].targets || {}; metadata[id].target = target[todayKey] || null; });
            Object.assign(focusData, data.focus || {});
            focusData.byHabit = focusData.byHabit || {}; focusData.byDay = focusData.byDay || {};
            if (data.theme === 'light' || data.theme === 'dark') {
                document.documentElement.classList.toggle('dark', data.theme === 'dark');
                document.querySelectorAll('[data-theme-toggle]').forEach(function (button) { button.setAttribute('aria-label', data.theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'); });
                syncThemeColor();
            }
            setSync('All changes synced', 'ok'); renderAll();
        } catch (error) { setSync('Sync unavailable', 'error'); announce('Your account data could not be loaded. Please refresh when your connection is available.'); }
    }

    function dateKey(date) {
        return date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0') + '-' + String(date.getDate()).padStart(2, '0');
    }
    function dateFromKey(key) {
        const parts = String(key).split('-').map(Number);
        return new Date(parts[0], parts[1] - 1, parts[2]);
    }
    function dayLabel(date) { return date.toLocaleDateString('en', { weekday: 'short' }); }
    function shortDay(date) { return dayLabel(date).charAt(0); }
    function announce(message) {
        const live = document.getElementById('app-announcements');
        if (live) live.textContent = message;
    }
    function metaFor(id) {
        if (!metadata[id] || typeof metadata[id] !== 'object') metadata[id] = {};
        return metadata[id];
    }
    function doneFor(card, key) {
        if (key === todayKey) return card.dataset.completed === 'true';
        return history[card.dataset.habitId + '|' + key] === true;
    }
    function isScheduled(card, date) {
        const created = card.dataset.created ? dateFromKey(card.dataset.created) : null;
        if (created && date < created) return false;
        const day = date.getDay();
        const frequency = card.dataset.frequency || 'Daily';
        if (frequency === 'Weekdays' && (day === 0 || day === 6)) return false;
        if (frequency === 'Weekly' && created && day !== created.getDay()) return false;
        return true;
    }
    function togglePast(card, key) {
        const historyKey = card.dataset.habitId + '|' + key;
        const completed = !history[historyKey];
        saveRemote({ action: 'checkin', habitId: Number(card.dataset.habitId), date: key, completed: completed }).then(function (saved) {
            if (!saved) return;
            if (completed) history[historyKey] = true; else delete history[historyKey];
            renderAll();
        });
    }
    function submitCheckin(card) {
        const form = card.querySelector('.checkin-form');
        if (!form || card.dataset.scheduled !== 'true') return;
        const note = metaFor(card.dataset.habitId).notes || {};
        const field = form.querySelector('input[name="note"]');
        if (field) field.value = note[todayKey] || '';
        form.requestSubmit();
    }
    function dayButton(card, date, matrix) {
        const key = dateKey(date);
        const todayTile = key === todayKey;
        const scheduled = isScheduled(card, date);
        const button = document.createElement('button');
        button.type = 'button';
        button.className = (matrix ? 'matrix-cell' : 'week-day') +
            (doneFor(card, key) ? ' done' : '') + (todayTile ? ' today' : '');
        button.disabled = !scheduled;
        button.dataset.day = key;
        button.style.setProperty('--cell-color', card.dataset.color || '#D41514');
        button.setAttribute('aria-label', card.querySelector('.habit-title').textContent + ', ' +
            dayLabel(date) + ' ' + date.getDate() + (doneFor(card, key) ? ', completed' : ', not completed'));
        if (matrix) {
            button.textContent = doneFor(card, key) ? '✓' : '·';
        } else {
            const letter = document.createElement('span');
            letter.textContent = shortDay(date);
            const number = document.createElement('span');
            number.textContent = String(date.getDate());
            button.append(letter, number);
        }
        button.addEventListener('click', async function () {
            if (key === todayKey) submitCheckin(card);
            else togglePast(card, key);
        });
        return button;
    }
    function drawWeeks() {
        cards.forEach(function (card) {
            card.style.setProperty('--card-accent', card.dataset.color || '#D41514');
            const strip = card.querySelector('.week-strip');
            if (strip) {
                strip.replaceChildren();
                weekDates.forEach(function (date) { strip.appendChild(dayButton(card, date, false)); });
            }
            const item = metaFor(card.dataset.habitId);
            const time = item.time || 'Anytime';
            const timeLabel = card.querySelector('.time-label');
            const timeSelect = card.querySelector('.time-select');
            if (timeLabel) timeLabel.textContent = time;
            if (timeSelect) timeSelect.value = time;
            const note = card.querySelector('.daily-note');
            if (note) note.value = (item.notes || {})[todayKey] || '';
            updateTarget(card);
            updateFocusBadge(card);
        });
    }
    function updateTarget(card) {
        const item = metaFor(card.dataset.habitId);
        const target = item.target && item.target.date === todayKey ? item.target : { date: todayKey, count: 0, total: 1 };
        const total = Math.max(1, Number(target.total) || 1);
        const count = Math.max(0, Math.min(total, Number(target.count) || 0));
        const label = card.querySelector('.target-count');
        const fill = card.querySelector('.target-fill');
        if (label) label.textContent = count + ' / ' + total;
        if (fill) fill.style.width = Math.round(count / total * 100) + '%';
        if (count === 0 && total === 1) {
            const row = card.querySelector('.target-row');
            if (row) row.classList.add('target-idle');
        }
    }
    function renderNoteHistory(card) {
        const root = card.querySelector('.note-history');
        if (!root) return;
        root.replaceChildren();
        const notes = metaFor(card.dataset.habitId).notes || {};
        Object.keys(notes).filter(function (key) { return notes[key]; }).sort().reverse().forEach(function (key) {
            const entry = document.createElement('article');
            entry.className = 'note-history-entry';
            const date = document.createElement('time');
            date.dateTime = key;
            date.textContent = new Date(key + 'T00:00:00').toLocaleDateString('en', { month: 'short', day: 'numeric', year: 'numeric' });
            const text = document.createElement('p');
            text.textContent = notes[key];
            entry.append(date, text);
            root.appendChild(entry);
        });
        if (!root.children.length) {
            const empty = document.createElement('p');
            empty.className = 'note-history-empty';
            empty.textContent = 'No notes recorded yet. Add your first reflection above.';
            root.appendChild(empty);
        }
    }
    function updateFocusBadge(card) {
        const badge = card.querySelector('.focus-badge');
        const minutes = Number(focusData.byHabit[card.dataset.habitId]) || 0;
        if (!badge) return;
        badge.hidden = minutes <= 0;
        badge.textContent = '◷ ' + minutes + 'm focus';
    }
    function applyFilters() {
        const query = (document.getElementById('habit-search').value || '').trim().toLowerCase();
        let visible = 0;
        cards.forEach(function (card) {
            const text = (card.querySelector('.habit-title').textContent + ' ' +
                card.querySelector('.habit-description').textContent + ' ' + card.dataset.category).toLowerCase();
            const categoryOk = activeCategory === 'All' || card.dataset.category === activeCategory;
            const timeOk = activeTime === 'All' || (metaFor(card.dataset.habitId).time || 'Anytime') === activeTime;
            const show = text.includes(query) && categoryOk && timeOk;
            card.hidden = !show;
            if (show) visible++;
        });
        document.getElementById('showing-count').textContent = 'Showing ' + visible + ' of ' + cards.length;
    }
    function renderMatrix() {
        const table = document.getElementById('matrix-table');
        if (!table) return;
        const matrixDates = Array.from({ length: matrixWindow }, function (_, index) {
            const date = new Date(today);
            date.setDate(today.getDate() - (matrixWindow - index - 1));
            return date;
        });
        table.replaceChildren();
        const thead = document.createElement('thead');
        const headerRow = document.createElement('tr');
        const habitHeading = document.createElement('th');
        habitHeading.textContent = 'Habit';
        headerRow.appendChild(habitHeading);
        matrixDates.forEach(function (date) {
            const th = document.createElement('th');
            if (dateKey(date) === todayKey) th.className = 'today-col';
            const name = document.createElement('span');
            name.className = 'matrix-day-label';
            name.textContent = dayLabel(date);
            const day = document.createElement('span');
            day.className = 'matrix-day-date';
            day.textContent = String(date.getDate());
            th.append(name, day);
            headerRow.appendChild(th);
        });
        const streakHeading = document.createElement('th');
        streakHeading.textContent = 'Streak';
        headerRow.appendChild(streakHeading);
        thead.appendChild(headerRow);
        table.appendChild(thead);

        if (!cards.length) {
            const row = document.createElement('tr');
            const cell = document.createElement('td');
            cell.colSpan = matrixDates.length + 2;
            cell.className = 'empty-matrix';
            cell.textContent = 'Create a habit to see your weekly matrix.';
            row.appendChild(cell);
            table.appendChild(row);
            return;
        }

        const tbody = document.createElement('tbody');
        cards.forEach(function (card) {
            const row = document.createElement('tr');
            const nameCell = document.createElement('td');
            const name = document.createElement('span');
            name.className = 'matrix-habit-name';
            const dot = document.createElement('i');
            dot.className = 'matrix-habit-dot';
            dot.style.background = card.dataset.color || '#D41514';
            const text = document.createElement('span');
            text.textContent = card.querySelector('.habit-title').textContent;
            name.append(dot, text);
            nameCell.appendChild(name);
            row.appendChild(nameCell);
            matrixDates.forEach(function (date) {
                const cell = document.createElement('td');
                cell.appendChild(dayButton(card, date, true));
                row.appendChild(cell);
            });
            const streak = document.createElement('td');
            streak.className = 'matrix-streak';
            const streakText = card.querySelector('.habit-meta .mono');
            streak.textContent = streakText ? streakText.textContent.replace('♨', '').trim() : '0d';
            row.appendChild(streak);
            tbody.appendChild(row);
        });
        const summary = document.createElement('tr');
        summary.className = 'matrix-summary';
        const title = document.createElement('td');
        title.textContent = 'Daily completion';
        summary.appendChild(title);
        matrixDates.forEach(function (date) {
            const cell = document.createElement('td');
            cell.className = 'summary-count';
            const key = dateKey(date);
            const count = cards.filter(function (card) { return isScheduled(card, date) && doneFor(card, key); }).length;
            cell.textContent = count + '/' + cards.filter(function (card) { return isScheduled(card, date); }).length;
            summary.appendChild(cell);
        });
        const blank = document.createElement('td');
        summary.appendChild(blank);
        tbody.appendChild(summary);
        table.appendChild(tbody);
    }
    function renderDailySummary() {
        const root = document.getElementById('summary-days');
        if (!root) return;
        root.replaceChildren();
        weekDates.forEach(function (date) {
            const scheduled = cards.filter(function (card) { return isScheduled(card, date); });
            const complete = scheduled.filter(function (card) { return doneFor(card, dateKey(date)); }).length;
            const percent = scheduled.length ? Math.round(complete / scheduled.length * 100) : 0;
            const item = document.createElement('div');
            item.className = 'summary-day';
            const name = document.createElement('span');
            name.textContent = dayLabel(date);
            const bar = document.createElement('span');
            bar.className = 'summary-bar';
            const fill = document.createElement('span');
            fill.style.width = percent + '%';
            bar.appendChild(fill);
            const value = document.createElement('strong');
            value.textContent = percent + '%';
            item.append(name, bar, value);
            root.appendChild(item);
        });
    }
    function renderAnalytics() {
        const scheduledTotal = weekDates.reduce(function (total, date) {
            return total + cards.filter(function (card) { return isScheduled(card, date); }).length;
        }, 0);
        const completedTotal = weekDates.reduce(function (total, date) {
            return total + cards.filter(function (card) { return isScheduled(card, date) && doneFor(card, dateKey(date)); }).length;
        }, 0);
        document.getElementById('consistency-score').textContent =
            (scheduledTotal ? Math.round(completedTotal / scheduledTotal * 100) : 0) + '%';
        const todayScheduled = cards.filter(function (card) { return isScheduled(card, today); }).length;
        const todayCompleted = cards.filter(function (card) { return isScheduled(card, today) && doneFor(card, todayKey); }).length;
        document.getElementById('analytics-today-rate').textContent =
            (todayScheduled ? Math.round(todayCompleted / todayScheduled * 100) : 0) + '%';
        document.getElementById('timer-side-total').textContent = (Number(focusData.totalMinutes) || 0) + ' min';

        const categoryRoot = document.getElementById('category-breakdown');
        categoryRoot.replaceChildren();
        const categoryColors = { Mind: '#7C3AED', Body: '#F08C46', Career: '#D41514', Wellness: '#0FA77A', Learning: '#2583D8' };
        ['Mind', 'Body', 'Career', 'Wellness', 'Learning'].forEach(function (category) {
            const categoryCards = cards.filter(function (card) { return card.dataset.category === category; });
            const count = categoryCards.length;
            const averageRate = count ? Math.round(categoryCards.reduce(function (sum, card) {
                return sum + rate(card);
            }, 0) / count) : 0;
            const row = document.createElement('div');
            row.className = 'category-row';
            const label = document.createElement('span');
            label.textContent = category + ' (' + count + (count === 1 ? ' habit' : ' habits') + ')';
            const track = document.createElement('span');
            track.className = 'category-track';
            const fill = document.createElement('span');
            fill.style.width = averageRate + '%';
            fill.style.setProperty('--bar-color', categoryColors[category]);
            track.appendChild(fill);
            const value = document.createElement('strong');
            value.textContent = averageRate + '%';
            row.append(label, track, value);
            categoryRoot.appendChild(row);
        });

        const leaderboard = document.getElementById('leaderboard');
        leaderboard.replaceChildren();
        const ranked = cards.slice().sort(function (a, b) { return streak(b) - streak(a); });
        if (!ranked.length) {
            const empty = document.createElement('p');
            empty.className = 'storage-note';
            empty.textContent = 'Add habits to start your streak leaderboard.';
            leaderboard.appendChild(empty);
        }
        ranked.forEach(function (card, index) {
            const row = document.createElement('div');
            row.className = 'leader-row';
            const rank = document.createElement('span');
            rank.className = 'leader-rank';
            rank.textContent = String(index + 1).padStart(2, '0');
            const name = document.createElement('span');
            name.className = 'leader-name';
            name.textContent = card.querySelector('.habit-title').textContent;
            const score = document.createElement('strong');
            score.textContent = streak(card) + ' days';
            row.append(rank, name, score);
            leaderboard.appendChild(row);
        });
        renderHeatmap();
        render28DayHeatmap();
    }
    function streak(card) {
        const match = card.querySelector('.habit-meta').textContent.match(/♨\s*(\d+)d/);
        return match ? Number(match[1]) : 0;
    }
    function rate(card) {
        const match = card.querySelector('.habit-meta').textContent.match(/(\d+)%\s*month/);
        return match ? Number(match[1]) : 0;
    }
    function renderHeatmap() {
        const root = document.getElementById('heatmap');
        const months = document.getElementById('heatmap-months');
        if (!root || !months) return;
        root.replaceChildren();
        months.replaceChildren();
        const start = new Date(today);
        start.setDate(today.getDate() - 111);
        let lastMonth = -1;
        for (let i = 0; i < 112; i++) {
            const date = new Date(start);
            date.setDate(start.getDate() + i);
            if (date.getMonth() !== lastMonth) {
                const label = document.createElement('span');
                label.textContent = date.toLocaleDateString('en', { month: 'short' });
                label.style.gridColumn = String(Math.floor(i / 7) + 1);
                months.appendChild(label);
                lastMonth = date.getMonth();
            }
            const count = cards.filter(function (card) { return isScheduled(card, date) && doneFor(card, dateKey(date)); }).length;
            const level = cards.length ? Math.min(4, Math.ceil(count / cards.length * 4)) : 0;
            const cell = document.createElement('span');
            cell.className = 'heat-cell' + (level ? ' level-' + level : '') + (date > today ? ' empty' : '');
            cell.title = date.toLocaleDateString('en', { month: 'short', day: 'numeric' }) + ': ' + count + ' habits completed';
            root.appendChild(cell);
        }
    }
    function render28DayHeatmap() {
        const root = document.getElementById('intensity-heatmap');
        if (!root) return;
        root.replaceChildren();
        for (let offset = 27; offset >= 0; offset--) {
            const date = new Date(today);
            date.setDate(today.getDate() - offset);
            const scheduled = cards.filter(function (card) { return isScheduled(card, date); });
            const completed = scheduled.filter(function (card) { return doneFor(card, dateKey(date)); }).length;
            const percent = scheduled.length ? Math.round(completed / scheduled.length * 100) : 0;
            const tile = document.createElement('div');
            tile.className = 'intensity-tile' + (percent >= 80 ? ' level-4' : percent >= 50 ? ' level-3' : percent >= 25 ? ' level-2' : percent > 0 ? ' level-1' : '');
            tile.title = date.toLocaleDateString('en', { month: 'short', day: 'numeric' }) + ': ' + completed + '/' + scheduled.length + ' habits completed';
            const month = document.createElement('span');
            month.textContent = date.toLocaleDateString('en', { month: 'short' });
            const day = document.createElement('strong');
            day.textContent = String(date.getDate());
            tile.append(month, day);
            root.appendChild(tile);
        }
    }
    function renderAll() {
        drawWeeks();
        applyFilters();
        renderMatrix();
        renderDailySummary();
        renderAnalytics();
        const completed = cards.filter(function (card) { return card.dataset.completed === 'true'; }).length;
        const percent = cards.length ? Math.round(completed / cards.length * 100) : 0;
        document.getElementById('momentum-percent').textContent = percent + '%';
        document.getElementById('momentum-fill').style.width = percent + '%';
        document.getElementById('daily-rate').textContent = percent + '%';
        const title = document.getElementById('insight-title');
        const message = document.getElementById('insight-message');
        if (completed === cards.length && cards.length) {
            title.textContent = 'You showed up for yourself';
            message.textContent = 'All your habits are checked in today. Take a moment to enjoy that progress.';
        } else if (completed > 0) {
            title.textContent = 'You are building momentum';
            message.textContent = completed + ' of ' + cards.length + ' habits are done. One small step can keep the day moving.';
        }
    }

    function switchView(view) {
        document.querySelectorAll('.workspace-view').forEach(function (panel) {
            panel.classList.toggle('active', panel.id === 'view-' + view);
        });
        document.querySelectorAll('[data-view]').forEach(function (button) {
            button.classList.toggle('active', button.dataset.view === view);
        });
        if (view === 'matrix') renderMatrix();
        if (view === 'analytics') renderAnalytics();
        window.scrollTo({ top: 0, behavior: 'smooth' });
    }
    function syncThemeColor() {
        const themeMeta = document.querySelector('meta[name="theme-color"]');
        if (themeMeta) {
            themeMeta.content = document.documentElement.classList.contains('dark') ? '#080808' : '#F8FAFC';
        }
    }
    window.openModal = function () {
        const modal = document.getElementById('habit-modal');
        const form = document.getElementById('add-habit');
        form.reset();
        document.getElementById('edit-habit-id').value = '';
        document.getElementById('edit-target-count').value = '0';
        document.getElementById('edit-target-total').value = '1';
        document.getElementById('edit-settings').hidden = true;
        document.getElementById('habit-modal-kicker').textContent = 'NEW ROUTINE';
        document.getElementById('modal-title').textContent = 'Create a habit';
        document.getElementById('habit-submit').innerHTML = 'Add to my routine <span>→</span>';
        modal.classList.add('open');
        modal.setAttribute('aria-hidden', 'false');
        window.setTimeout(function () { modal.querySelector('input[name="name"]').focus(); }, 20);
    };
    document.querySelectorAll('.edit-btn').forEach(function (button) {
        button.addEventListener('click', function () {
            const card = button.closest('.habit-card');
            const id = card.dataset.habitId;
            const item = metaFor(id);
            const target = item.target && item.target.date === todayKey ? item.target : { count: 0, total: 1 };
            const form = document.getElementById('add-habit');
            form.reset();
            document.getElementById('edit-habit-id').value = id;
            document.getElementById('edit-target-count').value = String(Math.min(Number(target.count) || 0, Number(target.total) || 1));
            document.getElementById('edit-target-total').value = String(Number(target.total) || 1);
            document.getElementById('edit-time-of-day').value = item.time || 'Anytime';
            form.elements.namedItem('name').value = card.querySelector('.habit-title').textContent.trim();
            form.elements.namedItem('description').value = card.querySelector('.habit-description').textContent.trim();
            form.elements.namedItem('category').value = card.dataset.category;
            form.elements.namedItem('frequency').value = card.dataset.frequency;
            const color = card.dataset.color.toUpperCase();
            const colorInput = Array.from(form.querySelectorAll('input[name="color"]')).find(function (input) { return input.value.toUpperCase() === color; });
            if (colorInput) colorInput.checked = true;
            document.getElementById('edit-settings').hidden = false;
            document.getElementById('habit-modal-kicker').textContent = 'UPDATE ROUTINE';
            document.getElementById('modal-title').textContent = 'Edit habit';
            document.getElementById('habit-submit').innerHTML = 'Save changes <span>→</span>';
            const modal = document.getElementById('habit-modal');
            modal.classList.add('open');
            modal.setAttribute('aria-hidden', 'false');
            form.elements.namedItem('name').focus();
        });
    });
    document.getElementById('edit-target-total').addEventListener('input', function (event) {
        const total = Math.max(1, Math.min(50, Number(event.target.value) || 1));
        const countInput = document.getElementById('edit-target-count');
        countInput.value = String(Math.min(Number(countInput.value) || 0, total));
    });
    window.closeModal = function () {
        const modal = document.getElementById('habit-modal');
        modal.classList.remove('open');
        modal.setAttribute('aria-hidden', 'true');
    };
    const profileToggle = document.getElementById('profile-menu-toggle');
    const profileDropdown = document.getElementById('profile-dropdown');
    const accountModal = document.getElementById('account-settings-modal');
    function openAccountSettings() {
        if (profileDropdown) profileDropdown.hidden = true;
        if (profileToggle) profileToggle.setAttribute('aria-expanded', 'false');
        accountModal.classList.add('open'); accountModal.setAttribute('aria-hidden', 'false');
    }
    if (profileToggle && profileDropdown) profileToggle.addEventListener('click', function () {
        const show = profileDropdown.hidden; profileDropdown.hidden = !show; profileToggle.setAttribute('aria-expanded', String(show));
    });
    document.querySelectorAll('#open-account-settings,#header-account-settings').forEach(function (button) { button.addEventListener('click', openAccountSettings); });
    document.getElementById('close-account-settings').addEventListener('click', function () { accountModal.classList.remove('open'); accountModal.setAttribute('aria-hidden', 'true'); });
    accountModal.addEventListener('click', function (event) { if (event.target === accountModal) { accountModal.classList.remove('open'); accountModal.setAttribute('aria-hidden', 'true'); } });

    document.querySelectorAll('[data-view]').forEach(function (button) {
        button.addEventListener('click', function () { switchView(button.dataset.view); });
    });
    document.querySelectorAll('[data-matrix-days]').forEach(function (button) {
        button.addEventListener('click', function () {
            matrixWindow = Number(button.dataset.matrixDays) === 14 ? 14 : 7;
            document.querySelectorAll('[data-matrix-days]').forEach(function (option) {
                const active = Number(option.dataset.matrixDays) === matrixWindow;
                option.classList.toggle('active', active);
                option.setAttribute('aria-pressed', String(active));
            });
            document.getElementById('matrix-window-label').textContent = matrixWindow + ' DAYS';
            renderMatrix();
        });
    });
    document.querySelectorAll('[data-open-view]').forEach(function (button) {
        button.addEventListener('click', function () { switchView(button.dataset.openView); });
    });
    document.querySelectorAll('[data-theme-toggle]').forEach(function (button) {
        button.addEventListener('click', async function () {
            const dark = document.documentElement.classList.toggle('dark');
            document.querySelectorAll('[data-theme-toggle]').forEach(function (toggle) {
                toggle.setAttribute('aria-label', dark ? 'Switch to light mode' : 'Switch to dark mode');
            });
            syncThemeColor();
            if (!(await saveRemote({ action: 'theme', theme: dark ? 'dark' : 'light' }))) {
                document.documentElement.classList.toggle('dark', !dark);
                document.querySelectorAll('[data-theme-toggle]').forEach(function (toggle) { toggle.setAttribute('aria-label', dark ? 'Switch to dark mode' : 'Switch to light mode'); });
                syncThemeColor();
            }
        });
    });
    document.querySelectorAll('[data-category-filter]').forEach(function (button) {
        button.addEventListener('click', function () {
            activeCategory = button.dataset.categoryFilter;
            document.querySelectorAll('[data-category-filter]').forEach(function (item) { item.classList.toggle('active', item === button); });
            applyFilters();
        });
    });
    document.querySelectorAll('[data-time-filter]').forEach(function (button) {
        button.addEventListener('click', function () {
            activeTime = button.dataset.timeFilter;
            document.querySelectorAll('[data-time-filter]').forEach(function (item) { item.classList.toggle('active', item === button); });
            applyFilters();
        });
    });
    document.getElementById('habit-search').addEventListener('input', applyFilters);
    document.querySelectorAll('.checkin-form').forEach(function (form) {
        form.addEventListener('submit', async function (event) {
            event.preventDefault();
            const card = form.closest('.habit-card');
            const note = (metaFor(card.dataset.habitId).notes || {})[todayKey] || '';
            if (await saveRemote({ action: 'checkin', habitId: Number(card.dataset.habitId), date: todayKey, completed: card.dataset.completed !== 'true', note: note })) window.location.reload();
        });
    });
    document.querySelectorAll('.target-step').forEach(function (button) {
        button.addEventListener('click', function () {
            const card = button.closest('.habit-card');
            const item = metaFor(card.dataset.habitId);
            const current = item.target && item.target.date === todayKey ? item.target : { date: todayKey, count: 0, total: 1 };
            current.total = Math.max(1, Math.min(50, Number(current.total) || 1));
            current.count = Math.max(0, Math.min(current.total, (Number(current.count) || 0) + Number(button.dataset.targetChange)));
            item.target = current;
            item.targets = item.targets || {}; item.targets[todayKey] = current;
            saveRemote({ action: 'metadata', habitId: Number(card.dataset.habitId), date: todayKey, time: item.time || 'Anytime', count: current.count, total: current.total }).then(function (saved) { if (saved) updateTarget(card); else renderAll(); });
        });
    });
    document.querySelectorAll('.note-toggle').forEach(function (button) {
        button.addEventListener('click', async function () {
            const card = button.closest('.habit-card');
            const drawer = card.querySelector('.note-drawer');
            const shouldOpen = drawer.hidden;
            document.querySelectorAll('.note-drawer').forEach(function (other) { other.hidden = true; });
            drawer.querySelector('.note-habit-name').textContent = card.querySelector('.habit-title').textContent;
            drawer.hidden = !shouldOpen;
            if (!drawer.hidden) {
                renderNoteHistory(card);
                drawer.querySelector('textarea').focus();
            }
        });
    });
    document.querySelectorAll('.note-close').forEach(function (button) {
        button.addEventListener('click', function () { button.closest('.note-drawer').hidden = true; });
    });
    document.querySelectorAll('.note-drawer').forEach(function (drawer) {
        drawer.addEventListener('click', function (event) {
            if (event.target === drawer) drawer.hidden = true;
        });
    });
    document.addEventListener('keydown', function (event) {
        if (event.key !== 'Escape') return;
        document.querySelectorAll('.note-drawer').forEach(function (drawer) { drawer.hidden = true; });
        if (document.getElementById('habit-modal').classList.contains('open')) window.closeModal();
    });
    document.querySelectorAll('.save-note-btn').forEach(function (button) {
        button.addEventListener('click', async function () {
            const card = button.closest('.habit-card');
            const item = metaFor(card.dataset.habitId);
            const noteText = card.querySelector('.daily-note').value.trim();
            const time = card.querySelector('.time-select').value;
            const target = item.target || { date: todayKey, count: 0, total: 1 };
            if (!(await saveRemote({ action: 'note', habitId: Number(card.dataset.habitId), date: todayKey, note: noteText }))) return;
            if (!(await saveRemote({ action: 'metadata', habitId: Number(card.dataset.habitId), date: todayKey, time: time, count: target.count, total: target.total }))) return;
            item.notes = item.notes || {}; item.notes[todayKey] = noteText; item.time = time; item.target = target;
            card.querySelector('.time-label').textContent = item.time;
            renderNoteHistory(card);
            applyFilters();
            announce('Note saved to your account.');
            button.textContent = 'Saved';
            window.setTimeout(function () { button.textContent = 'Save note'; }, 1200);
        });
    });
    document.querySelectorAll('.time-select').forEach(function (select) {
        select.addEventListener('change', async function () {
            const card = select.closest('.habit-card');
            const item = metaFor(card.dataset.habitId), target = item.target || { date: todayKey, count: 0, total: 1 };
            if (await saveRemote({ action: 'metadata', habitId: Number(card.dataset.habitId), date: todayKey, time: select.value, count: target.count, total: target.total })) { item.time = select.value; item.target = target; card.querySelector('.time-label').textContent = select.value; applyFilters(); }
            else select.value = item.time || 'Anytime';
        });
    });
    document.querySelectorAll('.focus-habit').forEach(function (button) {
        button.addEventListener('click', function () {
            const card = button.closest('.habit-card');
            switchView('timer');
            document.getElementById('timer-habit').value = card.dataset.habitId;
        });
    });
    document.querySelectorAll('.delete-btn').forEach(function (button) {
        button.addEventListener('click', async function () {
            if (!window.confirm('Delete this habit and its check-in history?')) return;
            try {
                const response = await fetch(base + 'habits?id=' + encodeURIComponent(button.dataset.id), { method: 'DELETE', headers: { 'X-CSRF-Token': csrfToken } });
                if (response.ok) {
                    window.location.reload();
                }
                else window.alert('The habit could not be deleted. Please try again.');
            } catch (error) {
                window.alert('Could not connect to the server. Please try again.');
            }
        });
    });
    document.querySelectorAll('.blueprint-add').forEach(function (button) {
        button.addEventListener('click', function () {
            const form = document.createElement('form');
            form.method = 'post';
            form.action = base + 'habits';
            const csrf = document.createElement('input'); csrf.type = 'hidden'; csrf.name = 'csrfToken'; csrf.value = csrfToken; form.appendChild(csrf);
            [['name', button.dataset.name], ['description', button.dataset.description],
                ['category', button.dataset.category], ['frequency', button.dataset.frequency],
                ['color', button.dataset.color]].forEach(function (pair) {
                const input = document.createElement('input');
                input.type = 'hidden';
                input.name = pair[0];
                input.value = pair[1];
                form.appendChild(input);
            });
            document.body.appendChild(form);
            form.submit();
        });
    });
    const dismiss = document.querySelector('.alert-dismiss');
    if (dismiss) dismiss.addEventListener('click', function () { document.getElementById('flash-banner').remove(); });
    document.getElementById('habit-modal').addEventListener('click', function (event) {
        if (event.target.id === 'habit-modal') window.closeModal();
    });
    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape') { window.closeModal(); accountModal.classList.remove('open'); accountModal.setAttribute('aria-hidden', 'true'); if (profileDropdown) profileDropdown.hidden = true; }
    });
    document.getElementById('live-date').textContent = new Intl.DateTimeFormat('en-GB', {
        day: '2-digit', month: 'short', year: 'numeric'
    }).format(new Date());

    function updateTimer() {
        const minutes = Math.floor(timerRemaining / 60);
        const seconds = timerRemaining % 60;
        document.getElementById('timer-display').textContent = String(minutes).padStart(2, '0') + ':' + String(seconds).padStart(2, '0');
        const ratio = timerMinutes ? timerRemaining / (timerMinutes * 60) : 1;
        document.getElementById('ring-progress').style.strokeDashoffset = String(590.62 * (1 - ratio));
        document.getElementById('timer-start').textContent = timerRunning ? 'Pause focus' : 'Start focus';
        document.getElementById('timer-state').textContent = timerRunning ? 'FOCUS SESSION IN PROGRESS' : (timerRemaining === 0 ? 'SESSION TIME IS UP' : (timerElapsed ? 'SESSION PAUSED' : 'READY WHEN YOU ARE'));
    }
    function stopTimer() {
        timerRunning = false;
        if (timerHandle) window.clearInterval(timerHandle);
        timerHandle = null;
        updateTimer();
    }
    document.querySelectorAll('.preset-btn').forEach(function (button) {
        button.addEventListener('click', function () {
            if (timerRunning) return;
            timerMinutes = Number(button.dataset.minutes);
            timerRemaining = timerMinutes * 60;
            timerElapsed = 0;
            document.querySelectorAll('.preset-btn').forEach(function (item) { item.classList.toggle('active', item === button); });
            updateTimer();
        });
    });
    document.getElementById('timer-start').addEventListener('click', function () {
        if (timerRunning) { stopTimer(); return; }
        if (timerRemaining <= 0) return;
        timerRunning = true;
        timerHandle = window.setInterval(function () {
            timerRemaining = Math.max(0, timerRemaining - 1);
            timerElapsed++;
            updateTimer();
            if (timerRemaining === 0) stopTimer();
        }, 1000);
        updateTimer();
    });
    document.getElementById('timer-reset').addEventListener('click', function () {
        stopTimer();
        timerRemaining = timerMinutes * 60;
        timerElapsed = 0;
        updateTimer();
    });
    document.getElementById('timer-complete').addEventListener('click', async function () {
        stopTimer();
        if (timerElapsed < 1) { announce('Start a focus session before logging time.'); return; }
        const minutes = Math.max(1, Math.round(timerElapsed / 60));
        const habitId = document.getElementById('timer-habit').value;
        const startedAt = new Date(Date.now() - timerElapsed * 1000).toISOString();
        if (!(await saveRemote({ action: 'focus', habitId: habitId || null, startedAt: startedAt, minutes: minutes }))) return;
        focusData.totalMinutes = (Number(focusData.totalMinutes) || 0) + minutes;
        focusData.byDay[todayKey] = (Number(focusData.byDay[todayKey]) || 0) + minutes;
        if (habitId) focusData.byHabit[habitId] = (Number(focusData.byHabit[habitId]) || 0) + minutes;
        renderAll();
        announce(minutes + ' focus minutes saved to your account.');
        const card = cards.find(function (item) { return item.dataset.habitId === habitId; });
        if (card && card.dataset.completed !== 'true' && card.dataset.scheduled === 'true') {
            submitCheckin(card);
        } else {
            document.getElementById('timer-state').textContent = minutes + ' MINUTES LOGGED';
            timerRemaining = timerMinutes * 60;
            timerElapsed = 0;
            updateTimer();
        }
    });
    document.getElementById('timer-habit').addEventListener('change', function () {
        document.getElementById('timer-state').textContent = 'READY WHEN YOU ARE';
    });

    document.getElementById('export-data').addEventListener('click', function () {
        const habits = cards.map(function (card) {
            const item = metaFor(card.dataset.habitId);
            return {
                id: card.dataset.habitId,
                name: card.querySelector('.habit-title').textContent,
                description: card.querySelector('.habit-description').textContent,
                category: card.dataset.category,
                frequency: card.dataset.frequency,
                color: card.dataset.color,
                completionRate: rate(card),
                completedToday: card.dataset.completed === 'true',
                browserNotes: item.notes || {},
                timeOfDay: item.time || 'Anytime',
                dailyTarget: item.target || null,
                focusMinutes: Number(focusData.byHabit[card.dataset.habitId]) || 0
            };
        });
        const payload = {
            exportedAt: new Date().toISOString(),
            habits: habits,
            browserHistory: history,
            focus: focusData
        };
        const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = 'habitflow-data-' + todayKey + '.json';
        document.body.appendChild(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(url);
        announce('Routine data exported.');
    });

    const existingTheme = document.documentElement.classList.contains('dark') ? 'dark' : 'light';
    document.querySelectorAll('[data-theme-toggle]').forEach(function (button) {
        button.setAttribute('aria-label', existingTheme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode');
    });
    syncThemeColor();
    updateTimer();
    renderAll();
    loadRemote();
})();
