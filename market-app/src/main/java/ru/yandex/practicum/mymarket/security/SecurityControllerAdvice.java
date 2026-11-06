package ru.yandex.practicum.mymarket.security;

import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.reactive.result.view.CsrfRequestDataValueProcessor;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Данные Spring Security для шаблонов Thymeleaf в WebFlux.
 * <ul>
 *     <li>CSRF-токен: в WebFlux он создаётся лениво ({@code Mono<CsrfToken>} в атрибутах запроса).
 *     Здесь он разрешается и кладётся в атрибут, из которого {@link CsrfRequestDataValueProcessor}
 *     берёт его для скрытого поля {@code _csrf} в формах с {@code th:action}.</li>
 *     <li>Контекст безопасности под именем {@code thymeleafSpringSecurityContext}: оттуда диалект
 *     {@code sec:} берёт аутентификацию для {@code sec:authorize} и {@code sec:authentication}.</li>
 * </ul>
 */
@ControllerAdvice
public class SecurityControllerAdvice {

    static final String SECURITY_CONTEXT_MODEL_ATTRIBUTE = "thymeleafSpringSecurityContext";

    @ModelAttribute("csrfToken")
    public Mono<CsrfToken> csrfToken(ServerWebExchange exchange) {
        Mono<CsrfToken> csrfToken = exchange.getAttribute(CsrfToken.class.getName());
        if (csrfToken == null) {
            return Mono.empty();
        }
        return csrfToken.doOnSuccess(token -> exchange.getAttributes()
                .put(CsrfRequestDataValueProcessor.DEFAULT_CSRF_ATTR_NAME, token));
    }

    @ModelAttribute(SECURITY_CONTEXT_MODEL_ATTRIBUTE)
    public Mono<SecurityContext> securityContext() {
        return ReactiveSecurityContextHolder.getContext();
    }
}
