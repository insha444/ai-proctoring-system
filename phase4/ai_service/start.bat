@echo off
echo Starting AI Proctor Detection Service...
echo.

cd /d %~dp0

REM Install dependencies if not already installed
pip install -r requirements.txt --quiet

echo.
echo AI Service starting on http://localhost:8001
echo Press Ctrl+C to stop
echo.

python -m uvicorn main:app --host 0.0.0.0 --port 8001 --reload
