package ru.yandex.practicum.mymarket.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.exception.UsernameTakenException;
import ru.yandex.practicum.mymarket.security.MarketUserDetails;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;

/**
 * Вход, выход и регистрация на уровне веб-слоя: фильтры Spring Security из {@code SecurityConfig}
 * с замоканными {@code MarketUserDetailsService} и {@code UserService}.
 */
class AuthControllerTest extends AbstractControllerTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void loginPage_shouldRenderFormWithCsrfToken() {
        String html = getHtml(anonymousClient, "/login");

        assertThat(html).contains("action=\"/login\"", "name=\"username\"", "name=\"password\"", "name=\"_csrf\"")
                .doesNotContain("Неверный логин или пароль");
    }

    @Test
    void loginPage_shouldShowError_afterFailedLogin() {
        assertThat(getHtml(anonymousClient, "/login?error")).contains("Неверный логин или пароль");
    }

    @Test
    void loginPage_shouldRedirectAuthenticatedUserToCatalog() {
        webTestClient.get().uri("/login")
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().location("/items");
    }

    @Test
    void login_shouldAuthenticateWithEncodedPasswordAndRedirectToCatalog() {
        when(userDetailsService.findByUsername("alice"))
                .thenReturn(Mono.just(new MarketUserDetails(1, "alice", passwordEncoder.encode("secret"))));

        login("alice", "secret")
                .expectStatus().is3xxRedirection()
                .expectHeader().location("/items")
                .expectCookie().exists("SESSION");
    }

    @Test
    void login_shouldRedirectBackWithError_whenPasswordWrong() {
        when(userDetailsService.findByUsername("alice"))
                .thenReturn(Mono.just(new MarketUserDetails(1, "alice", passwordEncoder.encode("secret"))));

        login("alice", "wrong")
                .expectStatus().is3xxRedirection()
                .expectHeader().location("/login?error");
    }

    @Test
    void login_shouldRedirectBackWithError_whenUserUnknown() {
        when(userDetailsService.findByUsername("nobody")).thenReturn(Mono.empty());

        login("nobody", "secret")
                .expectStatus().is3xxRedirection()
                .expectHeader().location("/login?error");
    }

    @Test
    void login_shouldBeForbidden_withoutCsrfToken() {
        anonymousClient.post().uri("/login")
                .body(BodyInserters.fromFormData("username", "alice").with("password", "secret"))
                .exchange()
                .expectStatus().isForbidden();

        verify(userDetailsService, never()).findByUsername(anyString());
    }

    @Test
    void logout_shouldExpireAllCookiesAndRedirectToCatalog() {
        List<ResponseCookie> cookies = webTestClient.post().uri("/logout")
                .cookie("SESSION", "session-id")
                .cookie("theme", "dark")
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().location("/items?logout")
                .returnResult(Void.class)
                .getResponseCookies()
                .values().stream().flatMap(List::stream).toList();

        assertThat(cookies).extracting(ResponseCookie::getName).contains("SESSION", "theme");
        assertThat(cookies).allSatisfy(cookie -> {
            assertThat(cookie.getMaxAge()).isZero();
            assertThat(cookie.getValue()).isEmpty();
        });
    }

    @Test
    void registrationPage_shouldRenderForm() {
        assertThat(getHtml(anonymousClient, "/register"))
                .contains("action=\"/register\"", "name=\"username\"", "name=\"confirmPassword\"", "name=\"_csrf\"");
    }

    @Test
    void register_shouldCreateUserAndRedirectToLogin() {
        when(userService.register("carol", "secret1")).thenReturn(Mono.just(3L));

        register("carol", "secret1", "secret1")
                .expectStatus().isSeeOther()
                .expectHeader().location("/login?registered");

        verify(userService).register("carol", "secret1");
    }

    @Test
    void register_shouldShowErrors_whenFormInvalid() {
        String html = register("c!", "123", "321")
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(occurrences(html, "is-invalid")).isEqualTo(2);
        verify(userService, never()).register(anyString(), anyString());
    }

    @Test
    void register_shouldShowError_whenPasswordsDiffer() {
        String html = register("carol", "secret1", "secret2")
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(html).contains("Пароли не совпадают");
        verify(userService, never()).register(anyString(), anyString());
    }

    @Test
    void register_shouldShowError_whenUsernameTaken() {
        when(userService.register("alice", "secret1")).thenReturn(Mono.error(new UsernameTakenException("alice")));

        String html = register("alice", "secret1", "secret1")
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(html).contains("Логин alice уже занят");
    }

    private ResponseSpec login(String username, String password) {
        return anonymousClient.mutateWith(csrf()).post().uri("/login")
                .body(BodyInserters.fromFormData("username", username).with("password", password))
                .exchange();
    }

    private ResponseSpec register(String username, String password, String confirmPassword) {
        return anonymousClient.mutateWith(csrf()).post().uri("/register")
                .body(BodyInserters.fromFormData("username", username)
                        .with("password", password)
                        .with("confirmPassword", confirmPassword))
                .exchange();
    }
}
