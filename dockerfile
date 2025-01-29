FROM ubuntu:24.04

#Update Packages
RUN DEBIAN_FRONTEND=noninteractive
RUN apt update
RUN apt install -y curl
RUN curl -s https://packagecloud.io/install/repositories/github/git-lfs/script.deb.sh | bash
RUN apt install -y maven openjdk-17-jdk git-lfs
RUN JACOCO_VERSION=0.8.12
RUN mvn dependency:get -Dartifact=org.jacoco:jacoco-maven-plugin:0.8.12

# Set Java Build Vars 
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV MAVEN_OPTS=-Xmx2048m

# Setup Dataset Folders
RUN mkdir neo4j
RUN mkdir datasets
RUN mkdir datasets/public
RUN mkdir datasets/internal

# Download Datasets
RUN ssh-keyscan -t rsa github.com >> ~/.ssh/known_hosts
RUN git clone https://github.com/neo4j-graph-examples/pole.git /datasets/public/pole
RUN git clone https://github.com/neo4j-graph-examples/recommendations.git /datasets/public/recommendations
COPY ./datasets/internal /datasets/internal

WORKDIR /neo4j

# (Optional) Expose the default Neo4j port
EXPOSE 7474 7687

COPY . .

RUN mvn clean install -DskipTests -T1C
RUN tar -xvf ./packaging/standalone/target/neo4j-community-5.20.0-SNAPSHOT-unix.tar.gz

ENTRYPOINT [ "bash" ]