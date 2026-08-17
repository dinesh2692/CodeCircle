FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY backend/pom.xml ./pom.xml
RUN mvn -q -DskipTests dependency:go-offline
COPY backend/src ./src
RUN mvn -q -DskipTests clean package

FROM eclipse-temurin:21-jdk
WORKDIR /app

RUN useradd -r -u 10001 appuser
COPY --from=build /app/target/codecircle-1.0.0.jar app.jar
COPY public ./public
RUN chown -R appuser:appuser /app

USER appuser
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
