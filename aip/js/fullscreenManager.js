/**
 * fullscreenManager.js — AI Proctoring Fullscreen Enforcement
 *
 * Responsibilities:
 *   1. Enter fullscreen automatically when the exam starts.
 *   2. Listen for fullscreenchange events — log FULLSCREEN_EXIT violation.
 *   3. Intercept ESC key before it can exit fullscreen (where possible).
 *   4. Show a warning overlay when not in fullscreen, with a "Re-enter" button.
 *
 * Fullscreen API notes:
 *   - requestFullscreen() must be called from a user-gesture context.
 *   - Browsers CANNOT prevent ESC from exiting fullscreen — we can only detect
 *     it after the fact and re-request fullscreen.
 *   - iOS Safari does not support the Fullscreen API at all.
 *
 * Usage:
 *   await FullscreenManager.enter();
 *   FullscreenManager.start(sessionId, token);    // begin monitoring
 *   FullscreenManager.stop();
 *   FullscreenManager.onExit = (msg) => { ... };  // set before start()
 */

const FullscreenManager = (() => {

  // ── Config ────────────────────────────────────────────────────────────────
  const API_BASE    = 'http://localhost:8080';
  const COOLDOWN_MS = 6000;

  // ── Private state ─────────────────────────────────────────────────────────
  let _sessionId  = null;
  let _token      = null;
  let _running    = false;
  let _lastSent   = 0;
  let _warningEl  = null;
  let _overlayEl  = null;

  // ── Public callbacks ──────────────────────────────────────────────────────
  let onExit  = null;
  let onEnter = null;

  // ── Public API ────────────────────────────────────────────────────────────

  /**
   * Request fullscreen on document.documentElement.
   * Must be called within a user-gesture handler (button click, etc.).
   * Returns true on success, false if the browser rejected or API unavailable.
   */
  async function enter() {
    const el   = document.documentElement;
    const api  = el.requestFullscreen
              || el.webkitRequestFullscreen
              || el.mozRequestFullScreen
              || el.msRequestFullscreen;

    if (!api) {
      console.warn('[FullscreenManager] Fullscreen API not supported.');
      return false;
    }
    try {
      await api.call(el);
      return true;
    } catch (e) {
      console.warn('[FullscreenManager] Could not enter fullscreen:', e.message);
      return false;
    }
  }

  /** True if the document is currently fullscreen. */
  function isFullscreen() {
    return !!(
      document.fullscreenElement         ||
      document.webkitFullscreenElement   ||
      document.mozFullScreenElement      ||
      document.msFullscreenElement
    );
  }

  /**
   * Start fullscreen monitoring.
   * Call this after a successful enter().
   */
  function start(sessionId, token) {
    _sessionId = sessionId;
    _token     = token;
    _running   = true;

    // Find or create the fullscreen-warning overlay in the DOM
    _overlayEl  = document.getElementById('fsWarningOverlay');
    _warningEl  = document.getElementById('fsWarningMsg');

    document.addEventListener('fullscreenchange',       _onFullscreenChange);
    document.addEventListener('webkitfullscreenchange', _onFullscreenChange);
    document.addEventListener('mozfullscreenchange',    _onFullscreenChange);
    document.addEventListener('MSFullscreenChange',     _onFullscreenChange);

    // Intercept ESC keydown — we can log it but cannot prevent fullscreen exit
    document.addEventListener('keydown', _onKeyDown, true);

    console.info('[FullscreenManager] Monitoring started. Session:', sessionId);
  }

  function stop() {
    _running = false;
    document.removeEventListener('fullscreenchange',       _onFullscreenChange);
    document.removeEventListener('webkitfullscreenchange', _onFullscreenChange);
    document.removeEventListener('mozfullscreenchange',    _onFullscreenChange);
    document.removeEventListener('MSFullscreenChange',     _onFullscreenChange);
    document.removeEventListener('keydown', _onKeyDown, true);
    _hideWarning();
  }

  // ── Private: event handlers ───────────────────────────────────────────────

  function _onFullscreenChange() {
    if (!_running) return;

    if (isFullscreen()) {
      // Student re-entered fullscreen (via the overlay button)
      _hideWarning();
      if (typeof onEnter === 'function') onEnter();
      console.info('[FullscreenManager] Fullscreen restored.');
    } else {
      // Student exited fullscreen
      _showWarning();
      _sendViolation();
      if (typeof onExit === 'function') {
        onExit('⚠️ You exited fullscreen. Please click "Re-enter Fullscreen" to continue.');
      }
    }
  }

  function _onKeyDown(e) {
    if (!_running) return;
    // Log ESC key — browser will still exit fullscreen, we capture the attempt
    if (e.key === 'Escape' || e.keyCode === 27) {
      console.warn('[FullscreenManager] ESC pressed — fullscreen exit imminent.');
      // Violation will be logged by _onFullscreenChange above
    }
  }

  // ── Private: warning overlay ──────────────────────────────────────────────

  function _showWarning() {
    if (_overlayEl) {
      _overlayEl.style.display = 'flex';
      if (_warningEl) {
        _warningEl.textContent =
          '⚠️ You have exited fullscreen mode. This has been recorded as a violation. ' +
          'Click the button below to return to fullscreen and continue your exam.';
      }
    }
  }

  function _hideWarning() {
    if (_overlayEl) _overlayEl.style.display = 'none';
  }

  // ── Private: send violation ───────────────────────────────────────────────

  async function _sendViolation() {
    const now = Date.now();
    if (now - _lastSent < COOLDOWN_MS) return;
    _lastSent = now;

    const payload = {
      sessionId  : _sessionId,
      type       : 'FULLSCREEN_EXIT',
      timestamp  : new Date().toISOString(),
      description: 'Student exited exam fullscreen mode',
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
      if (res.ok) console.warn('[FullscreenManager] FULLSCREEN_EXIT violation logged.');
    } catch (e) {
      console.error('[FullscreenManager] Failed to send violation:', e.message);
    }
  }

  // ── Expose public interface ───────────────────────────────────────────────
  return {
    enter,
    start,
    stop,
    isFullscreen,
    set onExit(fn)  { onExit  = fn; },
    set onEnter(fn) { onEnter = fn; },
  };

})();
