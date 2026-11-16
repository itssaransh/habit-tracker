<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <meta name="theme-color" content="#F8FAFC">
  <title>HabitFlow | <c:out value="${mode eq 'register' ? 'Create Account' : (mode eq 'forgot' ? 'Password Recovery' : (mode eq 'reset' ? 'Set New Password' : 'Sign In'))}"/></title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/styles.css?v=20261114-obsidian3">
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=JetBrains+Mono:wght@500;600;700&family=Plus+Jakarta+Sans:wght@700;800&display=swap" rel="stylesheet">
  <script>
    try {
      if (localStorage.getItem('habitflow-theme') === 'dark') {
        document.documentElement.classList.add('dark');
      }
    } catch (e) {}
  </script>
  <style>
    .auth-page {
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      padding: 24px 32px 40px;
      background: var(--hf-canvas);
      color: var(--hf-text);
      transition: background-color 0.2s ease, color 0.2s ease;
    }
    .auth-topbar {
      width: 100%;
      max-width: 1152px;
      margin: 0 auto 24px;
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .auth-brand {
      display: inline-flex;
      align-items: center;
      gap: 10px;
      color: var(--hf-text);
      text-decoration: none;
      font: 800 18px/1 'Plus Jakarta Sans', sans-serif;
      letter-spacing: -0.02em;
    }
    .auth-brand-mark {
      width: 32px;
      height: 32px;
      display: grid;
      place-items: center;
      color: #ffffff;
      background: linear-gradient(135deg, #EF2323 0%, #C41212 100%);
      border-radius: 12px;
      font: 800 14px/1 'Plus Jakarta Sans', sans-serif;
      box-shadow: 0 6px 18px rgba(239, 35, 35, 0.28);
    }
    .auth-brand-flow {
      color: #EF2323;
    }
    .auth-theme-toggle {
      width: 36px;
      height: 36px;
      display: grid;
      place-items: center;
      color: var(--hf-text);
      background: var(--hf-surface);
      border: 1px solid var(--hf-border);
      border-radius: 12px;
      cursor: pointer;
      transition: border-color 0.2s ease, color 0.2s ease;
    }
    .auth-theme-toggle:hover {
      border-color: rgba(239, 35, 35, 0.5);
    }
    .auth-theme-toggle svg {
      width: 16px;
      height: 16px;
      fill: none;
      stroke: currentColor;
      stroke-width: 1.8;
      stroke-linecap: round;
      stroke-linejoin: round;
    }
    html.dark .auth-theme-toggle .sun-icon {
      stroke: #fbbf24;
    }
    .auth-shell {
      flex: 1;
      width: 100%;
      max-width: 1152px;
      margin: 0 auto;
      display: grid;
      grid-template-columns: repeat(12, minmax(0, 1fr));
      gap: 32px;
      align-items: stretch;
    }
    .auth-showcase,
    .auth-card {
      position: relative;
      min-width: 0;
      overflow: hidden;
      border-radius: 16px;
      padding: 32px;
      background-color: var(--hf-surface);
      background-image: radial-gradient(circle at 92% 94%, rgba(239, 35, 35, 0.08) 0%, rgba(239, 35, 35, 0.025) 42%, transparent 72%);
      border: 1px solid var(--hf-border);
      box-shadow: inset -1px -1px 0 0 rgba(239, 35, 35, 0.08), 0 1px 3px rgba(15, 23, 42, 0.04);
    }
    html.dark .auth-showcase,
    html.dark .auth-card {
      background-color: var(--hf-surface);
      background-image: radial-gradient(circle at 92% 94%, rgba(220, 24, 24, 0.25) 0%, rgba(220, 24, 24, 0.08) 38%, transparent 70%);
      border: 1px solid var(--hf-border);
      box-shadow: inset -1px -1px 0 0 rgba(239, 35, 35, 0.22);
    }
    .auth-showcase {
      grid-column: span 6;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
    }
    .auth-showcase-body {
      position: relative;
      z-index: 2;
    }
    .auth-kicker {
      margin: 0 0 12px;
      color: #EF2323;
      font-size: 11px;
      font-weight: 800;
      letter-spacing: 0.14em;
      text-transform: uppercase;
    }
    .auth-showcase h1 {
      max-width: 520px;
      margin: 0;
      color: var(--hf-text);
      font: 800 clamp(28px, 3vw, 38px)/1.15 'Plus Jakarta Sans', sans-serif;
      letter-spacing: -0.035em;
    }
    .auth-formula {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 10px;
      margin: 20px 0 16px;
      color: var(--hf-text);
      font-size: 13.5px;
      font-weight: 800;
      letter-spacing: 0.04em;
    }
    .auth-formula svg {
      width: 24px;
      height: 18px;
      flex: 0 0 24px;
      display: block;
    }
    .auth-formula-divider {
      width: 1.5px;
      height: 16px;
      background: var(--hf-subtext);
      opacity: 0.4;
      margin: 0 2px;
    }
    .auth-formula sup {
      font-size: 0.68em;
      font-weight: 800;
      margin-left: 1px;
    }
    .auth-lead {
      max-width: 520px;
      margin: 16px 0 22px;
      color: var(--hf-subtext);
      font-size: 14px;
      line-height: 1.6;
    }
    .auth-features {
      display: grid;
      gap: 12px;
      margin-top: 4px;
    }
    .auth-feature {
      display: flex;
      align-items: flex-start;
      gap: 12px;
      padding: 14px;
      background: color-mix(in srgb, var(--hf-muted) 75%, transparent);
      border: 1px solid var(--hf-border);
      border-radius: 12px;
    }
    .auth-feature-icon {
      width: 16px;
      height: 16px;
      flex: 0 0 16px;
      margin-top: 2px;
      color: #EF2323;
      fill: none;
      stroke: currentColor;
      stroke-width: 2;
      stroke-linecap: round;
      stroke-linejoin: round;
    }
    .auth-feature-copy {
      display: grid;
      gap: 3px;
      min-width: 0;
    }
    .auth-feature strong {
      color: var(--hf-text);
      font-size: 12px;
      font-weight: 700;
      line-height: 1.35;
    }
    .auth-feature span {
      color: var(--hf-subtext);
      font-size: 12px;
      line-height: 1.5;
    }
    .auth-wave-wrap {
      position: relative;
      z-index: 1;
      height: 64px;
      margin: 24px -32px -32px;
      overflow: hidden;
      pointer-events: none;
    }
    .auth-wave {
      display: block;
      width: 100%;
      height: 100%;
    }
    .auth-card {
      grid-column: span 6;
      align-self: stretch;
      display: flex;
      flex-direction: column;
      justify-content: center;
    }
    .auth-switcher {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 4px;
      padding: 4px;
      margin-bottom: 24px;
      background: var(--hf-muted);
      border: 1px solid var(--hf-border);
      border-radius: 12px;
    }
    .auth-switcher a {
      padding: 8px 12px;
      text-align: center;
      color: var(--hf-subtext);
      font-size: 12px;
      font-weight: 700;
      text-decoration: none;
      border-radius: 8px;
      transition: all 0.2s ease;
    }
    .auth-switcher a:hover:not(.active) {
      color: var(--hf-text);
    }
    .auth-switcher a.active {
      color: #ffffff;
      background: linear-gradient(135deg, #EF2323 0%, #C41212 100%);
      border: 1px solid rgba(255, 95, 95, 0.45);
      box-shadow: 0 6px 18px rgba(239, 35, 35, 0.28);
    }
    .auth-card h2 {
      margin: 0 0 6px;
      color: var(--hf-text);
      font: 800 30px/1.15 'Plus Jakarta Sans', sans-serif;
      letter-spacing: -0.035em;
    }
    .auth-subtitle {
      margin: 0 0 20px;
      color: var(--hf-subtext);
      font-size: 13.5px;
      line-height: 1.5;
    }
    .auth-card form {
      display: grid;
      gap: 16px;
    }
    .auth-card label {
      display: grid;
      gap: 6px;
      color: var(--hf-text);
      font-size: 12px;
      font-weight: 700;
    }
    .auth-input-wrap {
      position: relative;
      display: flex;
      align-items: center;
    }
    .auth-input-wrap > svg {
      position: absolute;
      left: 14px;
      width: 16px;
      height: 16px;
      fill: none;
      stroke: var(--hf-subtext);
      stroke-width: 1.8;
      stroke-linecap: round;
      stroke-linejoin: round;
      pointer-events: none;
    }
    .auth-input-wrap input {
      width: 100%;
      height: 44px;
      padding: 0 42px 0 40px;
      color: var(--hf-text);
      background: var(--hf-muted);
      border: 1px solid var(--hf-border);
      border-radius: 12px;
      font-size: 14px;
      font-family: inherit;
      transition: border-color 0.15s ease;
    }
    .auth-input-wrap input::placeholder {
      color: var(--hf-subtext);
      opacity: 0.8;
    }
    .auth-input-wrap input:focus {
      border-color: #EF2323;
      outline: none;
    }
    .auth-password-toggle {
      position: absolute;
      right: 6px;
      width: 32px;
      height: 32px;
      display: grid;
      place-items: center;
      color: var(--hf-subtext);
      background: transparent;
      border: 0;
      border-radius: 8px;
      cursor: pointer;
    }
    .auth-password-toggle:hover {
      color: var(--hf-text);
    }
    .auth-password-toggle svg {
      width: 16px;
      height: 16px;
      fill: none;
      stroke: currentColor;
      stroke-width: 1.8;
      stroke-linecap: round;
      stroke-linejoin: round;
    }
    .auth-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      padding-top: 2px;
    }
    .auth-row label {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      color: var(--hf-subtext);
      font-size: 12px;
      font-weight: 500;
      cursor: pointer;
      user-select: none;
    }
    .auth-row input[type="checkbox"] {
      width: 16px;
      height: 16px;
      accent-color: #EF2323;
      border-radius: 4px;
      cursor: pointer;
    }
    .auth-link {
      color: #EF2323;
      font-size: 12px;
      font-weight: 700;
      text-decoration: none;
    }
    .auth-link:hover {
      text-decoration: underline;
    }
    .auth-submit {
      min-height: 46px;
      margin-top: 4px;
      padding: 0 18px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      color: #ffffff;
      background: linear-gradient(135deg, #EF2323 0%, #C41212 100%);
      border: 1px solid rgba(255, 95, 95, 0.45);
      border-radius: 12px;
      box-shadow: 0 6px 20px rgba(239, 35, 35, 0.28);
      font-size: 14px;
      font-weight: 700;
      cursor: pointer;
      transition: transform 0.15s ease, background 0.15s ease;
    }
    .auth-submit:hover {
      background: linear-gradient(135deg, #F13B3B 0%, #B91313 100%);
    }
    .auth-submit:active {
      transform: scale(0.99);
    }
    .auth-submit svg {
      width: 16px;
      height: 16px;
      fill: none;
      stroke: currentColor;
      stroke-width: 2;
      stroke-linecap: round;
      stroke-linejoin: round;
    }
    .auth-error {
      margin-bottom: 14px;
      padding: 12px;
      color: var(--hf-text);
      background: rgba(239, 35, 35, 0.12);
      border: 1px solid #EF2323;
      border-radius: 12px;
      font-size: 12px;
      font-weight: 600;
    }
    .auth-hint {
      color: var(--hf-subtext);
      font-size: 11px;
      line-height: 1.5;
    }
    .auth-hint.invalid {
      color: #EF2323;
    }
    .auth-account-switch {
      margin: 20px 0 0;
      text-align: center;
      color: var(--hf-subtext);
      font-size: 12px;
    }
    .auth-back-link { display:inline-flex; align-items:center; gap:7px; margin-bottom:20px; color:var(--hf-subtext); font-size:12px; font-weight:700; text-decoration:none; }
    .auth-back-link:hover { color:var(--hf-text); }
    .auth-back-link svg { width:15px; height:15px; fill:none; stroke:currentColor; stroke-width:2; stroke-linecap:round; stroke-linejoin:round; }
    .auth-notice { margin-bottom:14px; padding:12px; color:#047857; background:rgba(16,185,129,.12); border:1px solid rgba(16,185,129,.42); border-radius:12px; font-size:12px; font-weight:600; line-height:1.5; }
    @media (max-width: 900px) {
      .auth-page {
        padding: 16px 18px 28px;
      }
      .auth-shell {
        grid-template-columns: 1fr;
        gap: 20px;
      }
      .auth-showcase,
      .auth-card {
        grid-column: 1;
        padding: 24px;
      }
      .auth-wave-wrap {
        margin: 20px -24px -24px;
        height: 48px;
      }
    }
    .auth-or-divider { display:flex; align-items:center; gap:12px; margin:14px 0; }
    .auth-or-divider::before,.auth-or-divider::after { content:""; flex:1; height:1px; background:var(--hf-border); }
    .auth-or-divider span { color:var(--hf-subtext); font-size:11px; font-weight:700; letter-spacing:.12em; text-transform:uppercase; }
    .auth-google-btn { position:relative; width:100%; min-height:46px; border-radius:9999px; border:1px solid var(--hf-border); background:var(--hf-surface); color:var(--hf-text); font-family:inherit; font-size:15px; font-weight:600; line-height:1.2; white-space:nowrap; text-decoration:none; display:inline-flex; align-items:center; justify-content:center; padding:0 44px; cursor:pointer; box-shadow:0 1px 2px rgba(15,23,42,.04); transition:background .16s ease,border-color .16s ease,transform .15s ease,box-shadow .16s ease; }
    .auth-google-btn:hover { border-color:var(--hf-subtext); box-shadow:0 4px 12px rgba(15,23,42,.08); }
    .auth-google-btn:active { transform:scale(.995); }
    .auth-google-btn .google-g-icon { position:absolute; left:18px; width:20px; height:20px; flex-shrink:0; }
    @media (max-width: 520px) {
      .auth-showcase,
      .auth-card {
        padding: 20px 18px;
      }
      .auth-wave-wrap {
        margin: 18px -18px -20px;
      }
      .auth-showcase .auth-kicker {
        font-size: clamp(8.5px, 2.55vw, 10px);
        letter-spacing: 0.08em;
        white-space: nowrap;
      }
      .auth-google-btn {
        padding: 0 16px 0 38px;
        font-size: clamp(11.5px, 3.3vw, 13.5px);
        white-space: nowrap;
      }
      .auth-google-btn .google-g-icon {
        left: 14px;
        width: 18px;
        height: 18px;
      }
    }
  </style>
</head>
<body>
  <div class="auth-page">
    <header class="auth-topbar">
      <a class="auth-brand" href="${pageContext.request.contextPath}/login">
        <span class="auth-brand-mark">H</span>
        <span>Habit<span class="auth-brand-flow">Flow</span></span>
      </a>
      <button type="button" class="auth-theme-toggle" id="auth-theme-toggle" aria-label="Switch theme">
        <svg class="moon-icon" viewBox="0 0 24 24"><path d="M20 15A8.5 8.5 0 0 1 9 4a8.5 8.5 0 1 0 11 11Z"/></svg>
        <svg class="sun-icon" viewBox="0 0 24 24"><circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M4.9 4.9l1.4 1.4m11.4 11.4 1.4 1.4M2 12h2m16 0h2M4.9 19.1l-1.4-1.4m11.4-11.4 1.4-1.4"/></svg>
      </button>
    </header>

    <main class="auth-shell">
      <section class="auth-showcase hf-crimson-card">
        <div class="auth-showcase-body">
          <p class="auth-kicker">PERSONAL HABIT TRACKER · PRIVATE WORKSPACE</p>
          <h1>Build lasting routines across every device.</h1>

          <p class="auth-lead">Sign in to access your private daily routine, 7-day check-in matrix, deep-work focus timer, and 30-day consistency analytics.</p>

          <div class="auth-features">
            <article class="auth-feature">
              <svg class="auth-feature-icon" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
                <path d="m9 12 2 2 4-4"/>
              </svg>
              <div class="auth-feature-copy">
                <strong>Isolated Personal Workspaces</strong>
                <span>Your habits, daily reflections, streak history, and goals are stored privately inside your personal account.</span>
              </div>
            </article>

            <article class="auth-feature">
              <svg class="auth-feature-icon" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z"/>
                <path d="m9 13 2 2 4-4"/>
              </svg>
              <div class="auth-feature-copy">
                <strong>Real-Time Workspace Persistence</strong>
                <span>Every habit check-in, note, and focus session is saved automatically so you never lose your streak.</span>
              </div>
            </article>

            <article class="auth-feature">
              <svg class="auth-feature-icon" viewBox="0 0 24 24" aria-hidden="true">
                <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
                <line x1="16" y1="2" x2="16" y2="6"/>
                <line x1="8" y1="2" x2="8" y2="6"/>
                <line x1="3" y1="10" x2="21" y2="10"/>
                <path d="M8 14h.01M12 14h.01M16 14h.01M8 18h.01M12 18h.01M16 18h.01"/>
              </svg>
              <div class="auth-feature-copy">
                <strong>7-Day Matrix, Focus Timer &amp; Analytics</strong>
                <span>Track daily completions, log Pomodoro focus sessions, and visualize 30-day consistency momentum.</span>
              </div>
            </article>
          </div>
        </div>

        <div class="auth-wave-wrap" aria-hidden="true">
          <svg class="auth-wave" viewBox="0 0 280 68" fill="none" preserveAspectRatio="none">
            <defs>
              <linearGradient id="authWave1" x1="0" y1="0" x2="1" y2="0">
                <stop offset="0%" stop-color="rgba(239, 35, 35, 0.16)" />
                <stop offset="38%" stop-color="#FF3B3B" />
                <stop offset="68%" stop-color="#EF2323" />
                <stop offset="100%" stop-color="rgba(200, 20, 20, 0.22)" />
              </linearGradient>
              <linearGradient id="authWave2" x1="0" y1="0" x2="1" y2="0">
                <stop offset="0%" stop-color="rgba(239, 35, 35, 0.20)" />
                <stop offset="50%" stop-color="#EF2323" />
                <stop offset="100%" stop-color="rgba(239, 35, 35, 0.16)" />
              </linearGradient>
            </defs>
            <path d="M-12 28 C 52 14, 118 30, 182 18 C 228 11, 258 24, 292 36 L 292 48 C 245 34, 195 24, 128 28 C 65 32, 18 42, -12 46 Z" fill="rgba(239, 35, 35, 0.09)" />
            <path d="M-12 34 C 46 14, 108 28, 176 23 C 226 19, 258 11, 292 3" stroke="url(#authWave2)" stroke-width="1.9" fill="none" />
            <path d="M-12 23 C 54 33, 122 10, 192 15 C 234 18, 264 28, 292 39" stroke="url(#authWave1)" stroke-width="2.4" fill="none" />
          </svg>
        </div>
      </section>

      <section class="auth-card hf-crimson-card">
        <c:if test="${mode eq 'forgot' or mode eq 'reset'}">
          <a class="auth-back-link" href="${pageContext.request.contextPath}/login"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M19 12H5m7 7-7-7 7-7"/></svg>Back to sign in</a>
        </c:if>
        <c:if test="${mode eq 'forgot' or mode eq 'reset'}">
          <c:if test="${not empty error}"><div class="auth-error" role="alert"><c:out value="${error}"/></div></c:if>
        </c:if>
        <c:if test="${not empty notice}"><div class="auth-notice" role="status"><c:out value="${notice}"/></div></c:if>

        <c:if test="${mode eq 'forgot'}">
          <p class="auth-kicker">ACCOUNT RECOVERY</p>
          <h2>Forgot your password?</h2>
          <p class="auth-subtitle">Recover any email-password account via reset link, or verify ownership instantly with Google.</p>
          <form method="post" action="${pageContext.request.contextPath}/forgot-password">
            <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>"/>
            <label>Account email address
              <span class="auth-input-wrap">
                <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="5" width="18" height="14" rx="2"/><path d="m4 7 8 6 8-6"/></svg>
                <input type="email" name="email" autocomplete="email" required maxlength="254" value="<c:out value='${param.email}'/>" placeholder="you@example.com"/>
              </span>
            </label>
            <small class="auth-hint">We’ll send a one-time password reset link valid for 15 minutes.</small>
            <button type="submit" class="auth-submit"><span>Send password reset link</span><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h14"/><path d="m12 5 7 7-7 7"/></svg></button>
          </form>
          <div class="auth-or-divider" role="separator" aria-label="or instant verification"><span>or instant verification</span></div>
          <a href="${pageContext.request.contextPath}/auth/google?mode=reset" class="auth-google-btn">
            <svg class="google-g-icon" viewBox="0 0 24 24" aria-hidden="true"><path fill="#EA4335" d="M12 10.2v3.9h5.5c-.24 1.26-.96 2.33-2.04 3.05l3.3 2.56c1.92-1.77 3.04-4.38 3.04-7.46 0-.72-.06-1.41-.19-2.05H12z"/><path fill="#34A853" d="M12 22c2.75 0 5.06-.91 6.75-2.47l-3.3-2.56c-.91.61-2.08.98-3.45.98-2.65 0-4.9-1.79-5.71-4.19H2.88v2.64C4.56 19.74 8.01 22 12 22z"/><path fill="#FBBC05" d="M6.29 13.76A5.98 5.98 0 0 1 5.97 12c0-.61.11-1.21.32-1.76V7.6H2.88A9.98 9.98 0 0 0 2 12c0 1.61.39 3.14 1.08 4.4l3.21-2.64z"/><path fill="#4285F4" d="M12 5.95c1.5 0 2.84.52 3.9 1.53l2.92-2.92C17.05 2.91 14.74 2 12 2 8.01 2 4.56 4.26 2.88 7.6l3.41 2.64C7.1 7.74 9.35 5.95 12 5.95z"/></svg>
            <span>Verify with Google to reset password</span>
          </a>
          <p class="auth-account-switch">Google verification can authorize a reset for the same verified email without permanently linking accounts.</p>
        </c:if>

        <c:if test="${mode eq 'reset'}">
          <p class="auth-kicker">ACCOUNT RECOVERY</p>
          <h2>Set a new password</h2>
          <p class="auth-subtitle">Your account email is verified. Enter your new password below.</p>
          <form method="post" action="${pageContext.request.contextPath}/reset-password" id="auth-form">
            <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>"/>
            <input type="hidden" name="token" value="<c:out value='${resetToken}'/>"/>
            <label>New password
              <span class="auth-input-wrap">
                <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="4" y="10" width="16" height="11" rx="2"/><path d="M8 10V7a4 4 0 1 1 8 0v3"/></svg>
                <input id="auth-password" type="password" name="password" autocomplete="new-password" required minlength="10" maxlength="72" placeholder="At least 10 characters"/>
                <button type="button" class="auth-password-toggle" data-password-toggle="auth-password" aria-label="Show password"><svg class="eye-open" viewBox="0 0 24 24" aria-hidden="true"><path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z"/><circle cx="12" cy="12" r="3"/></svg></button>
              </span>
            </label>
            <label>Confirm new password
              <span class="auth-input-wrap">
                <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="4" y="10" width="16" height="11" rx="2"/><path d="M8 10V7a4 4 0 1 1 8 0v3"/></svg>
                <input id="auth-confirm-password" type="password" name="confirmPassword" autocomplete="new-password" required minlength="10" maxlength="72" placeholder="Repeat new password"/>
                <button type="button" class="auth-password-toggle" data-password-toggle="auth-confirm-password" aria-label="Show password confirmation"><svg class="eye-open" viewBox="0 0 24 24" aria-hidden="true"><path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z"/><circle cx="12" cy="12" r="3"/></svg></button>
              </span>
            </label>
            <small class="auth-hint" id="password-match">Use 10 or more characters. Passwords must match.</small>
            <button type="submit" class="auth-submit"><span>Update password</span><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h14"/><path d="m12 5 7 7-7 7"/></svg></button>
          </form>
        </c:if>

        <c:if test="${mode ne 'forgot' and mode ne 'reset'}">
        <nav class="auth-switcher" aria-label="Sign in or create account">
          <a class="${mode ne 'register' ? 'active' : ''}" href="${pageContext.request.contextPath}/login">Sign in</a>
          <a class="${mode eq 'register' ? 'active' : ''}" href="${pageContext.request.contextPath}/register">Create account</a>
        </nav>

        <p class="auth-kicker"><c:out value="${mode eq 'register' ? 'NEW PERSONAL WORKSPACE' : 'ACCOUNT ACCESS'}"/></p>
        <h2>
          <c:choose>
            <c:when test="${mode eq 'register'}">Create your account</c:when>
            <c:otherwise>Welcome back</c:otherwise>
          </c:choose>
        </h2>
        <p class="auth-subtitle">
          <c:choose>
            <c:when test="${mode eq 'register'}">Start a private HabitFlow workspace for your daily routines.</c:when>
            <c:otherwise>Enter your credentials to open your personal HabitFlow workspace.</c:otherwise>
          </c:choose>
        </p>

        <c:if test="${not empty error}">
          <div class="auth-error" role="alert"><c:out value="${error}"/></div>
        </c:if>

        <form method="post" action="${pageContext.request.contextPath}/${mode eq 'register' ? 'register' : 'login'}" id="auth-form">
          <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>"/>

          <c:if test="${mode eq 'register'}">
            <label>Full name
              <span class="auth-input-wrap">
                <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/></svg>
                <input name="name" autocomplete="name" required minlength="2" maxlength="80" value="<c:out value='${param.name}'/>" placeholder="Enter your full name"/>
              </span>
            </label>
          </c:if>

          <label>Email address
            <span class="auth-input-wrap">
              <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="5" width="18" height="14" rx="2"/><path d="m4 7 8 6 8-6"/></svg>
              <input type="email" name="email" autocomplete="email" required maxlength="254" value="<c:out value='${param.email}'/>" placeholder="you@example.com"/>
            </span>
          </label>

          <label>Password
            <span class="auth-input-wrap">
              <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="4" y="10" width="16" height="11" rx="2"/><path d="M8 10V7a4 4 0 1 1 8 0v3"/></svg>
              <input id="auth-password" type="password" name="password" autocomplete="${mode eq 'register' ? 'new-password' : 'current-password'}" required minlength="10" maxlength="72" placeholder="${mode eq 'register' ? 'At least 10 characters' : 'Enter your password'}"/>
              <button type="button" class="auth-password-toggle" data-password-toggle="auth-password" aria-label="Show password">
                <svg class="eye-open" viewBox="0 0 24 24" aria-hidden="true"><path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z"/><circle cx="12" cy="12" r="3"/></svg>
              </button>
            </span>
          </label>

          <c:if test="${mode eq 'register'}">
            <label>Confirm password
              <span class="auth-input-wrap">
                <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="4" y="10" width="16" height="11" rx="2"/><path d="M8 10V7a4 4 0 1 1 8 0v3"/></svg>
                <input id="auth-confirm-password" type="password" name="confirmPassword" autocomplete="new-password" required minlength="10" maxlength="72" placeholder="Repeat password"/>
                <button type="button" class="auth-password-toggle" data-password-toggle="auth-confirm-password" aria-label="Show password confirmation">
                  <svg class="eye-open" viewBox="0 0 24 24" aria-hidden="true"><path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z"/><circle cx="12" cy="12" r="3"/></svg>
                </button>
              </span>
            </label>
            <small class="auth-hint" id="password-match">Use 10 or more characters. Passwords must match.</small>
          </c:if>

          <c:if test="${mode ne 'register'}">
            <div class="auth-row">
              <label><input type="checkbox" name="remember" checked/> <span>Remember me</span></label>
              <a class="auth-link" href="${pageContext.request.contextPath}/forgot-password">Forgot password?</a>
            </div>
          </c:if>

          <button type="submit" class="auth-submit">
            <span><c:out value="${mode eq 'register' ? 'Create account' : 'Sign in'}"/></span>
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h14"/><path d="m12 5 7 7-7 7"/></svg>
          </button>
        </form>

        <div class="auth-or-divider" role="separator" aria-label="or"><span>or</span></div>
        <a href="${pageContext.request.contextPath}/auth/google?mode=${mode eq 'register' ? 'register' : 'login'}" class="auth-google-btn">
          <svg class="google-g-icon" viewBox="0 0 24 24" aria-hidden="true">
            <path fill="#EA4335" d="M12 10.2v3.9h5.5c-.24 1.26-.96 2.33-2.04 3.05l3.3 2.56c1.92-1.77 3.04-4.38 3.04-7.46 0-.72-.06-1.41-.19-2.05H12z"/>
            <path fill="#34A853" d="M12 22c2.75 0 5.06-.91 6.75-2.47l-3.3-2.56c-.91.61-2.08.98-3.45.98-2.65 0-4.9-1.79-5.71-4.19H2.88v2.64C4.56 19.74 8.01 22 12 22z"/>
            <path fill="#FBBC05" d="M6.29 13.76A5.98 5.98 0 0 1 5.97 12c0-.61.11-1.21.32-1.76V7.6H2.88A9.98 9.98 0 0 0 2 12c0 1.61.39 3.14 1.08 4.4l3.21-2.64z"/>
            <path fill="#4285F4" d="M12 5.95c1.5 0 2.84.52 3.9 1.53l2.92-2.92C17.05 2.91 14.74 2 12 2 8.01 2 4.56 4.26 2.88 7.6l3.41 2.64C7.1 7.74 9.35 5.95 12 5.95z"/>
          </svg>
          <span>Continue with Google</span>
        </a>

        <p class="auth-account-switch">
          <c:choose>
            <c:when test="${mode eq 'register'}">Already have an account? <a class="auth-link" href="${pageContext.request.contextPath}/login">Sign in</a></c:when>
            <c:otherwise>Don’t have an account yet? <a class="auth-link" href="${pageContext.request.contextPath}/register">Create account</a></c:otherwise>
          </c:choose>
        </p>
        </c:if>
      </section>
    </main>
  </div>

  <script>
    (function () {
      if (window.location.pathname.endsWith('/reset-password') && window.location.hash.indexOf('#token=') === 0) {
        var tokenField = document.querySelector('input[name="token"]');
        if (tokenField) {
          try { tokenField.value = decodeURIComponent(window.location.hash.substring(7)); } catch (e) {}
          window.history.replaceState(null, document.title, window.location.pathname);
        }
      }
    }());

    document.getElementById('auth-theme-toggle').addEventListener('click', function () {
      var dark = document.documentElement.classList.toggle('dark');
      try {
        localStorage.setItem('habitflow-theme', dark ? 'dark' : 'light');
      } catch (e) {}
      document.querySelector('meta[name="theme-color"]').content = dark ? '#080808' : '#F8FAFC';
    });

    var eyeSvg = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z"/><circle cx="12" cy="12" r="3"/></svg>';
    var eyeOffSvg = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9.88 9.88a3 3 0 1 0 4.24 4.24"/><path d="M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68"/><path d="M6.61 6.61A13.526 13.526 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61"/><line x1="2" y1="2" x2="22" y2="22"/></svg>';

    document.querySelectorAll('[data-password-toggle]').forEach(function (button) {
      button.addEventListener('click', function () {
        var input = document.getElementById(button.dataset.passwordToggle);
        var show = input.type === 'password';
        input.type = show ? 'text' : 'password';
        button.setAttribute('aria-label', show ? 'Hide password' : 'Show password');
        button.innerHTML = show ? eyeOffSvg : eyeSvg;
      });
    });

    var form = document.getElementById('auth-form'),
        password = document.getElementById('auth-password'),
        confirmation = document.getElementById('auth-confirm-password'),
        hint = document.getElementById('password-match');

    function validatePassword() {
      if (!confirmation) return;
      var same = confirmation.value === password.value;
      confirmation.setCustomValidity(same ? '' : 'Passwords do not match.');
      if (hint && confirmation.value) {
        hint.textContent = same ? 'Passwords match.' : 'Passwords do not match.';
        hint.classList.toggle('invalid', !same);
      }
    }

    if (confirmation) {
      password.addEventListener('input', validatePassword);
      confirmation.addEventListener('input', validatePassword);
      form.addEventListener('submit', function (event) {
        if (password.value !== confirmation.value) {
          event.preventDefault();
          validatePassword();
          confirmation.reportValidity();
        }
      });
    }

  </script>
</body>
</html>
