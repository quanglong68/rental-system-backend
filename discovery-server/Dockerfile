# Task B1: build tu repo root (compose: context .., dockerfile discovery-server/Dockerfile)
# de thay duoc parent pom. Tu chay: docker build -f discovery-server/Dockerfile .
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY discovery-server ./discovery-server
RUN mvn -q -pl discovery-server package -DskipTests

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/discovery-server/target/discovery-server.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-Dfile.encoding=UTF-8", "-jar", "app.jar"]
