package ru.yandex.practicum.mymarket.security;

import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.logout.ServerLogoutHandler;
import reactor.core.publisher.Mono;

/**
 * При выходе удаляет в браузере все куки, которые он прислал (сессия {@code SESSION} и любые другие),
 * выставляя им {@code Max-Age=0}. Заголовок {@code Clear-Site-Data} делает то же самое,
 * но браузеры учитывают его только по HTTPS, а это работает и по HTTP.
 */
public class ExpireCookiesServerLogoutHandler implements ServerLogoutHandler {

    @Override
    public Mono<Void> logout(WebFilterExchange exchange, Authentication authentication) {
        ServerHttpResponse response = exchange.getExchange().getResponse();
        exchange.getExchange().getRequest().getCookies().keySet().forEach(name -> response.addCookie(
                ResponseCookie.from(name, "")
                        .path("/")
                        .maxAge(0)
                        .httpOnly(true)
                        .sameSite("Lax")
                        .build()));
        return Mono.empty();
    }
}
