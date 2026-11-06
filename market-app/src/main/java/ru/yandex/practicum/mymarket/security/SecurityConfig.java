package ru.yandex.practicum.mymarket.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationFailureHandler;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.logout.DelegatingServerLogoutHandler;
import org.springframework.security.web.server.authentication.logout.HeaderWriterServerLogoutHandler;
import org.springframework.security.web.server.authentication.logout.RedirectServerLogoutSuccessHandler;
import org.springframework.security.web.server.authentication.logout.SecurityContextServerLogoutHandler;
import org.springframework.security.web.server.authentication.logout.ServerLogoutHandler;
import org.springframework.security.web.server.authentication.logout.WebSessionServerLogoutHandler;
import org.springframework.security.web.server.header.ClearSiteDataServerHttpHeadersWriter;
import org.springframework.security.web.server.header.ClearSiteDataServerHttpHeadersWriter.Directive;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * Безопасность витрины: вход покупателей по логину и паролю из базы данных.
 * <ul>
 *     <li>Посетитель без входа получает {@code AnonymousAuthenticationToken}: так шаблоны различают
 *     анонимного посетителя ({@code sec:authorize="isAnonymous()"}) и покупателя.</li>
 *     <li>Анонимному посетителю доступны только витрина, карточка товара, картинки, вход и регистрация.
 *     Корзина, заказы, покупка, изменение корзины и добавление товаров требуют входа:
 *     анонимный запрос к ним получает страницу ошибки 401 со ссылкой на вход.</li>
 *     <li>Форма входа — своя страница {@code /login}; логин и пароль проверяет
 *     {@code UserDetailsRepositoryReactiveAuthenticationManager} с {@link MarketUserDetailsService}
 *     и {@link PasswordEncoder} (BCrypt). Контекст безопасности хранится в {@code WebSession}.</li>
 *     <li>Выход — {@code POST /logout}: очищается контекст безопасности, сессия инвалидируется,
 *     все куки браузера удаляются (и по HTTPS дополнительно отправляется {@code Clear-Site-Data}).</li>
 *     <li>CSRF-защита включена: формы в шаблонах получают скрытое поле {@code _csrf}
 *     ({@link SecurityControllerAdvice}).</li>
 * </ul>
 * Кроме правил по URL, контроллеры приватных страниц помечены {@code @PreAuthorize("isAuthenticated()")}
 * ({@link EnableReactiveMethodSecurity}): ограничение действует, даже если правило по URL забудут обновить.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

    public static final String LOGIN_PAGE = "/login";
    static final String UNAUTHORIZED_MESSAGE = "Войдите в магазин, чтобы пользоваться корзиной и заказами";
    static final String FORBIDDEN_MESSAGE = "Доступ запрещён";

    /**
     * Хеши паролей в формате {@code {bcrypt}...}: по префиксу можно сменить алгоритм, не ломая старые пароли.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.GET, "/", "/items", "/items/{id}", "/images/{itemId}").permitAll()
                        .pathMatchers(LOGIN_PAGE, "/register").permitAll()
                        .pathMatchers(HttpMethod.GET, "/favicon.ico").permitAll()
                        .anyExchange().authenticated())
                .formLogin(formLogin -> formLogin
                        .loginPage(LOGIN_PAGE)
                        .authenticationSuccessHandler(new RedirectServerAuthenticationSuccessHandler("/items"))
                        .authenticationFailureHandler(new RedirectServerAuthenticationFailureHandler(LOGIN_PAGE + "?error")))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutHandler(logoutHandler())
                        .logoutSuccessHandler(logoutSuccessHandler()))
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint((exchange, e) ->
                                Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, UNAUTHORIZED_MESSAGE)))
                        .accessDeniedHandler((exchange, e) ->
                                Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, FORBIDDEN_MESSAGE))))
                .anonymous(Customizer.withDefaults())
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .build();
    }

    private static ServerLogoutHandler logoutHandler() {
        return new DelegatingServerLogoutHandler(
                new SecurityContextServerLogoutHandler(),
                new WebSessionServerLogoutHandler(),
                new ExpireCookiesServerLogoutHandler(),
                new HeaderWriterServerLogoutHandler(new ClearSiteDataServerHttpHeadersWriter(
                        Directive.CACHE, Directive.COOKIES, Directive.STORAGE)));
    }

    private static RedirectServerLogoutSuccessHandler logoutSuccessHandler() {
        RedirectServerLogoutSuccessHandler handler = new RedirectServerLogoutSuccessHandler();
        handler.setLogoutSuccessUrl(URI.create("/items?logout"));
        return handler;
    }
}
