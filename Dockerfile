FROM gradle:8.14.2-jdk21 AS builder

WORKDIR /workspace

COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY gradlew ./
COPY src ./src
COPY tessdata ./tessdata

RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends software-properties-common \
    && add-apt-repository universe \
    && apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr \
    && apt-get purge -y --auto-remove software-properties-common \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /workspace/build/libs/*.jar app.jar
COPY --from=builder /workspace/tessdata ./tessdata

ENV DEALER_TESSDATA_PATH=/app/tessdata

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
