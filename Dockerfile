FROM tomcat:9.0-jdk8

RUN rm -rf /usr/local/tomcat/webapps/*

COPY target/smart-vehicle-tracker.war /usr/local/tomcat/webapps/ROOT.war

EXPOSE 8080

CMD ["catalina.sh", "run"]