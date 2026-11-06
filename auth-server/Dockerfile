# Образ собирается из корня мультипроекта: docker build -f auth-server/Dockerfile .
# Сборка executable JAR вместе с тестами: внешних зависимостей у сервера авторизации нет.
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY openapi openapi
COPY auth-server/pom.xml auth-server/
COPY market-app/pom.xml market-app/
COPY payment-service/pom.xml payment-service/
RUN mvn -B -q -pl auth-server -am dependency:go-offline
COPY auth-server/src auth-server/src
RUN mvn -B -pl auth-server -am package

# Запуск во встроенном Tomcat
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 auth
COPY --from=build /workspace/auth-server/target/auth-server.jar app.jar
USER auth
EXPOSE 9000
ENTRYPOINT ["java", "-jar", "app.jar"]
