package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import ru.yandex.practicum.mymarket.model.ItemImage;

public interface ItemImageRepository extends R2dbcRepository<ItemImage, Long> {
}
