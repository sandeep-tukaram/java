# Stage 1: Create a minimal JRE using jlink
FROM eclipse-temurin:21-jdk-alpine AS builder

# Create a minimal JRE
RUN jlink \
    --add-modules java.base \           
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=2 \
    --no-man-pages \
    --output /customjre

# Stage 2: Create the final image
FROM alpine:3.19

# Copy the custom JRE
COPY --from=builder /customjre /opt/java

# Set up Java environment
ENV JAVA_HOME=/opt/java
ENV PATH="${JAVA_HOME}/bin:${PATH}"

WORKDIR /app

# Copy the application
COPY hello.jar /app/

# Create a non-root user
RUN addgroup -S javauser && adduser -S -G javauser javauser && \
    chown -R javauser:javauser /app /opt/java

# Switch to non-root user
USER javauser

# Set the entry point
ENTRYPOINT ["java", "-jar", "hello.jar"]

# Label the image with metadata
LABEL maintainer="developer"
LABEL version="1.0"
LABEL description="Docker image for hello1 Java application"
