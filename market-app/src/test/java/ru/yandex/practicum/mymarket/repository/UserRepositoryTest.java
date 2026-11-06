package ru.yandex.practicum.mymarket.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.model.User;

import static org.assertj.core.api.Assertions.assertThat;

class UserRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.save(new User("alice", "{bcrypt}$2a$10$hash")).block();
    }

    @Test
    void findByUsername_shouldReturnUserWithPasswordHash() {
        StepVerifier.create(userRepository.findByUsername("alice"))
                .assertNext(user -> {
                    assertThat(user.getId()).isNotNull();
                    assertThat(user.getPassword()).isEqualTo("{bcrypt}$2a$10$hash");
                })
                .verifyComplete();
        StepVerifier.create(userRepository.findByUsername("bob"))
                .verifyComplete();
    }

    @Test
    void existsByUsername_shouldCheckLogin() {
        StepVerifier.create(userRepository.existsByUsername("alice")).expectNext(true).verifyComplete();
        StepVerifier.create(userRepository.existsByUsername("bob")).expectNext(false).verifyComplete();
    }

    @Test
    void save_shouldRejectDuplicateUsername() {
        StepVerifier.create(userRepository.save(new User("alice", "{noop}other")))
                .expectError(DataIntegrityViolationException.class)
                .verify();
    }
}
