FROM gradle:8.14.2-jdk21 AS builder

WORKDIR /workspace

COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY gradlew ./
COPY src ./src
COPY tessdata ./tessdata

RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        chromium \
        chromium-driver \
        fonts-liberation \
        libgbm1 \
        libgtk-3-0 \
        libnss3 \
        libxss1 \
        tesseract-ocr \
    && ln -sf "$(command -v chromium || command -v chromium-browser)" /usr/local/bin/chrome \
    && ln -sf "$(command -v chromedriver)" /usr/local/bin/chromedriver \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /workspace/build/libs/*.jar app.jar
COPY --from=builder /workspace/tessdata ./tessdata

ENV DEALER_TESSDATA_PATH=/app/tessdata
ENV CHROME_BIN=/usr/local/bin/chrome
ENV CHROMEDRIVER_PATH=/usr/local/bin/chromedriver

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
