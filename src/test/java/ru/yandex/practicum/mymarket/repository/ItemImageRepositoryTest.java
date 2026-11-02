package ru.yandex.practicum.mymarket.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import ru.yandex.practicum.mymarket.model.Item;
import ru.yandex.practicum.mymarket.model.ItemImage;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ItemImageRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ItemImageRepository itemImageRepository;

    @Test
    void save_shouldStoreImageContentByItemId() {
        Item item = entityManager.persistAndFlush(new Item("Мяч", "Описание", 100));
        byte[] content = {1, 2, 3};

        itemImageRepository.saveAndFlush(new ItemImage(item.getId(), "image/png", content));
        entityManager.clear();

        Optional<ItemImage> image = itemImageRepository.findById(item.getId());
        assertThat(image).isPresent();
        assertThat(image.get().getContentType()).isEqualTo("image/png");
        assertThat(image.get().getContent()).containsExactly(1, 2, 3);
    }
}
