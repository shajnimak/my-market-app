# Образ собирается из корня мультипроекта: docker build -f market-app/Dockerfile .
# Сборка executable JAR. Тесты выполняются прямо здесь: база H2 работает в памяти,
# внешние зависимости не нужны, а образ с упавшими тестами не соберётся.
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY market-app/pom.xml market-app/
COPY payment-service/pom.xml payment-service/
RUN mvn -B -q -pl market-app -am dependency:go-offline
COPY market-app/src market-app/src
RUN mvn -B -pl market-app -am package

# Запуск приложения во встроенном Netty
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 market
COPY --from=build /workspace/market-app/target/market-app.jar app.jar
USER market
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
