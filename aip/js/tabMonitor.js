/**
 * tabMonitor.js — AI Proctoring Tab & Focus Monitor
 *
 * Detects:
 *   1. Tab switch      — document.visibilitychange (hidden)
 *   2. Window blur     — window.blur (user switched to another app)
 *   3. Window focus    — window.focus (user returned)
 *   4. DevTools open   — window size change heuristic (optional)
 *
 * Every detected event is POSTed to POST /api/proctor/violation.
 * A COOLDOWN_MS guard prevents duplicate events being sent in rapid succession.
 *
 * Usage:
 *   TabMonitor.start(sessionId, token);
 *   TabMonitor.stop();
 *   TabMonitor.onAlert = (event) => { showToast(event.message); };
 */

const TabMonitor = (() => {

  // ── Configuration ─────────────────────────────────────────────────────────
  const API_BASE    = 'http://localhost:8080';
  const COOLDOWN_MS = 5000;  // min ms between sending the same violation type

  // ── Private state ─────────────────────────────────────────────────────────
  let _sessionId = null;
  let _token     = null;
  let _running   = false;
  let _lastSent  = {};   // { violation_type: Date.now() }

  // DevTools detection
  let _devtoolsThreshold = 160;
  let _devtoolsTimer     = null;

  // ── Public callback — set by exam.html ───────────────────────────────────
  /** Called with { type, message } when a violation fires. */
  let onAlert = null;

  // ── Public API ────────────────────────────────────────────────────────────

  function start(sessionId, token) {
    if (_running) return;
    _sessionId = sessionId;
    _token     = token;
    _running   = true;
    _lastSent  = {};

    // Bind all event listeners
    document.addEventListener('visibilitychange', _onVisibilityChange);
    window.addEventListener('blur',  _onWindowBlur);
    window.addEventListener('focus', _onWindowFocus);

    // Optional: devtools detection via resize heuristic
    _devtoolsTimer = setInterval(_checkDevtools, 1500);

    console.info('[TabMonitor] Started. Session:', sessionId);
  }

  function stop() {
    if (!_running) return;
    _running = false;

    document.removeEventListener('visibilitychange', _onVisibilityChange);
    window.removeEventListener('blur',  _onWindowBlur);
    window.removeEventListener('focus', _onWindowFocus);

    if (_devtoolsTimer) {
      clearInterval(_devtoolsTimer);
      _devtoolsTimer = null;
    }
    console.info('[TabMonitor] Stopped.');
  }

  function isRunning() { return _running; }

  // ── Private: event handlers ───────────────────────────────────────────────

  function _onVisibilityChange() {
    if (!_running) return;
    if (document.hidden) {
      _send('TAB_SWITCH', 'Student switched browser tab or minimised window');
    }
  }

  function _onWindowBlur() {
    if (!_running) return;
    // Small delay so we don't fire if focus moves to devtools (caught separately)
    setTimeout(() => {
      if (!document.hasFocus()) {
        _send('TAB_SWITCH', 'Browser window lost focus');
      }
    }, 300);
  }

  function _onWindowFocus() {
    // Focus return is not a violation — log locally for debugging only
    console.info('[TabMonitor] Window focus returned.');
  }

  // ── DevTools detection (heuristic) ───────────────────────────────────────

  /**
   * Checks whether devtools is open by comparing the outer vs inner window
   * dimensions. Works best for undocked devtools panel.
   * May produce false positives on very small monitors — adjust threshold.
   */
  function _checkDevtools() {
    if (!_running) return;
    const widthDiff  = window.outerWidth  - window.innerWidth;
    const heightDiff = window.outerHeight - window.innerHeight;
    if (widthDiff > _devtoolsThreshold || heightDiff > _devtoolsThreshold) {
      _send('TAB_SWITCH', 'Developer tools may be open (window size mismatch)');
    }
  }

  // ── Private: send violation to backend ───────────────────────────────────

  async function _send(type, description) {
    // Cooldown guard
    const now = Date.now();
    if (_lastSent[type] && (now - _lastSent[type]) < COOLDOWN_MS) {
      console.debug('[TabMonitor] Cooldown active for', type);
      return;
    }
    _lastSent[type] = now;

    // Fire the public callback so exam.html can show a warning toast
    if (typeof onAlert === 'function') {
      onAlert({ type, message: description });
    }

    const payload = {
      sessionId  : _sessionId,
      type,
      timestamp  : new Date().toISOString(),
      description,
    };

    try {
      const res = await fetch(`${API_BASE}/api/proctor/violation`, {
        method : 'POST',
        headers: {
          'Content-Type' : 'application/json',
          'Authorization': `Bearer ${_token}`,
        },
        body: JSON.stringify(payload),
      });

      if (res.ok) {
        console.warn(`[TabMonitor] Violation logged: ${type}`);
      } else if (res.status === 401) {
        stop();   // JWT expired
      }
    } catch (err) {
      // Network error — violation still fired locally
      console.error('[TabMonitor] Failed to send violation:', err.message);
    }
  }

  // ── Expose public interface ───────────────────────────────────────────────
  return {
    start,
    stop,
    isRunning,
    set onAlert(fn) { onAlert = fn; },
  };

})();
