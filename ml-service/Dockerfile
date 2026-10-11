FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    ML_MODEL_PATH=/app/artifacts/no_show_model.cbm

WORKDIR /app

COPY requirements.txt .
RUN pip install -r requirements.txt

COPY app ./app
COPY wsgi.py .
RUN python -m app.model.train --out artifacts

RUN useradd --system --no-create-home service && chown -R service /app
USER service

EXPOSE 5001

HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=5 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:5001/health', timeout=2)"

CMD ["gunicorn", "--bind", "0.0.0.0:5001", "--workers", "2", "--timeout", "30", "wsgi:app"]
