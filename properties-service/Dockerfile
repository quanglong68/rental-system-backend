# Build the jar first from the backend root (same convention as the other modules):
#   mvn -pl properties-service -am package -DskipTests
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY properties-service/target/properties-service.jar app.jar

RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8083

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8083/actuator/health || exit 1

ENTRYPOINT ["java", "-Duser.timezone=UTC", "-Dfile.encoding=UTF-8", "-jar", "app.jar"]
