# Luban data server. Coolify: New resource → this repository → Build pack "Dockerfile", base directory /server,
# port 8080, health check path /health. Set FOOTBALL_DATA_KEY (free token from football-data.org) for the matches.
FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 PORT=8080
RUN useradd --system --uid 10001 luban && mkdir -p /data/dinner && chown -R luban /data
WORKDIR /app
COPY app.py .
USER luban
# the dinner plans: give /data a persistent volume in Coolify, or they are lost when the container is rebuilt
VOLUME /data
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD python -c "import urllib.request,sys;urllib.request.urlopen('http://127.0.0.1:8080/health',timeout=4)" || exit 1
CMD ["python", "app.py"]
