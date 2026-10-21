# Luban data server. Coolify: New resource → this repository → Build pack "Dockerfile", base directory /server,
# port 8080, health check path /health. Set SPORTSDB_KEY if you have a paid key.
FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 PORT=8080
RUN useradd --system --uid 10001 luban
WORKDIR /app
COPY app.py .
USER luban
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD python -c "import urllib.request,sys;urllib.request.urlopen('http://127.0.0.1:8080/health',timeout=4)" || exit 1
CMD ["python", "app.py"]
