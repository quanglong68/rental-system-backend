FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn -q -pl asset-maintenance-service -am package -DskipTests
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/asset-maintenance-service/target/asset-maintenance-service.jar app.jar
EXPOSE 8087
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-Dfile.encoding=UTF-8", "-jar", "app.jar"]
