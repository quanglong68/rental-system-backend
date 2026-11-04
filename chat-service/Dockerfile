FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY chat-service/target/chat-service.jar app.jar
EXPOSE 8088
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-Dfile.encoding=UTF-8", "-jar", "app.jar"]
