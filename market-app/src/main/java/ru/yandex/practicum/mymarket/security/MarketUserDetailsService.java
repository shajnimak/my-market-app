package ru.yandex.practicum.mymarket.security;

import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.repository.UserRepository;

/**
 * Загружает покупателя из таблицы {@code users} для входа по логину и паролю.
 * Пароль сравнивает {@code UserDetailsRepositoryReactiveAuthenticationManager} через {@code PasswordEncoder};
 * если логина нет, возвращается пустой {@link Mono} — Spring Security ответит той же ошибкой «неверные учётные данные»,
 * что и при неверном пароле.
 */
@Service
public class MarketUserDetailsService implements ReactiveUserDetailsService {

    private final UserRepository userRepository;

    public MarketUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Mono<UserDetails> findByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(MarketUserDetails::from);
    }
}
