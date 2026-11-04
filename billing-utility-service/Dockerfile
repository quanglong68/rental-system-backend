FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY billing-utility-service/target/billing-utility-service.jar app.jar
EXPOSE 8086
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-Dfile.encoding=UTF-8", "-jar", "app.jar"]
