// HabitFlow browser interactions. Database-backed actions still use the Java servlets.
(function () {
    'use strict';

    const body = document.body;
    const base = body.dataset.base || '/';
    const cards = Array.from(document.querySelectorAll('.habit-card'));
    const history = readStore('hf-history-v1', {});
    const metadata = readStore('hf-metadata-v1', {});
    const focusData = readStore('hf-focus-v1', { totalMinutes: 0, byHabit: {}, byDay: {} });
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
    let timerMinutes = 25;
    let timerRemaining = timerMinutes * 60;
    let timerElapsed = 0;
    let timerRunning = false;
    let timerHandle = null;

    function readStore(key, fallback) {
        try {
            const value = JSON.parse(localStorage.getItem(key) || 'null');
            return value && typeof value === 'object' ? value : fallback;
        } catch (error) {
            return fallback;
        }
    }
    function writeStore(key, value) {
        try { localStorage.setItem(key, JSON.stringify(value)); return true; }
        catch (error) { announce('Browser storage is unavailable. This change may not be saved.'); return false; }
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
        if (history[historyKey]) delete history[historyKey];
        else history[historyKey] = true;
        writeStore('hf-history-v1', history);
        renderAll();
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
        button.addEventListener('click', function () {
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
        table.replaceChildren();
        const thead = document.createElement('thead');
        const headerRow = document.createElement('tr');
        const habitHeading = document.createElement('th');
        habitHeading.textContent = 'Habit';
        headerRow.appendChild(habitHeading);
        weekDates.forEach(function (date) {
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
            cell.colSpan = 9;
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
            weekDates.forEach(function (date) {
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
        weekDates.forEach(function (date) {
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
        document.getElementById('focus-total').textContent = (Number(focusData.totalMinutes) || 0) + ' min';
        document.getElementById('timer-side-total').textContent = (Number(focusData.totalMinutes) || 0) + ' min';

        const categoryRoot = document.getElementById('category-breakdown');
        categoryRoot.replaceChildren();
        const categoryColors = { Mind: '#7C3AED', Body: '#F08C46', Career: '#D41514', Wellness: '#0FA77A', Learning: '#2583D8' };
        ['Mind', 'Body', 'Career', 'Wellness', 'Learning'].forEach(function (category) {
            const count = cards.filter(function (card) { return card.dataset.category === category; }).length;
            const row = document.createElement('div');
            row.className = 'category-row';
            const label = document.createElement('span');
            label.textContent = category;
            const track = document.createElement('span');
            track.className = 'category-track';
            const fill = document.createElement('span');
            fill.style.width = (cards.length ? Math.round(count / cards.length * 100) : 0) + '%';
            fill.style.setProperty('--bar-color', categoryColors[category]);
            track.appendChild(fill);
            const value = document.createElement('strong');
            value.textContent = String(count);
            row.append(label, track, value);
            categoryRoot.appendChild(row);
        });

        const leaderboard = document.getElementById('leaderboard');
        leaderboard.replaceChildren();
        const ranked = cards.slice().sort(function (a, b) { return rate(b) - rate(a); });
        if (!ranked.length) {
            const empty = document.createElement('p');
            empty.className = 'storage-note';
            empty.textContent = 'Add habits to start your leaderboard.';
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
            score.textContent = rate(card) + '%';
            row.append(rank, name, score);
            leaderboard.appendChild(row);
        });
        renderHeatmap();
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
            themeMeta.content = document.documentElement.classList.contains('dark') ? '#080808' : '#FAF8F3';
        }
    }
    window.openModal = function () {
        const modal = document.getElementById('habit-modal');
        modal.classList.add('open');
        modal.setAttribute('aria-hidden', 'false');
        window.setTimeout(function () { modal.querySelector('input[name="name"]').focus(); }, 20);
    };
    window.closeModal = function () {
        const modal = document.getElementById('habit-modal');
        modal.classList.remove('open');
        modal.setAttribute('aria-hidden', 'true');
    };

    document.querySelectorAll('[data-view]').forEach(function (button) {
        button.addEventListener('click', function () { switchView(button.dataset.view); });
    });
    document.querySelectorAll('[data-open-view]').forEach(function (button) {
        button.addEventListener('click', function () { switchView(button.dataset.openView); });
    });
    document.querySelectorAll('[data-theme-toggle]').forEach(function (button) {
        button.addEventListener('click', function () {
            const dark = document.documentElement.classList.toggle('dark');
            document.querySelectorAll('[data-theme-toggle]').forEach(function (toggle) {
                toggle.setAttribute('aria-label', dark ? 'Switch to light mode' : 'Switch to dark mode');
            });
            syncThemeColor();
            try {
                localStorage.setItem('habitflow-theme', dark ? 'dark' : 'light');
            } catch (error) {
                announce('Theme preference could not be saved in this browser.');
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
        form.addEventListener('submit', function () {
            const card = form.closest('.habit-card');
            const note = (metaFor(card.dataset.habitId).notes || {})[todayKey] || '';
            form.querySelector('input[name="note"]').value = note;
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
            writeStore('hf-metadata-v1', metadata);
            updateTarget(card);
        });
    });
    document.querySelectorAll('.note-toggle').forEach(function (button) {
        button.addEventListener('click', function () {
            const drawer = button.closest('.habit-card').querySelector('.note-drawer');
            drawer.hidden = !drawer.hidden;
            if (!drawer.hidden) drawer.querySelector('textarea').focus();
        });
    });
    document.querySelectorAll('.save-note-btn').forEach(function (button) {
        button.addEventListener('click', function () {
            const card = button.closest('.habit-card');
            const item = metaFor(card.dataset.habitId);
            if (!item.notes) item.notes = {};
            item.notes[todayKey] = card.querySelector('.daily-note').value.trim();
            item.time = card.querySelector('.time-select').value;
            writeStore('hf-metadata-v1', metadata);
            card.querySelector('.time-label').textContent = item.time;
            applyFilters();
            announce('Note saved in this browser.');
            button.textContent = 'Saved';
            window.setTimeout(function () { button.textContent = 'Save note'; }, 1200);
        });
    });
    document.querySelectorAll('.time-select').forEach(function (select) {
        select.addEventListener('change', function () {
            const card = select.closest('.habit-card');
            metaFor(card.dataset.habitId).time = select.value;
            writeStore('hf-metadata-v1', metadata);
            card.querySelector('.time-label').textContent = select.value;
            applyFilters();
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
                const response = await fetch(base + 'habits?id=' + encodeURIComponent(button.dataset.id), { method: 'DELETE' });
                if (response.ok) {
                    const id = button.dataset.id;
                    delete metadata[id];
                    Object.keys(history).forEach(function (key) {
                        if (key.indexOf(id + '|') === 0) delete history[key];
                    });
                    delete focusData.byHabit[id];
                    writeStore('hf-metadata-v1', metadata);
                    writeStore('hf-history-v1', history);
                    writeStore('hf-focus-v1', focusData);
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
        if (event.key === 'Escape') window.closeModal();
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
    document.getElementById('timer-complete').addEventListener('click', function () {
        stopTimer();
        if (timerElapsed < 1) { announce('Start a focus session before logging time.'); return; }
        const minutes = Math.max(1, Math.round(timerElapsed / 60));
        const habitId = document.getElementById('timer-habit').value;
        focusData.totalMinutes = (Number(focusData.totalMinutes) || 0) + minutes;
        focusData.byDay[todayKey] = (Number(focusData.byDay[todayKey]) || 0) + minutes;
        if (habitId) focusData.byHabit[habitId] = (Number(focusData.byHabit[habitId]) || 0) + minutes;
        writeStore('hf-focus-v1', focusData);
        renderAll();
        announce(minutes + ' focus minutes saved in this browser.');
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

    let existingTheme = 'light';
    try { existingTheme = localStorage.getItem('habitflow-theme') || 'light'; }
    catch (error) { /* The saved theme is optional. */ }
    document.querySelectorAll('[data-theme-toggle]').forEach(function (button) {
        if (existingTheme === 'dark') button.setAttribute('aria-label', 'Switch to light mode');
    });
    syncThemeColor();
    updateTimer();
    renderAll();
})();
