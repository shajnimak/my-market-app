package ru.yandex.practicum.mymarket.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;

/**
 * Форма регистрации покупателя. Логин ограничен теми же символами, что и логин счёта в сервисе платежей
 * ({@code openapi/payment-api.yaml}): по нему витрина обращается к счёту покупателя.
 */
public class RegistrationForm {

    public static final String USERNAME_PATTERN = "^[A-Za-z0-9_.-]{3,50}$";

    @NotBlank
    @Pattern(regexp = USERNAME_PATTERN, message = "от 3 до 50 латинских букв, цифр и символов . _ -")
    private String username;

    @NotBlank
    @Size(min = 6, max = 72, message = "от 6 до 72 символов")
    private String password;

    private String confirmPassword;

    /**
     * BCrypt учитывает только первые 72 байта пароля, а кириллица в UTF-8 занимает по 2 байта на символ.
     */
    @AssertTrue(message = "пароль длиннее 72 байт")
    public boolean isPasswordFitsBcrypt() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
