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
        ca-certificates \
        curl \
        fonts-liberation \
        libgbm1 \
        libgtk-3-0 \
        libnss3 \
        libxss1 \
        gnupg \
        tesseract-ocr \
        unzip \
        wget \
    && install -d -m 0755 /etc/apt/keyrings \
    && wget -q -O- https://dl.google.com/linux/linux_signing_key.pub | gpg --dearmor -o /etc/apt/keyrings/google-chrome.gpg \
    && chmod a+r /etc/apt/keyrings/google-chrome.gpg \
    && echo "deb [arch=amd64 signed-by=/etc/apt/keyrings/google-chrome.gpg] https://dl.google.com/linux/chrome/deb/ stable main" > /etc/apt/sources.list.d/google-chrome.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends google-chrome-stable \
    && CHROME_VERSION="$(google-chrome --product-version | cut -d '.' -f 1-3)" \
    && DRIVER_VERSION="$(curl -fsSL "https://googlechromelabs.github.io/chrome-for-testing/LATEST_RELEASE_${CHROME_VERSION}")" \
    && wget -q -O /tmp/chromedriver.zip "https://storage.googleapis.com/chrome-for-testing-public/${DRIVER_VERSION}/linux64/chromedriver-linux64.zip" \
    && unzip -q /tmp/chromedriver.zip -d /opt \
    && ln -sf /usr/bin/google-chrome /usr/local/bin/chrome \
    && ln -sf /opt/chromedriver-linux64/chromedriver /usr/local/bin/chromedriver \
    && rm -rf /tmp/chromedriver.zip /opt/chromedriver-linux64/LICENSE.chromedriver /opt/chromedriver-linux64/THIRD_PARTY_NOTICES.chromedriver \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /workspace/build/libs/*.jar app.jar
COPY --from=builder /workspace/tessdata ./tessdata

ENV DEALER_TESSDATA_PATH=/app/tessdata
ENV CHROME_BIN=/usr/local/bin/chrome
ENV CHROMEDRIVER_PATH=/usr/local/bin/chromedriver

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
