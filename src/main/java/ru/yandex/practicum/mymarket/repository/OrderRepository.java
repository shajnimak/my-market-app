package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.yandex.practicum.mymarket.model.Order;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = "items")
    List<Order> findAllByOrderByIdDesc();

    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(Long id);
}
