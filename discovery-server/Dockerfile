FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY discovery-server/target/discovery-server.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-Dfile.encoding=UTF-8", "-jar", "app.jar"]