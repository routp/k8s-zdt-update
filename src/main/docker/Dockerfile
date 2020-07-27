FROM frolvlad/alpine-java:jre8.202.08-slim

RUN mkdir /app

COPY ${project.artifactId}.jar /app
COPY libs /app/libs

WORKDIR /app

EXPOSE 9015

ENTRYPOINT ["java", "-jar", "/app/${project.artifactId}.jar"]