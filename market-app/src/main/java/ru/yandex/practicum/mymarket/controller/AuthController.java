package ru.yandex.practicum.mymarket.controller;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.result.view.Rendering;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.RegistrationForm;
import ru.yandex.practicum.mymarket.exception.UsernameTakenException;
import ru.yandex.practicum.mymarket.security.MarketUserDetails;
import ru.yandex.practicum.mymarket.service.UserService;

import java.util.Objects;

/**
 * Страницы входа и регистрации. Саму проверку логина и пароля ({@code POST /login}) и выход
 * ({@code POST /logout}) выполняют фильтры Spring Security, см. {@code SecurityConfig}.
 */
@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public Mono<Rendering> getLoginPage(@RequestParam MultiValueMap<String, String> params,
                                        @AuthenticationPrincipal MarketUserDetails user) {
        if (user != null) {
            return Mono.just(Rendering.redirectTo("/items").build());
        }
        return Mono.just(Rendering.view("login")
                .modelAttribute("loginError", params.containsKey("error"))
                .modelAttribute("registered", params.containsKey("registered"))
                .build());
    }

    @GetMapping("/register")
    public Mono<String> getRegistrationForm(@ModelAttribute("form") RegistrationForm form) {
        return Mono.just("register");
    }

    /**
     * После регистрации покупатель попадает на страницу входа: пароль нужно ввести ещё раз,
     * чтобы вход всегда проходил через одну и ту же проверку Spring Security.
     */
    @PostMapping("/register")
    public Mono<String> register(@Valid @ModelAttribute("form") RegistrationForm form, BindingResult bindingResult) {
        if (!bindingResult.hasFieldErrors("password")
                && !Objects.equals(form.getPassword(), form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch", "Пароли не совпадают");
        }
        if (bindingResult.hasErrors()) {
            return Mono.just("register");
        }
        return userService.register(form.getUsername(), form.getPassword())
                .thenReturn("redirect:/login?registered")
                .onErrorResume(UsernameTakenException.class, e -> {
                    bindingResult.rejectValue("username", "username.taken", e.getMessage());
                    return Mono.just("register");
                });
    }
}
