# Образ собирается из корня мультипроекта: docker build -f market-app/Dockerfile .
# Сборка executable JAR. Клиент сервиса платежей генерируется из общей спецификации openapi/payment-api.yaml.
# Тесты витрины здесь не запускаются: интеграционным тестам нужен Redis в Testcontainers,
# а внутри docker build нет доступа к Docker. Тесты выполняются командой ./mvnw verify и в CI перед сборкой образа.
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY openapi openapi
COPY market-app/pom.xml market-app/
COPY payment-service/pom.xml payment-service/
RUN mvn -B -q -pl market-app -am dependency:go-offline
COPY market-app/src market-app/src
RUN mvn -B -pl market-app -am package -DskipTests

# Запуск приложения во встроенном Netty
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 market
COPY --from=build /workspace/market-app/target/market-app.jar app.jar
USER market
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
