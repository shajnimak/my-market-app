package ru.yandex.practicum.mymarket.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.io.ResourceLoader;
import ru.yandex.practicum.mymarket.AbstractIntegrationTest;
import ru.yandex.practicum.mymarket.config.DemoCatalogLoader;
import ru.yandex.practicum.mymarket.dto.ImageData;
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
    void run_shouldLoadDemoItemsWithImages_onlyIntoEmptyCatalog() throws Exception {
        DemoCatalogLoader loader = new DemoCatalogLoader(itemService, objectMapper, resourceLoader);

        loader.run(new DefaultApplicationArguments());
        long loadedCount = itemRepository.count();
        loader.run(new DefaultApplicationArguments());

        assertThat(loadedCount).isPositive();
        assertThat(itemRepository.count()).isEqualTo(loadedCount);

        long firstItemId = itemService.findItems("", ItemSort.NO, 1, 1).getContent().getFirst().getId();
        ImageData image = itemService.getImage(firstItemId);
        assertThat(image.contentType()).isEqualTo("image/svg+xml");
        assertThat(image.content()).isNotEmpty();
    }
}
