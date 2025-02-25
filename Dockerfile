# Build stage
FROM maven:3.9-ibm-semeru-21-jammy AS build
WORKDIR /usr/local/app
COPY . .
RUN mvn clean package -DskipTests

# Run stage
FROM ibm-semeru-runtimes:open-21-jre-jammy

# See https://googlechromelabs.github.io/chrome-for-testing/
ENV CHROMEDRIVER_VERSION=133.0.6943.142

# Install dependencies, Chrome, and ChromeDriver
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        wget \
        unzip \
        gnupg2 \
        ca-certificates && \
    wget -q -O - https://dl.google.com/linux/linux_signing_key.pub | apt-key add - && \
    echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" > /etc/apt/sources.list.d/google.list && \
    apt-get update && \
    apt-get install -y --no-install-recommends google-chrome-stable && \
    wget -q "https://edgedl.me.gvt1.com/edgedl/chrome/chrome-for-testing/${CHROMEDRIVER_VERSION}/linux64/chromedriver-linux64.zip" -O /tmp/chromedriver-linux64.zip && \
    unzip /tmp/chromedriver-linux64.zip -d /usr/local/bin/ && \
    mv /usr/local/bin/chromedriver-linux64/chromedriver /usr/local/bin/chromedriver && \
    chmod +x /usr/local/bin/chromedriver && \
    rm -rf /var/lib/apt/lists/* /tmp/* /usr/local/bin/chromedriver-linux64

# Copy the application jar
COPY --from=build /usr/local/app/target/mohh-discord-bot-*.jar /mohh-discord-bot.jar

# Set entrypoint
ENTRYPOINT ["java", "-jar", "/mohh-discord-bot.jar"]
