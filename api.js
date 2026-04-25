/**
 * js/api.js — Centralized API client for AI Proctor Frontend
 *
 * Provides:
 *   - API.get / post / put / delete  (auto-attaches JWT, handles 401)
 *   - Auth helpers: getToken, getUser, logout, requireAuth, requireRole
 *   - UI helpers:   showToast, showAlert, setLoading, formatDate, formatDuration
 */

const API_BASE = 'http://localhost:8080';

/* ══════════════════════════════════════════════════════════════
   AUTH HELPERS
══════════════════════════════════════════════════════════════ */

const Auth = {
  getToken   : () => localStorage.getItem('jwt_token'),
  getUser    : () => ({
    id    : localStorage.getItem('user_id'),
    name  : localStorage.getItem('user_name')  || 'User',
    email : localStorage.getItem('user_email') || '',
    role  : localStorage.getItem('user_role')  || '',
  }),
  save(data) {
    localStorage.setItem('jwt_token',  data.token);
    localStorage.setItem('user_id',    data.userId);
    localStorage.setItem('user_name',  data.name);
    localStorage.setItem('user_email', data.email);
    localStorage.setItem('user_role',  data.role);
  },
  clear() {
    ['jwt_token','user_id','user_name','user_email','user_role',
     'currentSessionId','currentExamId'].forEach(k => localStorage.removeItem(k));
  },
  logout() {
    this.clear();
    window.location.href = 'login.html';
  },
  /** Redirect to login if no token. Call at top of every protected page. */
  requireAuth() {
    if (!this.getToken()) { window.location.href = 'login.html'; return false; }
    return true;
  },
  /** Redirect if role doesn't match. role = 'ADMIN' | 'STUDENT' */
  requireRole(role) {
    if (!this.requireAuth()) return false;
    const user = this.getUser();
    if (user.role !== role) {
      window.location.href = user.role === 'ADMIN'
        ? 'admin.html' : 'dashboard.html';
      return false;
    }
    return true;
  },
};

/* ══════════════════════════════════════════════════════════════
   HTTP CLIENT
══════════════════════════════════════════════════════════════ */

const API = {
  async _request(method, path, body = null) {
    const headers = { 'Content-Type': 'application/json' };
    const token = Auth.getToken();
    if (token) headers['Authorization'] = `Bearer ${token}`;

    const opts = { method, headers };
    if (body) opts.body = JSON.stringify(body);

    const res = await fetch(API_BASE + path, opts);

    if (res.status === 401) {
      Auth.logout();
      throw new Error('Session expired. Please log in again.');
    }

    let data;
    const ct = res.headers.get('content-type') || '';
    if (ct.includes('application/json')) {
      data = await res.json();
    } else {
      data = await res.text();
    }

    if (!res.ok) {
      const msg = (data && data.message) ? data.message : `HTTP ${res.status}`;
      throw new Error(msg);
    }
    return data;
  },

  get   : (path)        => API._request('GET',    path),
  post  : (path, body)  => API._request('POST',   path, body),
  put   : (path, body)  => API._request('PUT',    path, body),
  delete: (path)        => API._request('DELETE', path),
};

/* ══════════════════════════════════════════════════════════════
   UI HELPERS
══════════════════════════════════════════════════════════════ */

/**
 * Show a bottom-right toast notification.
 * @param {string} message
 * @param {'success'|'error'|'warning'|''} type
 * @param {number} duration ms
 */
function showToast(message, type = '', duration = 3500) {
  let el = document.getElementById('toast');
  if (!el) {
    el = document.createElement('div');
    el.id = 'toast';
    el.className = 'toast';
    document.body.appendChild(el);
  }
  el.textContent = message;
  el.className   = `toast ${type} show`;
  clearTimeout(el._timer);
  el._timer = setTimeout(() => { el.classList.remove('show'); }, duration);
}

/**
 * Show/hide an inline alert element.
 * @param {string} id   element id
 * @param {string} msg  message text (empty string hides it)
 * @param {'success'|'error'|'info'|'warning'} type
 */
function showAlert(id, msg, type = 'error') {
  const el = document.getElementById(id);
  if (!el) return;
  if (!msg) { el.className = 'alert'; el.textContent = ''; return; }
  el.textContent = msg;
  el.className   = `alert ${type} show`;
}

/**
 * Toggle loading state on a button.
 * @param {string}  btnId      button element id
 * @param {boolean} loading
 * @param {string}  labelId    span inside button that holds text
 * @param {string}  spinnerId  spinner element id
 * @param {string}  loadingText text shown while loading
 */
function setLoading(btnId, loading, labelId, spinnerId, loadingText = 'Loading...') {
  const btn     = document.getElementById(btnId);
  const label   = labelId    ? document.getElementById(labelId)   : null;
  const spinner = spinnerId  ? document.getElementById(spinnerId) : null;
  if (!btn) return;
  btn.disabled = loading;
  if (label)   label.textContent = loading ? loadingText : label.dataset.default || '';
  if (spinner) spinner.classList.toggle('show', loading);
}

/* ── Date / time formatters ───────────────────────────────── */
function formatDate(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
}
function formatDateShort(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('en-IN', { dateStyle: 'short', timeStyle: 'short' });
}
function formatDuration(mins) {
  if (!mins) return '—';
  if (mins < 60) return `${mins} min`;
  const h = Math.floor(mins / 60), m = mins % 60;
  return m > 0 ? `${h}h ${m}m` : `${h}h`;
}
function timeAgo(iso) {
  if (!iso) return '—';
  const diff = (Date.now() - new Date(iso)) / 1000;
  if (diff < 60)   return 'Just now';
  if (diff < 3600) return `${Math.floor(diff/60)}m ago`;
  if (diff < 86400)return `${Math.floor(diff/3600)}h ago`;
  return `${Math.floor(diff/86400)}d ago`;
}

/* ── Exam status helpers ──────────────────────────────────── */
function getExamStatus(exam) {
  const now = Date.now();
  const start = new Date(exam.startTime).getTime();
  const end   = new Date(exam.endTime).getTime();
  if (now < start) return 'upcoming';
  if (now > end)   return 'closed';
  return 'open';
}
function examStatusBadge(status) {
  const map = {
    open:     '<span class="badge badge-green">Open</span>',
    upcoming: '<span class="badge badge-yellow">Upcoming</span>',
    closed:   '<span class="badge badge-gray">Closed</span>',
  };
  return map[status] || map.closed;
}
function sessionStatusBadge(status) {
  const map = {
    IN_PROGRESS: '<span class="badge badge-blue">In Progress</span>',
    SUBMITTED:   '<span class="badge badge-green">Submitted</span>',
    TERMINATED:  '<span class="badge badge-red">Terminated</span>',
  };
  return map[status] || `<span class="badge badge-gray">${status}</span>`;
}
function violationSeverityBadge(severity) {
  const map = {
    HIGH:     '<span class="badge badge-red">High</span>',
    MEDIUM:   '<span class="badge badge-yellow">Medium</span>',
    LOW:      '<span class="badge badge-blue">Low</span>',
    CRITICAL: '<span class="badge badge-red">Critical</span>',
  };
  return map[severity] || `<span class="badge badge-gray">${severity}</span>`;
}

/* ── Populate header user info ──────────────────────────────── */
function populateHeader() {
  const user = Auth.getUser();
  const nameEl  = document.getElementById('headerName');
  const emailEl = document.getElementById('headerEmail');
  const roleEl  = document.getElementById('headerRole');
  if (nameEl)  nameEl.textContent  = user.name;
  if (emailEl) emailEl.textContent = user.email;
  if (roleEl)  roleEl.textContent  = user.role;
}
