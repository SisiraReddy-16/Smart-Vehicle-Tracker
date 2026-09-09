# ---------- Stage 1: build the WAR ----------
FROM maven:3.9.9-eclipse-temurin-8 AS build
WORKDIR /app
 
# Cache dependencies separately so code changes don't re-download the internet
COPY backend/pom.xml .
RUN mvn -B -q dependency:go-offline
 
COPY backend/src ./src
RUN mvn -B -q clean package -DskipTests
# -> produces /app/target/smart-vehicle-tracker.war
 
# ---------- Stage 2: runtime image ----------
FROM tomcat:9.0-jdk8-temurin AS runtime
 
ENV APP_CTX=smart-vehicle-tracker
ENV CATALINA_OPTS="-Xms256m -Xmx512m"
 
# curl only used for the HEALTHCHECK below
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*
 
# Remove Tomcat's default apps - keep the image lean and avoid sample-app CVEs
RUN rm -rf /usr/local/tomcat/webapps/ROOT \
           /usr/local/tomcat/webapps/docs \
           /usr/local/tomcat/webapps/examples \
           /usr/local/tomcat/webapps/manager \
           /usr/local/tomcat/webapps/host-manager
 
# Explode the WAR ourselves so we can merge the static frontend into the
# same context - this gives ONE container serving both UI and /api/*,
# so a single image/container/public IP is the whole application.
COPY --from=build /app/target/smart-vehicle-tracker.war /tmp/app.war
RUN mkdir -p /usr/local/tomcat/webapps/${APP_CTX} \
 && cd /usr/local/tomcat/webapps/${APP_CTX} \
 && jar -xf /tmp/app.war \
 && rm -f /tmp/app.war
 
# Static frontend goes into the same context root, alongside WEB-INF/
# (frontend/index.html, frontend/login/, frontend/dashboard/, etc.)
COPY frontend/ /usr/local/tomcat/webapps/${APP_CTX}/
 
# DB creds are injected at "docker run" / task-definition time, NEVER baked in:
#   -e DB_URL="jdbc:mysql://<rds-endpoint>:3306/garagepulse?useSSL=true&serverTimezone=UTC"
#   -e DB_USER="..."  -e DB_PASSWORD="..."
# DBConnection.java already prefers these env vars over db.properties.
 
EXPOSE 8080
 
HEALTHCHECK --interval=30s --timeout=5s --start-period=25s --retries=3 \
  CMD curl -sf http://localhost:8080/${APP_CTX}/login/index.html || exit 1
 
CMD ["catalina.sh", "run"]