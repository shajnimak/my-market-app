package ru.yandex.practicum.mymarket.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.AbstractIntegrationTest;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;

/**
 * Настоящий вход по логину и паролю из базы данных (BCrypt), сессия, выход и регистрация — через HTTP,
 * без подстановки аутентификации. Тест ведёт себя как браузер: хранит куку {@code SESSION} и обновляет её
 * по {@code Set-Cookie} (Spring Security меняет id сессии при входе и при выпуске CSRF-токена).
 * CSRF-токен подставляет {@code csrf()}: сама проверка CSRF покрыта тестами веб-слоя.
 */
class AuthenticationIntegrationTest extends AbstractIntegrationTest {

    private Browser browser;

    @BeforeEach
    void openBrowser() {
        browser = new Browser(anonymousClient.mutateWith(csrf()));
    }

    @Test
    void login_shouldAuthenticateUserFromDatabaseAndOpenPrivatePages() {
        browser.login("alice", PASSWORD);

        assertThat(browser.getHtml("/items")).contains("alice", "action=\"/logout\"").doesNotContain("href=\"/login\"");
        assertThat(browser.getHtml("/cart/items")).contains("href=\"/orders\"");
        assertThat(browser.getHtml("/orders")).contains("alice");
    }

    @Test
    void login_shouldChangeSessionId_toPreventSessionFixation() {
        browser.getHtml("/login");
        String anonymousSession = browser.session;

        browser.login("alice", PASSWORD);

        assertThat(anonymousSession).isNotNull();
        assertThat(browser.session).isNotNull().isNotEqualTo(anonymousSession);
    }

    @Test
    void login_shouldFail_withWrongPassword() {
        EntityExchangeResult<String> result = browser.post("/login", form("username", "alice", "password", "wrong"));

        assertThat(result.getStatus().is3xxRedirection()).isTrue();
        assertThat(result.getResponseHeaders().getLocation()).isEqualTo(URI.create("/login?error"));
        assertThat(browser.get("/cart/items").getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anonymousVisitor_shouldSeeOnlyCatalogAndItemPage() {
        assertThat(browser.getHtml("/items")).contains("href=\"/login\"").doesNotContain("href=\"/cart/items\"");

        for (String uri : new String[]{"/cart/items", "/orders", "/orders/1", "/admin/items/new"}) {
            assertThat(browser.get(uri).getStatus()).as(uri).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        assertThat(browser.post("/buy", form()).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logout_shouldInvalidateSessionAndExpireCookies() {
        browser.login("alice", PASSWORD);
        browser.getHtml("/cart/items");
        String authenticatedSession = browser.session;

        EntityExchangeResult<String> logout = browser.post("/logout", form());

        assertThat(logout.getStatus().is3xxRedirection()).isTrue();
        assertThat(logout.getResponseHeaders().getLocation()).isEqualTo(URI.create("/items?logout"));
        ResponseCookie expired = logout.getResponseCookies().getFirst("SESSION");
        assertThat(expired).isNotNull();
        assertThat(expired.getMaxAge()).isZero();
        assertThat(browser.session).isNull();
        anonymousClient.get().uri("/cart/items")
                .cookie("SESSION", authenticatedSession)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void registration_shouldStoreHashedPasswordAndAllowLogin() {
        EntityExchangeResult<String> result = browser.post("/register",
                form("username", "carol", "password", "carol-secret", "confirmPassword", "carol-secret"));

        assertThat(result.getStatus()).isEqualTo(HttpStatus.SEE_OTHER);
        assertThat(result.getResponseHeaders().getLocation()).isEqualTo(URI.create("/login?registered"));
        StepVerifier.create(userRepository.findByUsername("carol"))
                .assertNext(user -> assertThat(user.getPassword()).startsWith("{bcrypt}").doesNotContain("carol-secret"))
                .verifyComplete();
        browser.login("carol", "carol-secret");
        assertThat(browser.getHtml("/orders")).contains("carol");
    }

    @Test
    void registration_shouldRejectTakenUsername() {
        EntityExchangeResult<String> result = browser.post("/register",
                form("username", "alice", "password", "another-secret", "confirmPassword", "another-secret"));

        assertThat(result.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(result.getResponseBody()).contains("Логин alice уже занят");
    }

    private static MultiValueMap<String, String> form(String... keysAndValues) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            form.add(keysAndValues[i], keysAndValues[i + 1]);
        }
        return form;
    }

    /**
     * Минимальный «браузер»: отправляет куку сессии и запоминает новую из ответа.
     */
    private static final class Browser {

        private final WebTestClient client;
        private String session;

        private Browser(WebTestClient client) {
            this.client = client;
        }

        void login(String username, String password) {
            EntityExchangeResult<String> result = post("/login", form("username", username, "password", password));
            assertThat(result.getStatus().is3xxRedirection()).isTrue();
            assertThat(result.getResponseHeaders().getLocation()).isEqualTo(URI.create("/items"));
        }

        String getHtml(String uri) {
            EntityExchangeResult<String> result = get(uri);
            assertThat(result.getStatus()).as(uri).isEqualTo(HttpStatus.OK);
            return result.getResponseBody();
        }

        EntityExchangeResult<String> get(String uri) {
            WebTestClient.RequestHeadersSpec<?> request = client.get().uri(uri);
            if (session != null) {
                request = request.cookie("SESSION", session);
            }
            return remember(request.exchange());
        }

        EntityExchangeResult<String> post(String uri, MultiValueMap<String, String> form) {
            WebTestClient.RequestBodySpec request = client.post().uri(uri);
            if (session != null) {
                request = request.cookie("SESSION", session);
            }
            return remember(request.body(BodyInserters.fromFormData(form)).exchange());
        }

        private EntityExchangeResult<String> remember(WebTestClient.ResponseSpec response) {
            EntityExchangeResult<String> result = response.expectBody(String.class).returnResult();
            ResponseCookie cookie = result.getResponseCookies().getFirst("SESSION");
            if (cookie != null) {
                session = cookie.getMaxAge().isZero() ? null : cookie.getValue();
            }
            return result;
        }
    }
}
