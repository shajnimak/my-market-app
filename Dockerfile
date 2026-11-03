# Сборка executable JAR
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q package -DskipTests

# Запуск приложения во встроенном Netty
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 market
COPY --from=build /workspace/target/my-market-app.jar app.jar
USER market
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
