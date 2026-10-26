FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY discovery-server/pom.xml discovery-server/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY user-notification-service/pom.xml user-notification-service/pom.xml
COPY contract-tenant-service/pom.xml contract-tenant-service/pom.xml
COPY common-lib ./common-lib
COPY asset-maintenance-service ./asset-maintenance-service
RUN mvn -q -pl asset-maintenance-service -am package -DskipTests
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/asset-maintenance-service/target/asset-maintenance-service.jar app.jar
EXPOSE 8087
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-Dfile.encoding=UTF-8", "-jar", "app.jar"]
