package ru.yandex.practicum.mymarket.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.exception.UsernameTakenException;
import ru.yandex.practicum.mymarket.model.User;
import ru.yandex.practicum.mymarket.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Mock
    private UserRepository userRepository;

    @Test
    void register_shouldSaveUserWithBcryptHashInsteadOfPassword() {
        when(userRepository.existsByUsername("carol")).thenReturn(Mono.just(false));
        when(userRepository.save(any())).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 3L);
            return Mono.just(user);
        });

        StepVerifier.create(userService().register("carol", "secret1"))
                .expectNext(3L)
                .verifyComplete();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("carol");
        assertThat(saved.getPassword()).startsWith("{bcrypt}$2").doesNotContain("secret1");
        assertThat(passwordEncoder.matches("secret1", saved.getPassword())).isTrue();
    }

    @Test
    void register_shouldFail_whenUsernameTaken() {
        when(userRepository.existsByUsername("alice")).thenReturn(Mono.just(true));

        StepVerifier.create(userService().register("alice", "secret1"))
                .expectError(UsernameTakenException.class)
                .verify();

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_shouldFail_whenSameUsernameRegisteredConcurrently() {
        when(userRepository.existsByUsername("carol")).thenReturn(Mono.just(false));
        when(userRepository.save(any())).thenReturn(Mono.error(new DuplicateKeyException("users.username")));

        StepVerifier.create(userService().register("carol", "secret1"))
                .expectError(UsernameTakenException.class)
                .verify();
    }

    private UserService userService() {
        return new UserService(userRepository, passwordEncoder);
    }
}
