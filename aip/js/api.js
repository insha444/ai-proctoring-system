/**
 * js/api.js — AI Proctor Shared Library
 * Provides: API client · Auth · OTP · UI helpers · formatters
 */

const API_BASE = 'https://ai-proctoring-system-production.up.railway.app';

/* ────────────────────────────────────────────────────────────
   AUTH
──────────────────────────────────────────────────────────── */
const Auth = {
  getToken : ()  => localStorage.getItem('jwt_token'),
  getUser  : ()  => ({
    id    : localStorage.getItem('user_id')    || '',
    name  : localStorage.getItem('user_name')  || 'User',
    email : localStorage.getItem('user_email') || '',
    role  : localStorage.getItem('user_role')  || '',
  }),
  save(d) {
    localStorage.setItem('jwt_token',  d.token);
    localStorage.setItem('user_id',    d.userId);
    localStorage.setItem('user_name',  d.name);
    localStorage.setItem('user_email', d.email);
    localStorage.setItem('user_role',  d.role);
  },
  clear() {
    ['jwt_token','user_id','user_name','user_email','user_role',
     'otp_pending_email','currentSessionId','currentExamId']
      .forEach(k => localStorage.removeItem(k));
  },
  logout() { this.clear(); window.location.href = 'login.html'; },
  requireAuth() {
    if (!this.getToken()) { window.location.href = 'login.html'; return false; }
    return true;
  },
  requireRole(role) {
    if (!this.requireAuth()) return false;
    const r = this.getUser().role;
    if (r !== role) { window.location.href = r === 'ADMIN' ? 'admin.html' : 'dashboard.html'; return false; }
    return true;
  },
};

/* ────────────────────────────────────────────────────────────
   HTTP CLIENT
──────────────────────────────────────────────────────────── */
const API = {
  async _req(method, path, body = null) {
    const headers = { 'Content-Type': 'application/json' };
    const token = Auth.getToken();
    if (token) headers['Authorization'] = `Bearer ${token}`;
    const opts = { method, headers };
    if (body) opts.body = JSON.stringify(body);
    const res = await fetch(API_BASE + path, opts);
    if (res.status === 401) { Auth.logout(); throw new Error('Session expired.'); }
    const ct = res.headers.get('content-type') || '';
    const data = ct.includes('application/json') ? await res.json() : await res.text();
    if (!res.ok) throw new Error((data && data.message) ? data.message : `HTTP ${res.status}`);
    return data;
  },
  get   : (p)    => API._req('GET',    p),
  post  : (p, b) => API._req('POST',   p, b),
  put   : (p, b) => API._req('PUT',    p, b),
  delete: (p)    => API._req('DELETE', p),
};

/* ────────────────────────────────────────────────────────────
   OTP HELPERS  (frontend simulation — backend endpoint stubs)
   In production, replace _generateOtp / _sendOtp to call your
   real backend: POST /api/auth/send-otp  { email }
                 POST /api/auth/verify-otp { email, otp }
──────────────────────────────────────────────────────────── */
const OTP = {
  _store: {},   // { email: { code, expires } }

  /** Request OTP for an email. Returns the 6-digit code (for demo). */
  async request(email) {
    // In production: return await API.post('/api/auth/send-otp', { email });
    const code    = String(Math.floor(100000 + Math.random() * 900000));
    const expires = Date.now() + 5 * 60 * 1000; // 5 min
    OTP._store[email] = { code, expires };
    localStorage.setItem('otp_pending_email', email);
    console.info(`[OTP] Code for ${email}: ${code}`);   // remove in production
    return { success: true, code, message: 'OTP sent to ' + email };
  },

  /** Verify the 6-digit code entered by the user. */
  verify(email, entered) {
    // In production: return await API.post('/api/auth/verify-otp', { email, otp: entered });
    const rec = OTP._store[email];
    if (!rec)                       return { ok: false, message: 'No OTP requested for this email.' };
    if (Date.now() > rec.expires)   return { ok: false, message: 'OTP has expired. Please request a new one.' };
    if (rec.code !== entered.trim()) return { ok: false, message: 'Incorrect OTP. Please try again.' };
    delete OTP._store[email];
    return { ok: true };
  },

  clearPending() { localStorage.removeItem('otp_pending_email'); },
};

/* ────────────────────────────────────────────────────────────
   UI HELPERS
──────────────────────────────────────────────────────────── */
function showToast(msg, type = '', duration = 3500) {
  let el = document.getElementById('_toast');
  if (!el) {
    el = document.createElement('div');
    el.id = '_toast'; el.className = 'toast';
    document.body.appendChild(el);
  }
  el.textContent = msg;
  el.className   = `toast ${type} show`;
  clearTimeout(el._t);
  el._t = setTimeout(() => el.classList.remove('show'), duration);
}

function showAlert(id, msg, type = 'error') {
  const el = document.getElementById(id);
  if (!el) return;
  if (!msg) { el.className = 'alert'; el.textContent = ''; return; }
  el.textContent = msg;
  el.className   = `alert ${type} show`;
  el.scrollIntoView({ block: 'nearest' });
}

function setBtnLoading(btnId, loading, text = null) {
  const btn = document.getElementById(btnId);
  if (!btn) return;
  btn.disabled = loading;
  const sp  = btn.querySelector('.spinner');
  const txt = btn.querySelector('.btn-label') || btn.querySelector('span:not(.spinner)');
  if (sp)  sp.classList.toggle('show', loading);
  if (txt && text) txt.textContent = loading ? text : (txt.dataset.orig || txt.textContent);
  if (txt && !txt.dataset.orig && loading) txt.dataset.orig = txt.textContent;
}

function populateHeader() {
  const u = Auth.getUser();
  const set = (id, val) => { const el = document.getElementById(id); if (el) el.textContent = val; };
  set('headerName',  u.name);
  set('headerEmail', u.email);
  set('headerRole',  u.role);
}

/* ── OTP input keyboard wiring ───────────────────────────── */
function wireOtpInputs(parentId, onComplete) {
  const inputs = Array.from(document.querySelectorAll(`#${parentId} input`));
  inputs.forEach((inp, i) => {
    inp.addEventListener('keydown', e => {
      if (e.key === 'Backspace' && !inp.value && i > 0) { inputs[i-1].focus(); inputs[i-1].value = ''; }
    });
    inp.addEventListener('input', e => {
      const val = inp.value.replace(/\D/g,'');
      inp.value = val ? val[val.length-1] : '';
      inp.classList.toggle('filled', !!inp.value);
      if (inp.value && i < inputs.length - 1) inputs[i+1].focus();
      if (inputs.every(x => x.value)) {
        const code = inputs.map(x => x.value).join('');
        if (typeof onComplete === 'function') onComplete(code);
      }
    });
    inp.addEventListener('paste', e => {
      e.preventDefault();
      const paste = (e.clipboardData || window.clipboardData).getData('text').replace(/\D/g,'');
      paste.split('').slice(0, inputs.length).forEach((ch, j) => {
        if (inputs[j]) { inputs[j].value = ch; inputs[j].classList.add('filled'); }
      });
      const next = Math.min(paste.length, inputs.length - 1);
      inputs[next].focus();
      if (paste.length >= inputs.length && typeof onComplete === 'function')
        onComplete(paste.slice(0, inputs.length));
    });
  });
}

function getOtpValue(parentId) {
  return Array.from(document.querySelectorAll(`#${parentId} input`)).map(x => x.value).join('');
}

function shakeOtpInputs(parentId) {
  document.querySelectorAll(`#${parentId} input`).forEach(inp => {
    inp.classList.remove('error');
    void inp.offsetWidth; // reflow
    inp.classList.add('error');
    inp.value = '';
  });
  const first = document.querySelector(`#${parentId} input`);
  if (first) first.focus();
}

function startOtpCountdown(displayId, seconds, onExpired) {
  let remaining = seconds;
  const el = document.getElementById(displayId);
  const handle = setInterval(() => {
    remaining--;
    if (el) el.textContent = remaining + 's';
    if (remaining <= 0) { clearInterval(handle); if (onExpired) onExpired(); }
  }, 1000);
  return handle;
}

/* ── Date / time ─────────────────────────────────────────── */
function fDate(iso)  { if (!iso) return '—'; return new Date(iso).toLocaleString('en-IN',{dateStyle:'medium',timeStyle:'short'}); }
function fDateS(iso) { if (!iso) return '—'; return new Date(iso).toLocaleString('en-IN',{dateStyle:'short',timeStyle:'short'}); }
function fDur(mins)  { if (!mins) return '—'; if (mins < 60) return `${mins} min`; const h=Math.floor(mins/60),m=mins%60; return m?`${h}h ${m}m`:`${h}h`; }
function timeAgo(iso){ if (!iso) return '—'; const d=(Date.now()-new Date(iso))/1000; if(d<60) return 'Just now'; if(d<3600) return `${Math.floor(d/60)}m ago`; if(d<86400) return `${Math.floor(d/3600)}h ago`; return `${Math.floor(d/86400)}d ago`; }
// Aliases used by pages
const formatDate      = fDate;
const formatDateShort = fDateS;
const formatDuration  = fDur;

/* ── Status badges ───────────────────────────────────────── */
function getExamStatus(e) { const n=Date.now(),s=new Date(e.startTime).getTime(),en=new Date(e.endTime).getTime(); if(n<s) return 'upcoming'; if(n>en) return 'closed'; return 'open'; }
function examStatusBadge(st)  { return {open:'<span class="badge badge-green">Open</span>',upcoming:'<span class="badge badge-yellow">Upcoming</span>',closed:'<span class="badge badge-gray">Closed</span>'}[st]||'<span class="badge badge-gray">Closed</span>'; }
function sessionStatusBadge(st){ return {IN_PROGRESS:'<span class="badge badge-blue">In Progress</span>',SUBMITTED:'<span class="badge badge-green">Submitted</span>',TERMINATED:'<span class="badge badge-red">Terminated</span>'}[st]||`<span class="badge badge-gray">${st}</span>`; }
function sevBadge(sv){ return {HIGH:'<span class="badge badge-red">High</span>',CRITICAL:'<span class="badge badge-red">Critical</span>',MEDIUM:'<span class="badge badge-yellow">Medium</span>',LOW:'<span class="badge badge-blue">Low</span>'}[sv]||`<span class="badge badge-gray">${sv||'?'}</span>`; }
const violationSeverityBadge = sevBadge;

/* ── XSS escape ──────────────────────────────────────────── */
function esc(str) { if (!str) return ''; return String(str).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }
