FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY backend/pom.xml backend/pom.xml
RUN mvn -f backend/pom.xml -DskipTests dependency:go-offline

COPY backend backend
RUN mvn -f backend/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN useradd --create-home --shell /usr/sbin/nologin stratus

COPY --from=build /workspace/backend/target/backend-0.1.0-SNAPSHOT.jar app.jar

USER stratus
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
