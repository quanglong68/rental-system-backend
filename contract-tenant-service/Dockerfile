FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY contract-tenant-service/target/contract-tenant-service.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-Dfile.encoding=UTF-8", "-jar", "app.jar"]
