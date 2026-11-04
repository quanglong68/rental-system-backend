FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY asset-maintenance-service/target/asset-maintenance-service.jar app.jar
EXPOSE 8087
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-Dfile.encoding=UTF-8", "-jar", "app.jar"]
