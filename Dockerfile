FROM maven:3.9.16-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B clean verify

FROM tomcat:10.1.61-jre17-temurin
COPY --from=build /workspace/target/habitflow.war /usr/local/tomcat/webapps/ROOT.war
EXPOSE 10000
CMD ["sh", "-c", "sed -i \"s/port=\\\"8080\\\"/port=\\\"${PORT:-10000}\\\"/\" \"$CATALINA_HOME/conf/server.xml\" && exec catalina.sh run"]
