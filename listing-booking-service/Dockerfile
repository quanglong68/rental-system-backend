FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY listing-booking-service/target/listing-booking-service.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-Dfile.encoding=UTF-8", "-jar", "app.jar"]
