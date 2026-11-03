package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.yandex.practicum.mymarket.model.ItemImage;

public interface ItemImageRepository extends JpaRepository<ItemImage, Long> {
}
