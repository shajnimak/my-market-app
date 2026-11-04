# Образ собирается из корня мультипроекта: docker build -f payment-service/Dockerfile .
# Сборка executable JAR вместе с тестами: внешних зависимостей у сервиса нет,
# серверные интерфейсы генерируются из общей спецификации openapi/payment-api.yaml.
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY openapi openapi
COPY market-app/pom.xml market-app/
COPY payment-service/pom.xml payment-service/
RUN mvn -B -q -pl payment-service -am dependency:go-offline
COPY payment-service/src payment-service/src
RUN mvn -B -pl payment-service -am package

# Запуск сервиса во встроенном Netty
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 payment
COPY --from=build /workspace/payment-service/target/payment-service.jar app.jar
USER payment
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
