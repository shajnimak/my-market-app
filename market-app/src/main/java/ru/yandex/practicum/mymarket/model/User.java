package ru.yandex.practicum.mymarket.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Покупатель. Пароль хранится только в виде хеша с префиксом алгоритма ({@code {bcrypt}...}),
 * который проверяет {@code DelegatingPasswordEncoder} Spring Security.
 */
@Table("users")
public class User {

    @Id
    private Long id;

    private String username;

    private String password;

    protected User() {
    }

    public User(String username, String encodedPassword) {
        this.username = username;
        this.password = encodedPassword;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
