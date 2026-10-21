FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY recommender.py .
COPY api.py .

RUN useradd --create-home appuser
USER appuser

EXPOSE 8000

CMD [
    "python",
    "-m",
    "uvicorn",
    "api:app",
    "--host",
    "0.0.0.0",
    "--port",
    "8000"
]