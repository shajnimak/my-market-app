package ru.yandex.practicum.mymarket.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.yandex.practicum.mymarket.exception.UsernameTakenException;
import ru.yandex.practicum.mymarket.model.User;
import ru.yandex.practicum.mymarket.repository.UserRepository;

/**
 * Регистрация покупателей. Пароль сохраняется только в виде хеша {@link PasswordEncoder} (BCrypt).
 * BCrypt намеренно медленный, поэтому хеш считается на {@code boundedElastic}, а не в потоке Netty —
 * так же, как Spring Security проверяет пароль при входе.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Создаёт покупателя.
     *
     * @return id нового покупателя или {@link UsernameTakenException}, если логин занят
     * (в том числе если его заняли параллельно — сработает уникальный индекс)
     */
    public Mono<Long> register(String username, String rawPassword) {
        return userRepository.existsByUsername(username)
                .flatMap(exists -> exists
                        ? Mono.error(new UsernameTakenException(username))
                        : Mono.fromCallable(() -> passwordEncoder.encode(rawPassword))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(hash -> userRepository.save(new User(username, hash))))
                .onErrorMap(DataIntegrityViolationException.class, e -> new UsernameTakenException(username))
                .map(User::getId);
    }
}
