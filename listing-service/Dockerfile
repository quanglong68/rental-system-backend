# Build stage
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app

# Copy root pom and child modules (required for multi-module build)
COPY pom.xml .
COPY common-lib/ common-lib/
COPY discovery-server/ discovery-server/
COPY api-gateway/ api-gateway/
COPY auth-service/ auth-service/
COPY user-notification-service/ user-notification-service/
COPY asset-maintenance-service/ asset-maintenance-service/
COPY contract-tenant-service/ contract-tenant-service/
COPY listing-service/ listing-service/

# Build listing-service and its dependencies
RUN mvn -q -pl listing-service -am package -DskipTests

# Final stage
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/listing-service/target/listing-service.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-Dfile.encoding=UTF-8", "-jar", "app.jar"]
