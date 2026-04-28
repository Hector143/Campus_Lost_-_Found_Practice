/* ==========================================================================
   Shared frontend helpers.
   Loaded by every HTML page before the page-specific <script>.
   ========================================================================== */

/**
 * Tiny wrapper around fetch that:
 *   - automatically sends the Authorization header if we have a token
 *   - sends `body` as JSON when present
 *   - throws an Error with the server's message on non-2xx
 *
 * Examples:
 *   const items = await api('/api/items');
 *   await api('/api/auth/login', { method: 'POST', body: { username, password } });
 */
async function api(url, opts = {}) {
    const headers = { 'Content-Type': 'application/json' };
    const token = Auth.token();
    if (token) headers.Authorization = 'Bearer ' + token;

    const init = {
        method: opts.method || 'GET',
        headers,
    };
    if (opts.body !== undefined) init.body = JSON.stringify(opts.body);

    let res;
    try {
        res = await fetch(url, init);
    } catch (netErr) {
        throw new Error('Network error: ' + netErr.message);
    }

    let data = {};
    const text = await res.text();
    if (text) {
        try { data = JSON.parse(text); }
        catch { data = { error: text }; }
    }
    if (!res.ok) throw new Error(data.error || ('HTTP ' + res.status));
    return data;
}

/**
 * Auth state lives in localStorage so it survives page reloads.
 *   Auth.set(token, user) – store after login
 *   Auth.token()          – the bearer token (or null)
 *   Auth.user()           – the user object (or null)
 *   Auth.logout()         – clear and go to login page
 *   Auth.requireLogin()   – redirect to /index.html if not logged in
 *   Auth.requireRole(r)   – redirect if logged-in user is not that role
 */
const Auth = {
    set(token, user) {
        localStorage.setItem('lf_token', token);
        localStorage.setItem('lf_user',  JSON.stringify(user));
    },
    token() { return localStorage.getItem('lf_token'); },
    user()  {
        const raw = localStorage.getItem('lf_user');
        return raw ? JSON.parse(raw) : null;
    },
    async logout() {
        try { await api('/api/auth/logout', { method: 'POST' }); } catch (e) { /* ignore */ }
        localStorage.removeItem('lf_token');
        localStorage.removeItem('lf_user');
        location.href = 'index.html';
    },
    requireLogin() {
        if (!this.token()) location.href = 'index.html';
    },
    requireRole(role) {
        this.requireLogin();
        const u = this.user();
        if (!u || u.role !== role) location.href = 'dashboard.html';
    },
};

/** Escape user-supplied text so we can safely drop it into innerHTML. */
function escapeHtml(s) {
    if (s === null || s === undefined) return '';
    return String(s)
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');
}
