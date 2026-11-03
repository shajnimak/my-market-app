package ru.yandex.practicum.mymarket.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.AbstractIntegrationTest;
import ru.yandex.practicum.mymarket.config.DemoCatalogLoader;
import ru.yandex.practicum.mymarket.model.ItemSort;
import ru.yandex.practicum.mymarket.repository.ItemRepository;
import ru.yandex.practicum.mymarket.service.ItemService;

import static org.assertj.core.api.Assertions.assertThat;

class DemoCatalogLoaderIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ItemService itemService;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ResourceLoader resourceLoader;

    @Test
    void load_shouldLoadDemoItemsWithImages_onlyIntoEmptyCatalog() {
        DemoCatalogLoader loader = new DemoCatalogLoader(itemService, objectMapper, resourceLoader);

        StepVerifier.create(loader.load())
                .assertNext(count -> assertThat(count).isPositive())
                .verifyComplete();
        long loadedCount = itemRepository.count().block();
        StepVerifier.create(loader.load())
                .expectNext(0L)
                .verifyComplete();
        StepVerifier.create(itemRepository.count())
                .expectNext(loadedCount)
                .verifyComplete();

        StepVerifier.create(itemService.findItems("", ItemSort.NO, 1, 1)
                        .flatMap(page -> itemService.getImage(page.items().getFirst().id())))
                .assertNext(image -> {
                    assertThat(image.contentType()).isEqualTo("image/svg+xml");
                    assertThat(image.content()).isNotEmpty();
                })
                .verifyComplete();
    }
}
