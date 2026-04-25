"""
AI Proctoring - FastAPI Detection Service
Runs on port 8001
Endpoint: POST /analyze-frame
"""

import base64
import time
import os
import logging
from typing import List, Optional

import cv2
import mediapipe as mp
import numpy as np
from fastapi import FastAPI, HTTPException, Header
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

# ── Logging ───────────────────────────────────────────────────────────────────
logging.basicConfig(level=logging.INFO)
log = logging.getLogger("ai_proctor")

# ── Config ────────────────────────────────────────────────────────────────────
AI_SECRET = os.getenv("AI_SERVICE_SECRET", "change-me-in-production")

# ── FastAPI app ───────────────────────────────────────────────────────────────
app = FastAPI(title="AI Proctor Detection Service", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:8080"],
    allow_methods=["POST", "GET"],
    allow_headers=["*"],
)

# ── MediaPipe setup ───────────────────────────────────────────────────────────
mp_face_detection = mp.solutions.face_detection
mp_face_mesh      = mp.solutions.face_mesh

face_detector = mp_face_detection.FaceDetection(
    model_selection=0,       # 0 = short range (best for webcam)
    min_detection_confidence=0.6
)

face_mesh = mp_face_mesh.FaceMesh(
    max_num_faces=4,
    refine_landmarks=True,
    min_detection_confidence=0.6,
    min_tracking_confidence=0.6
)

# ── Request / Response models ─────────────────────────────────────────────────
class FrameRequest(BaseModel):
    session_id:    int
    exam_id:       Optional[int] = None
    frame_base64:  str
    sequence_num:  Optional[int] = None
    timestamp_ms:  Optional[int] = None

class AiViolation(BaseModel):
    violation_type: str
    severity:       str
    confidence:     float
    description:    str

class FrameResponse(BaseModel):
    session_id:         int
    exam_id:            Optional[int]
    face_count:         int
    violations:         List[AiViolation]
    processing_time_ms: float
    frame_width:        int
    frame_height:       int
    clean:              bool

# ── Gaze detection helpers ────────────────────────────────────────────────────
# MediaPipe Face Mesh landmark indices for eyes
LEFT_EYE_INNER  = 468   # left iris center (with refine_landmarks=True)
RIGHT_EYE_INNER = 473
NOSE_TIP        = 1
LEFT_EYE_CORNER = 33
RIGHT_EYE_CORNER= 263

def _estimate_gaze(landmarks, img_w, img_h):
    """
    Returns (looking_away: bool, confidence: float).
    Uses horizontal iris position relative to eye corners.
    """
    try:
        def pt(idx):
            lm = landmarks.landmark[idx]
            return np.array([lm.x * img_w, lm.y * img_h])

        left_corner  = pt(LEFT_EYE_CORNER)
        right_corner = pt(RIGHT_EYE_CORNER)
        nose         = pt(NOSE_TIP)

        face_width = np.linalg.norm(right_corner - left_corner)
        if face_width < 10:
            return False, 0.0

        face_center_x = (left_corner[0] + right_corner[0]) / 2
        nose_offset   = abs(nose[0] - face_center_x) / face_width

        # If nose shifts more than 35% of face width, student is looking away
        if nose_offset > 0.35:
            confidence = min(1.0, nose_offset / 0.5)
            return True, round(confidence, 2)

        return False, 0.0

    except Exception:
        return False, 0.0

# ── Health check ──────────────────────────────────────────────────────────────
@app.get("/health")
def health():
    return {"status": "UP", "service": "AI Proctor Detection"}

# ── Main analysis endpoint ────────────────────────────────────────────────────
@app.post("/analyze-frame", response_model=FrameResponse)
def analyze_frame(
    request: FrameRequest,
    x_ai_secret: Optional[str] = Header(None)
):
    # Secret validation
    if x_ai_secret != AI_SECRET:
        raise HTTPException(status_code=403, detail="Invalid AI service secret")

    t_start = time.time()
    violations: List[AiViolation] = []

    # ── Decode base64 frame ──────────────────────────────────────────────────
    try:
        img_bytes = base64.b64decode(request.frame_base64)
        img_array = np.frombuffer(img_bytes, dtype=np.uint8)
        frame     = cv2.imdecode(img_array, cv2.IMREAD_COLOR)
        if frame is None:
            raise ValueError("Could not decode image")
    except Exception as e:
        log.error("Frame decode failed for session %d: %s", request.session_id, e)
        raise HTTPException(status_code=400, detail="Invalid frame data")

    img_h, img_w = frame.shape[:2]
    rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)

    # ── Face detection ───────────────────────────────────────────────────────
    det_result  = face_detector.process(rgb_frame)
    face_count  = 0

    if det_result.detections:
        face_count = len(det_result.detections)

    # NO FACE detected
    if face_count == 0:
        violations.append(AiViolation(
            violation_type="NO_FACE",
            severity="HIGH",
            confidence=0.92,
            description="No face detected in webcam frame"
        ))

    # MULTIPLE FACES detected
    elif face_count > 1:
        violations.append(AiViolation(
            violation_type="MULTIPLE_FACES",
            severity="CRITICAL",
            confidence=0.95,
            description=f"{face_count} faces detected in webcam frame"
        ))

    # GAZE detection (only when exactly one face is visible)
    if face_count == 1:
        mesh_result = face_mesh.process(rgb_frame)
        if mesh_result.multi_face_landmarks:
            lm = mesh_result.multi_face_landmarks[0]
            looking_away, gaze_conf = _estimate_gaze(lm, img_w, img_h)
            if looking_away and gaze_conf >= 0.6:
                violations.append(AiViolation(
                    violation_type="LOOKING_AWAY",
                    severity="MEDIUM",
                    confidence=gaze_conf,
                    description="Student appears to be looking away from screen"
                ))

    processing_ms = round((time.time() - t_start) * 1000, 2)

    log.info(
        "Session=%d seq=%s faces=%d violations=%d time=%.1fms",
        request.session_id,
        request.sequence_num,
        face_count,
        len(violations),
        processing_ms
    )

    return FrameResponse(
        session_id         = request.session_id,
        exam_id            = request.exam_id,
        face_count         = face_count,
        violations         = violations,
        processing_time_ms = processing_ms,
        frame_width        = img_w,
        frame_height       = img_h,
        clean              = len(violations) == 0
    )
