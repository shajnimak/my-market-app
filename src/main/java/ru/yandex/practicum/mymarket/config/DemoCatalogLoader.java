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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
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

    /**
     * Выполняется в главном потоке при старте приложения, до приёма запросов,
     * поэтому дождаться окончания загрузки здесь допустимо.
     */
    @Override
    public void run(ApplicationArguments args) {
        load().block();
    }

    /**
     * Загружает демо-каталог, если витрина пуста.
     *
     * @return количество добавленных товаров (0, если каталог уже заполнен)
     */
    public Mono<Long> load() {
        return itemService.isCatalogEmpty()
                .filter(Boolean::booleanValue)
                .flatMap(empty -> readCatalog()
                        .flatMapMany(Flux::fromIterable)
                        .concatMap(this::createItem)
                        .count()
                        .doOnNext(count -> log.info("Loaded {} demo items into the catalog", count)))
                .defaultIfEmpty(0L);
    }

    private Mono<List<DemoItem>> readCatalog() {
        return Mono.fromCallable(() -> {
            try (InputStream catalog = resourceLoader.getResource(CATALOG_LOCATION).getInputStream()) {
                return objectMapper.readValue(catalog, new TypeReference<List<DemoItem>>() {
                });
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Long> createItem(DemoItem demoItem) {
        return Mono.fromCallable(() -> loadImage(demoItem.image()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(image -> itemService.createItem(
                        new NewItem(demoItem.title(), demoItem.description(), demoItem.price(), image)));
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
