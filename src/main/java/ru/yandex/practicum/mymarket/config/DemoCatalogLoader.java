package ru.yandex.practicum.mymarket.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.mymarket.dto.ImageData;
import ru.yandex.practicum.mymarket.dto.NewItem;
import ru.yandex.practicum.mymarket.service.ItemService;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Заполняет пустую витрину демонстрационными товарами из {@code classpath:demo/catalog.json}.
 */
@Component
@ConditionalOnProperty(name = "market.demo-data.enabled", havingValue = "true")
public class DemoCatalogLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoCatalogLoader.class);

    private static final String CATALOG_LOCATION = "classpath:demo/catalog.json";
    private static final String IMAGES_LOCATION = "classpath:demo/images/";

    private final ItemService itemService;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    public DemoCatalogLoader(ItemService itemService, ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        this.itemService = itemService;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (!itemService.isCatalogEmpty()) {
            return;
        }
        List<DemoItem> demoItems;
        try (InputStream catalog = resourceLoader.getResource(CATALOG_LOCATION).getInputStream()) {
            demoItems = objectMapper.readValue(catalog, new TypeReference<>() {
            });
        }
        for (DemoItem demoItem : demoItems) {
            itemService.createItem(new NewItem(demoItem.title(), demoItem.description(), demoItem.price(),
                    loadImage(demoItem.image())));
        }
        log.info("Loaded {} demo items into the catalog", demoItems.size());
    }

    private ImageData loadImage(String fileName) throws IOException {
        Resource resource = resourceLoader.getResource(IMAGES_LOCATION + fileName);
        String contentType = MediaTypeFactory.getMediaType(fileName)
                .orElse(MediaType.APPLICATION_OCTET_STREAM)
                .toString();
        return new ImageData(contentType, resource.getContentAsByteArray());
    }

    private record DemoItem(String title, String description, long price, String image) {
    }
}
