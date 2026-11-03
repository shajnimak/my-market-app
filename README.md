# My Market App — витрина интернет-магазина

Веб-приложение «Витрина интернет-магазина» на **Spring Boot** (реактивный стек: WebFlux + R2DBC):
пользователь просматривает товары, складывает их в корзину, оформляет заказы и смотрит историю покупок.

## Технологии

| Что                 | Чем                                                         |
|---------------------|-------------------------------------------------------------|
| Язык                | Java 21                                                     |
| Фреймворк           | Spring Boot 3.5 (Spring WebFlux, Project Reactor, встроенный Netty) |
| Шаблоны             | Thymeleaf (reactive view resolver) + Bootstrap 5            |
| Доступ к данным     | Spring Data R2DBC                                           |
| База данных         | H2 (in-memory) через реактивный драйвер `r2dbc-h2`, схема — `src/main/resources/schema.sql` |
| Тесты               | JUnit 5, Mockito, Reactor Test (`StepVerifier`), `@SpringBootTest`, `@WebFluxTest`, `@DataR2dbcTest`, `WebTestClient` |
| Сборка              | Maven (Maven Wrapper), executable JAR                       |
| Контейнеризация     | Docker (multi-stage build)                                  |

## Функциональность

- **Витрина** (`/`, `/items`): плитка товаров по три в ряд, поиск по вхождению строки в название или описание,
  сортировка по алфавиту или цене, пагинация по 2/5/10/20/50/100 товаров, кнопки `+`/`-` для изменения
  количества товара в корзине.
- **Карточка товара** (`/items/{id}`): картинка, название, описание, цена, управление количеством в корзине.
- **Корзина** (`/cart/items`): товары с количеством и ценой, итоговая сумма, удаление товара, кнопка «Купить».
- **Покупка** (`POST /buy`): эмулирует оформление заказа — содержимое корзины переносится в новый заказ,
  корзина очищается, пользователь попадает на страницу заказа.
- **Заказы** (`/orders`, `/orders/{id}`): список оформленных заказов с составом и суммой, страница отдельного заказа.
- **Добавление товаров** (`/admin/items/new`): веб-форма с загрузкой картинки (кнопка «Добавить товар» на витрине).
- **Демо-каталог**: при старте пустая витрина заполняется товарами из `src/main/resources/demo/catalog.json`
  (отключается свойством `market.demo-data.enabled=false`).

## Эндпоинты

| Метод | URL                                                              | Результат                                              |
|-------|------------------------------------------------------------------|--------------------------------------------------------|
| GET   | `/` или `/items?search=&sort=NO\|ALPHA\|PRICE&pageNumber=1&pageSize=5` | шаблон `items`                                   |
| POST  | `/items?id=&action=PLUS\|MINUS&search=&sort=&pageNumber=&pageSize=` | редирект на `/items` с теми же параметрами         |
| GET   | `/items/{id}`                                                    | шаблон `item`                                          |
| POST  | `/items/{id}?action=PLUS\|MINUS`                                 | редирект на `/items/{id}`                              |
| GET   | `/cart/items`                                                    | шаблон `cart`                                          |
| POST  | `/cart/items?id=&action=PLUS\|MINUS\|DELETE`                     | редирект на `/cart/items`                              |
| POST  | `/buy`                                                           | редирект на `/orders/{id}?newOrder=true`               |
| GET   | `/orders`                                                        | шаблон `orders`                                        |
| GET   | `/orders/{id}?newOrder=false`                                    | шаблон `order`                                         |
| GET   | `/images/{itemId}`                                               | байты картинки товара                                  |
| GET   | `/admin/items/new`                                               | форма добавления товара                                |
| POST  | `/admin/items` (multipart: `title`, `description`, `price`, `image`) | редирект на `/items/{id}` нового товара            |

Все POST-запросы, меняющие данные, отвечают редиректом (Post/Redirect/Get): обновление страницы
в браузере не отправляет форму повторно и не меняет количество товара ещё раз.

## Структура проекта

```
src/main/java/ru/yandex/practicum/mymarket
├── config        # Clock, реактивная загрузка демо-каталога
├── controller    # ItemController, CartController, OrderController, PurchaseController,
│                 # ImageController, ItemAdminController — возвращают Mono<Rendering>/Mono<String>
├── dto           # ItemDto, Paging, CartDto, OrderDto, OrderItemDto, ImageData, NewItem,
│                 # формы NewItemForm, ItemsQuery, CartActionForm, CartItemActionForm
├── exception     # NotFoundException и наследники, EmptyCartException
├── mapper        # преобразование сущностей в DTO
├── model         # сущности Spring Data Relational Item, ItemImage, CartItem, Order, OrderItem;
│                 # проекция CartLine; enum ItemSort, CartAction
├── repository    # реактивные репозитории R2dbcRepository (Mono/Flux)
└── service       # ItemService, CartService, OrderService, PurchaseService, ShowcaseService (Mono/Flux)
```

Слои взаимодействуют только сверху вниз: `controller → service → repository`. Контроллеры работают с DTO.
Вся цепочка обработки запроса неблокирующая: контроллеры возвращают `Mono`, сервисы и репозитории — `Mono`/`Flux`,
изменяющие методы сервисов помечены `@Transactional` и выполняются через `R2dbcTransactionManager`.

Модули каталога (`ItemService`) и корзины (`CartService`) друг от друга не зависят. Витрину и карточку товара,
где нужны и товары, и их количество в корзине, собирает фасад `ShowcaseService` уровнем выше.

### Загрузка картинок

- Формат картинки проверяется на сервере по сигнатуре первых байтов файла (`ImageFormat`): принимаются
  только PNG, JPEG, GIF и WebP. Имя файла и Content-Type от браузера не учитываются, в базу сохраняется
  тип, определённый сервером. SVG не принимается, так как может содержать скрипты.
- `GET /images/{itemId}` отдаёт картинку с заголовками `Content-Disposition: attachment`,
  `X-Content-Type-Options: nosniff` и `Content-Security-Policy: ... sandbox`. В `<img>` картинка
  показывается как обычно, а при открытии по прямой ссылке браузер не исполнит активное содержимое
  (это касается SVG из демо-каталога).

Особенности перехода на реактивный стек:

- R2DBC не поддерживает связи между сущностями, поэтому сущности хранят внешние ключи (`itemId`, `orderId`),
  а связанные данные подгружаются явно: корзина — одним запросом с `join` в проекцию `CartLine`,
  позиции всех заказов — одним запросом `findByOrderIdIn`.
- У картинки товара первичный ключ равен id товара и не генерируется базой, поэтому `ItemImage`
  реализует `Persistable` — так Spring Data отличает вставку от обновления.
- В WebFlux `@RequestParam` не читает тело формы, поэтому данные форм привязываются через `@ModelAttribute`
  к record-классам (`CartItemActionForm`, `ItemsQuery`), а картинка загружается как `FilePart`.
- Шаблон страницы ошибки лежит в `templates/error/error.html` — там его ищет `ErrorWebExceptionHandler` WebFlux.

### Схема базы данных

```
items (id, title, description, price)
item_images (item_id PK/FK → items, content_type, content)    -- картинки отдельно от товаров (varbinary)
cart_items (id, item_id UNIQUE FK → items, quantity > 0)       -- корзина: одна строка на товар
orders (id, created_at, total_sum)
order_items (id, order_id FK → orders, item_id FK → items, title, price, quantity)
```

В `order_items` название и цена товара копируются в момент покупки, поэтому оформленные заказы
не меняются при последующем изменении товара. Схема создаётся скриптом `schema.sql` при старте
(`spring.sql.init.mode=always`, Spring Boot выполняет его через R2DBC-соединение).

Подключение к базе настраивается в `application.properties`:

```properties
spring.r2dbc.url=r2dbc:h2:mem:///market?options=DB_CLOSE_DELAY=-1
spring.r2dbc.username=sa
```

## Требования

- JDK 21+
- Docker (для запуска в контейнере)

Maven устанавливать не обязательно — в проекте есть Maven Wrapper (`./mvnw`).

## Сборка и запуск

### В среде разработки

1. Открыть каталог проекта в IntelliJ IDEA как Maven-проект.
2. Выбрать JDK 21.
3. Запустить класс `ru.yandex.practicum.mymarket.MyMarketAppApplication`.
4. Открыть http://localhost:8080.

### Локально

```bash
./mvnw clean package
java -jar target/my-market-app.jar
```

Приложение запускается во встроенном Netty и будет доступно на http://localhost:8080. Порт можно поменять: `java -jar target/my-market-app.jar --server.port=9090`.

Запуск без сборки JAR:

```bash
./mvnw spring-boot:run
```

### В Docker

```bash
docker build -t my-market-app .
docker run --rm -p 8080:8080 my-market-app
```

Образ собирается в два этапа: в первом Maven прогоняет все тесты и собирает executable JAR, во втором JAR
запускается на `eclipse-temurin:21-jre` от непривилегированного пользователя во встроенном Netty.
Если тесты упали, образ не соберётся. Внешние сервисы для тестов не нужны — H2 работает в памяти. Приложение доступно на http://localhost:8080.

> База данных H2 работает в памяти, поэтому корзина и заказы сбрасываются при перезапуске приложения.

## Тесты

```bash
./mvnw test
```

Запуск одного класса или уровня тестов:

```bash
./mvnw test -Dtest='*ControllerTest'
```

| Уровень                 | Аннотация / инструмент                          | Классы                                               |
|-------------------------|-------------------------------------------------|------------------------------------------------------|
| Доступ к данным         | `@DataR2dbcTest` + `StepVerifier`               | `repository/*RepositoryTest`                         |
| Сервисы (модульные)     | JUnit 5 + Mockito + `StepVerifier`              | `service/*ServiceTest`                               |
| Веб-слой                | `@WebFluxTest` + `WebTestClient`                | `controller/*ControllerTest`                         |
| Интеграционные          | `@SpringBootTest` + `WebTestClient`             | `integration/*`, `MyMarketAppApplicationTests`       |

Реактивные транзакции не откатываются тестовым фреймворком автоматически, поэтому тесты с базой данных
очищают таблицы перед каждым тестом (`TestDatabase.clean`).

**Кеширование контекстов.** Тесты одного уровня имеют одинаковую конфигурацию, поэтому Spring TestContext
создаёт контекст один раз и переиспользует его:

- все `@DataR2dbcTest`-тесты наследуют `AbstractRepositoryTest`;
- все `@WebFluxTest`-тесты наследуют `AbstractControllerTest` с общим набором `@MockitoBean`;
- все `@SpringBootTest`-тесты наследуют `AbstractIntegrationTest` (одинаковые свойства и `@AutoConfigureWebTestClient`).

В итоге на весь прогон поднимается три контекста.

## Непрерывная интеграция

GitHub Actions (`.github/workflows/ci.yml`) на каждый push в `main` и ветки спринтов и на каждый pull request
в `main` запускает `./mvnw verify` (все тесты и сборка JAR) на JDK 21, а затем собирает Docker-образ.
Зелёные тесты проверяются автоматически до публикации образа.

