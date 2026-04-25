# Phase 4 — FastAPI AI Detection Service

## What this does
- Receives webcam frames from Spring Boot
- Detects: No Face, Multiple Faces, Looking Away
- Returns violations back to Spring Boot which logs them

## Setup

### 1. Copy ai_service folder
Place the `ai_service` folder at:
D:\AI-Proctoring-System\ai_service\

### 2. Install Python dependencies
```
cd D:\AI-Proctoring-System\ai_service
pip install -r requirements.txt
```

### 3. Start the AI service
```
start.bat
```
OR
```
python -m uvicorn main:app --host 0.0.0.0 --port 8001 --reload
```

### 4. Test health check
```
curl http://localhost:8001/health
```
Expected: {"status":"UP","service":"AI Proctor Detection"}

### 5. Add to application-dev.properties (Spring Boot)
```
app.ai.service.url=http://localhost:8001
app.ai.service.secret=change-me-in-production
```

## Detection Logic
- NO_FACE       → HIGH severity    → triggers when 0 faces detected
- MULTIPLE_FACES → CRITICAL severity → triggers when 2+ faces detected
- LOOKING_AWAY  → MEDIUM severity  → triggers when gaze offset > 35%

## Ports
- Spring Boot : 8080
- FastAPI     : 8001
- Frontend    : 5500
