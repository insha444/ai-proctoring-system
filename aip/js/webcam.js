/**
 * webcam.js — AI Proctoring Webcam Module
 *
 * Responsibilities:
 *   1. Request webcam access via getUserMedia
 *   2. Render live video into a <video> element
 *   3. Capture a JPEG frame every CAPTURE_INTERVAL_MS via <canvas>
 *   4. Strip the data-URI prefix and POST the raw base64 to the backend
 *
 * Usage (exam.html calls these):
 *   await WebcamMonitor.start(videoEl, canvasEl, sessionId, token);
 *   WebcamMonitor.stop();
 *   WebcamMonitor.onViolation = (result) => { ... };
 */

const WebcamMonitor = (() => {

  // ── Configuration ─────────────────────────────────────────────────────────
  const API_BASE           = 'http://localhost:8080';
  const CAPTURE_INTERVAL_MS = 1000;   // send a frame every 1 second
  const JPEG_QUALITY        = 0.6;    // 0.0–1.0; lower = smaller payload
  const MAX_WIDTH           = 320;    // resize before encoding (saves bandwidth)
  const MAX_HEIGHT          = 240;

  // ── Private state ─────────────────────────────────────────────────────────
  let _stream       = null;
  let _videoEl      = null;
  let _canvasEl     = null;
  let _ctx          = null;
  let _sessionId    = null;
  let _token        = null;
  let _captureTimer = null;
  let _running      = false;
  let _failCount    = 0;

  const MAX_CONSECUTIVE_FAILS = 5;  // stop trying after this many 4xx/5xx

  // ── Public callback — set by exam.html ───────────────────────────────────
  /** Called after every successful backend response with the FrameResult. */
  let onViolation = null;
  /** Called when webcam is denied or unavailable. */
  let onError     = null;

  // ── Public API ────────────────────────────────────────────────────────────

  /**
   * Request webcam, attach to video element, start frame capture loop.
   *
   * @param {HTMLVideoElement}  videoEl    — preview <video> tag
   * @param {HTMLCanvasElement} canvasEl   — hidden <canvas> for frame extraction
   * @param {number}            sessionId  — active exam session ID
   * @param {string}            token      — JWT bearer token
   * @returns {Promise<boolean>}  true if webcam was successfully started
   */
  async function start(videoEl, canvasEl, sessionId, token) {
    _videoEl   = videoEl;
    _canvasEl  = canvasEl;
    _ctx       = canvasEl.getContext('2d');
    _sessionId = sessionId;
    _token     = token;

    // Check browser support
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      _fireError('getUserMedia is not supported in this browser.');
      return false;
    }

    try {
      _stream = await navigator.mediaDevices.getUserMedia({
        video: {
          width:       { ideal: MAX_WIDTH  },
          height:      { ideal: MAX_HEIGHT },
          facingMode:  'user',
          frameRate:   { ideal: 15, max: 30 },
        },
        audio: false,
      });

      // Attach stream to video element
      _videoEl.srcObject = _stream;
      await _videoEl.play();

      _running   = true;
      _failCount = 0;

      // Wait one extra second so the video element is fully ready
      setTimeout(_startCaptureLoop, 1200);

      console.info('[WebcamMonitor] Webcam started. Session:', sessionId);
      return true;

    } catch (err) {
      const msg = _mapGumError(err);
      _fireError(msg);
      return false;
    }
  }

  /** Stop the capture loop and release the camera. */
  function stop() {
    _running = false;
    if (_captureTimer) {
      clearInterval(_captureTimer);
      _captureTimer = null;
    }
    if (_stream) {
      _stream.getTracks().forEach(t => t.stop());
      _stream = null;
    }
    if (_videoEl) {
      _videoEl.srcObject = null;
    }
    console.info('[WebcamMonitor] Webcam stopped.');
  }

  /** Returns true if the webcam is currently active. */
  function isRunning() { return _running; }

  // ── Private: capture loop ─────────────────────────────────────────────────

  function _startCaptureLoop() {
    if (!_running) return;
    _captureTimer = setInterval(_captureAndSend, CAPTURE_INTERVAL_MS);
  }

  async function _captureAndSend() {
    if (!_running || !_videoEl || !_ctx) return;

    // Skip if video isn't ready
    if (_videoEl.readyState < 2) return;

    try {
      // Draw current video frame onto canvas
      _canvasEl.width  = MAX_WIDTH;
      _canvasEl.height = MAX_HEIGHT;
      _ctx.drawImage(_videoEl, 0, 0, MAX_WIDTH, MAX_HEIGHT);

      // Encode as JPEG base64
      const dataUrl    = _canvasEl.toDataURL('image/jpeg', JPEG_QUALITY);
      const frameBase64 = dataUrl.split(',')[1];   // strip "data:image/jpeg;base64,"

      if (!frameBase64) return;

      // POST to backend
      const res = await fetch(`${API_BASE}/api/proctor/frame`, {
        method: 'POST',
        headers: {
          'Content-Type' : 'application/json',
          'Authorization': `Bearer ${_token}`,
        },
        body: JSON.stringify({ sessionId: _sessionId, frameBase64 }),
      });

      if (res.ok) {
        _failCount = 0;
        const result = await res.json();
        if (typeof onViolation === 'function' && result) {
          onViolation(result);
        }
      } else if (res.status === 401) {
        // JWT expired — stop monitoring gracefully
        console.warn('[WebcamMonitor] 401 Unauthorised — stopping capture.');
        stop();
      } else {
        _failCount++;
        if (_failCount >= MAX_CONSECUTIVE_FAILS) {
          console.error('[WebcamMonitor] Too many failures — stopping capture.');
          stop();
        }
      }
    } catch (networkErr) {
      // Silent network errors — backend may be briefly unavailable
      _failCount++;
      if (_failCount >= MAX_CONSECUTIVE_FAILS) {
        console.warn('[WebcamMonitor] Network errors threshold reached.', networkErr);
        stop();
      }
    }
  }

  // ── Private: helpers ──────────────────────────────────────────────────────

  function _fireError(msg) {
    console.error('[WebcamMonitor] Error:', msg);
    if (typeof onError === 'function') onError(msg);
  }

  function _mapGumError(err) {
    if (err.name === 'NotAllowedError' || err.name === 'PermissionDeniedError') {
      return 'Webcam access was denied. Please allow camera permission and reload.';
    }
    if (err.name === 'NotFoundError' || err.name === 'DevicesNotFoundError') {
      return 'No webcam found. Please connect a camera and reload.';
    }
    if (err.name === 'NotReadableError' || err.name === 'TrackStartError') {
      return 'Webcam is in use by another application.';
    }
    if (err.name === 'OverconstrainedError') {
      return 'Webcam does not meet the required constraints.';
    }
    return `Webcam error: ${err.message}`;
  }

  // ── Expose public interface ───────────────────────────────────────────────
  return {
    start,
    stop,
    isRunning,
    set onViolation(fn) { onViolation = fn; },
    set onError(fn)     { onError = fn; },
  };

})();
