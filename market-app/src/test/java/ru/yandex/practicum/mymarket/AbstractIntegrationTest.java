package ru.yandex.practicum.mymarket;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.yandex.practicum.mymarket.model.User;
import ru.yandex.practicum.mymarket.repository.UserRepository;
import ru.yandex.practicum.mymarket.security.MarketUserDetails;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockAuthentication;

/**
 * Базовый класс интеграционных тестов. Конфигурация одинакова для всех наследников,
 * поэтому контекст приложения (вместе с контейнером Redis) поднимается один раз и берётся из кеша.
 * <p>
 * Сервер авторизации и сервис платежей заменены HTTP-заглушками {@link AuthorizationServerStub}
 * и {@link PaymentServiceStub}: они одни на все тесты, так что их адреса в свойствах одинаковы
 * и не мешают кешированию контекста.
 * <p>
 * Перед каждым тестом очищаются таблицы и кеш (реактивные транзакции не откатываются тестовым фреймворком),
 * заглушки возвращаются в исходное состояние, а сохранённый витриной access-токен удаляется — каждый тест
 * начинает без токена. В базе создаются покупатели {@code alice} и {@code bob} с паролем {@value #PASSWORD}.
 * {@link #webTestClient} работает от имени {@code alice} (аутентификация подставляется
 * {@code mockAuthentication}, CSRF-токен — {@code csrf()}); настоящий вход по логину и паролю
 * проверяет {@code AuthenticationIntegrationTest}.
 */
@SpringBootTest(properties = "market.demo-data.enabled=false")
@AutoConfigureWebTestClient
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    protected static final String PASSWORD = "password";
    private static final String PASSWORD_HASH = "{bcrypt}" + new BCryptPasswordEncoder().encode(PASSWORD);

    protected static final AuthorizationServerStub authorizationServerStub = new AuthorizationServerStub();
    protected static final PaymentServiceStub paymentServiceStub = new PaymentServiceStub(authorizationServerStub);

    @DynamicPropertySource
    static void stubProperties(DynamicPropertyRegistry registry) {
        registry.add("market.payment.url", paymentServiceStub::url);
        registry.add("spring.security.oauth2.client.provider.auth-server.token-uri", authorizationServerStub::tokenUri);
    }

    /**
     * Клиент без аутентификации — анонимный посетитель.
     */
    @Autowired
    protected WebTestClient anonymousClient;

    @Autowired
    protected DatabaseClient databaseClient;

    @Autowired
    protected ReactiveStringRedisTemplate redisTemplate;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    private ReactiveOAuth2AuthorizedClientService authorizedClientService;

    protected MarketUserDetails alice;
    protected MarketUserDetails bob;

    /**
     * Клиент вошедшего покупателя {@code alice}.
     */
    protected WebTestClient webTestClient;

    @BeforeEach
    void prepareEnvironment() {
        TestDatabase.clean(databaseClient).block();
        redisTemplate.execute(connection -> connection.serverCommands().flushDb()).blockLast();
        authorizationServerStub.reset();
        paymentServiceStub.reset();
        authorizedClientService.removeAuthorizedClient("payment-service", "payment-service").block();
        alice = createUser("alice");
        bob = createUser("bob");
        webTestClient = clientOf(alice);
    }

    protected MarketUserDetails createUser(String username) {
        User user = userRepository.save(new User(username, PASSWORD_HASH)).block();
        return MarketUserDetails.from(user);
    }

    protected WebTestClient clientOf(MarketUserDetails user) {
        return anonymousClient
                .mutateWith(mockAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities())))
                .mutateWith(csrf());
    }
}
