FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY user-notification-service/target/user-notification-service.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-Dfile.encoding=UTF-8", "-jar", "app.jar"]
