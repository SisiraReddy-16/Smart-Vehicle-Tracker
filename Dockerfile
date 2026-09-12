FROM tomcat:9.0-jdk17

RUN rm -rf /usr/local/tomcat/webapps/*

COPY backend/target/smart-vehicle-tracker.war /tmp/app.war

RUN mkdir -p /usr/local/tomcat/webapps/ROOT \
    && cd /usr/local/tomcat/webapps/ROOT \
    && jar -xf /tmp/app.war

COPY frontend/ /usr/local/tomcat/webapps/ROOT/

EXPOSE 8080

CMD ["catalina.sh", "run"]