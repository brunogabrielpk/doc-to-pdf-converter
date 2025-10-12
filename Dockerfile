# Use an official Clojure base image with Java
# FROM clojure:temurin-21-tools-deps-alpine
FROM docker.io/library/clojure:temurin-21-tools-deps-alpine

# Install LibreOffice (required for document conversion)
# Alpine package manager (apk) installs LibreOffice and its dependencies
RUN apk update && \
    apk add --no-cache \
    libreoffice \
    libreoffice-writer \
    ttf-dejavu \
    && rm -rf /var/cache/apk/*

# Set working directory
WORKDIR /app

# Copy dependency files first (for better Docker layer caching)
# This way dependencies are only re-downloaded when deps.edn changes
COPY deps.edn /app/

# Download dependencies
RUN clojure -P

# Copy the application source code
COPY src /app/src
COPY resources /app/resources

# Create uploads directory
RUN mkdir -p /app/uploads

# Expose port 3000
EXPOSE 3000

# Set environment variable for port
ENV PORT=3000

# Run the application
CMD ["clojure", "-M", "-m", "doc-converter.core"]
