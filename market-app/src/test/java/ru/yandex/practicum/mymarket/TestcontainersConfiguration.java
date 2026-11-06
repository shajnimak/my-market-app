package ru.yandex.practicum.mymarket;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Redis для интеграционных тестов в Docker-контейнере. {@link ServiceConnection} передаёт адрес контейнера
 * в {@code spring.data.redis.*}. Контейнер — бин контекста, поэтому живёт, пока контекст лежит в кеше,
 * и запускается один раз для всех тестов с одинаковой конфигурацией.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection(name = "redis")
    public GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
    }
}
