package ru.yandex.practicum.mymarket.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.model.ItemImage;

import static org.assertj.core.api.Assertions.assertThat;

class ItemImageRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private ItemImageRepository itemImageRepository;

    private long itemId;

    @BeforeEach
    void setUp() {
        itemId = itemRepository.save(new Item("Мяч", "Футбольный", 2500)).block().getId();
    }

    @Test
    void save_shouldInsertImageWithItemIdAsKey() {
        StepVerifier.create(itemImageRepository.save(ItemImage.newImage(itemId, "image/png", new byte[]{1, 2, 3}))
                        .then(itemImageRepository.findById(itemId)))
                .assertNext(image -> {
                    assertThat(image.getItemId()).isEqualTo(itemId);
                    assertThat(image.getContentType()).isEqualTo("image/png");
                    assertThat(image.getContent()).containsExactly(1, 2, 3);
                    assertThat(image.isNew()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void deleteItem_shouldCascadeToImage() {
        StepVerifier.create(itemImageRepository.save(ItemImage.newImage(itemId, "image/png", new byte[]{1}))
                        .then(itemRepository.deleteById(itemId))
                        .then(itemImageRepository.existsById(itemId)))
                .expectNext(false)
                .verifyComplete();
    }
}
