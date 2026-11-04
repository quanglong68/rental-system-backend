FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY auth-service/target/auth-service.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-Dfile.encoding=UTF-8", "-jar", "app.jar"]
