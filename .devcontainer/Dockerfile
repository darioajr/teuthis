FROM eclipse-temurin:21
USER root

# Install Git and other essential tools
RUN apt-get update && apt-get install -y \
    git \
    curl \
    wget \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH=$JAVA_HOME/bin:$PATH
EXPOSE 8080
WORKDIR /workspaces