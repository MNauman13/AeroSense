FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1
WORKDIR /app
COPY analytics/pyproject.toml analytics/uv.lock ./
COPY analytics/app ./app
COPY analytics/reference_notes ./reference_notes
RUN pip install --no-cache-dir uv \
    && uv sync --frozen --no-dev
EXPOSE 8000
CMD ["uv", "run", "--no-sync", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
