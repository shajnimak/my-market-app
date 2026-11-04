# My Market — витрина интернет-магазина и сервис платежей

Maven-мультипроект из двух приложений на **Spring Boot** (реактивный стек):

- **`market-app`** — веб-приложение «Витрина интернет-магазина» (WebFlux + Thymeleaf + R2DBC):
  пользователь просматривает товары, складывает их в корзину, оплачивает заказ и смотрит историю покупок.
  Товары кешируются в **Redis**, оплата идёт через REST-запросы в сервис платежей.
- **`payment-service`** — RESTful-сервис платежей (WebFlux): баланс счёта и списание суммы заказа.

Интеграция описана OpenAPI-спецификацией [`openapi/payment-api.yaml`](openapi/payment-api.yaml). По ней при сборке
генерируются реактивный HTTP-клиент витрины (WebClient) и интерфейсы REST-контроллеров сервиса платежей.

```
           браузер
              │ HTML
              ▼
   ┌────────────────────┐   GET /api/balance    ┌─────────────────────┐
   │     market-app     │   POST /api/payments  │   payment-service   │
   │       :8080        │ ────────────────────▶ │        :8081        │
   │  WebFlux, R2DBC/H2 │        JSON           │ WebFlux, баланс     │
   └─────────┬──────────┘                       │ в памяти            │
             │ кеш товаров (Lettuce, reactive)  └─────────────────────┘
             ▼
   ┌────────────────────┐
   │   Redis  :6379     │
   └────────────────────┘
```

## Технологии

| Что                 | Чем                                                                              |
|---------------------|----------------------------------------------------------------------------------|
| Язык                | Java 21                                                                          |
| Фреймворк           | Spring Boot 3.5 (Spring WebFlux, Project Reactor, встроенный Netty)              |
| Шаблоны             | Thymeleaf (reactive view resolver) + Bootstrap 5                                 |
| Доступ к данным     | Spring Data R2DBC, H2 (in-memory) через `r2dbc-h2`                               |
| Кеш                 | Spring Data Redis, реактивный стек (`spring-boot-starter-data-redis-reactive`, Lettuce) |
| Интеграция          | OpenAPI 3, `openapi-generator-maven-plugin`: генераторы `java/webclient` и `spring` (reactive) |
| Тесты               | JUnit 5, Mockito, Reactor Test, Spring Boot Test, `@WebFluxTest`, `@DataR2dbcTest`, `@DataRedisTest`, Testcontainers, MockWebServer |
| Сборка              | Maven (мультимодульный проект, Maven Wrapper), executable JAR                    |
| Контейнеризация     | Docker (multi-stage build), Docker Compose                                       |

## Структура мультипроекта

```
.
├── pom.xml                    # родительский POM: модули, Java 21, версия генератора, общие плагины
├── openapi/payment-api.yaml   # спецификация интеграции витрины и сервиса платежей
├── market-app/                # витрина интернет-магазина
│   ├── pom.xml                # + генерация клиента сервиса платежей
│   ├── Dockerfile
│   └── src/
├── payment-service/           # сервис платежей
│   ├── pom.xml                # + генерация серверных интерфейсов
│   ├── Dockerfile
│   └── src/
├── docker-compose.yml         # Redis + payment-service + market-app
└── mvnw                       # Maven Wrapper для всего проекта
```

Модули друг от друга не зависят: общий у них только контракт `openapi/payment-api.yaml`. Сгенерированный код
не хранится в репозитории — он создаётся на фазе `generate-sources` в `target/generated-sources/openapi`.

## Сервис платежей

### API

| Метод | URL             | Тело запроса       | Ответ                                                         |
|-------|-----------------|--------------------|---------------------------------------------------------------|
| GET   | `/api/balance`  | —                  | `200 {"amount": 100000}`                                      |
| POST  | `/api/payments` | `{"amount": 5000}` | `200 {"amount": 5000, "balance": 95000}`                      |
|       |                 |                    | `402 {"code": "INSUFFICIENT_FUNDS", "message": "..."}` — не хватает средств, баланс не меняется |
|       |                 |                    | `400 {"code": "INVALID_REQUEST", "message": "..."}` — сумма не положительная или тело некорректно |

Все суммы — целые рубли, формат обмена — JSON.

### Как устроен

- Генератор `spring` с опциями `reactive`, `interfaceOnly`, `useTags`, `useBeanValidation` создаёт интерфейсы
  `BalanceApi` и `PaymentsApi` (маппинги `@RequestMapping`, `Mono<ResponseEntity<...>>`) и модели запросов/ответов
  с ограничениями Bean Validation из спецификации (`minimum: 1` → `@Min(1)`).
- `BalanceController` и `PaymentController` реализуют эти интерфейсы, `ApiExceptionHandler` переводит ошибки
  в `ErrorResponse` из спецификации.
- `AccountService` хранит баланс в памяти. Начальный баланс берётся из конфигурации
  (`payment.account.initial-balance`, по умолчанию 100000). Списание атомарное (compare-and-set),
  поэтому параллельные платежи не уводят баланс в минус. При перезапуске баланс сбрасывается.

## Витрина интернет-магазина

### Функциональность

- **Витрина** (`/`, `/items`): плитка товаров по три в ряд, поиск по вхождению строки в название или описание,
  сортировка по алфавиту или цене, пагинация по 2/5/10/20/50/100 товаров, кнопки `+`/`-` для изменения
  количества товара в корзине. Страницы берутся из кеша Redis.
- **Карточка товара** (`/items/{id}`): картинка, название, описание, цена, управление количеством в корзине.
  Карточка и картинка берутся из кеша Redis.
- **Корзина** (`/cart/items`): товары с количеством и ценой (данные товаров — из кеша), итоговая сумма, удаление
  товара, баланс счёта и кнопка «Купить». Кнопка доступна, только если баланса в сервисе платежей хватает на корзину;
  если не хватает или сервис платежей недоступен, кнопка заблокирована и показано сообщение.
- **Покупка** (`POST /buy`): сумма корзины списывается в сервисе платежей; после успешной оплаты содержимое корзины
  переносится в новый заказ, корзина очищается, пользователь попадает на страницу заказа. Если платёж отклонён
  (402) или сервис недоступен (503), показывается страница с ошибкой, заказ не создаётся, корзина не меняется.
- **Заказы** (`/orders`, `/orders/{id}`): список оформленных заказов с составом и суммой, страница отдельного заказа.
- **Добавление товаров** (`/admin/items/new`): веб-форма с загрузкой картинки (кнопка «Добавить товар» на витрине).
- **Демо-каталог**: при старте пустая витрина заполняется товарами из `market-app/src/main/resources/demo/catalog.json`
  (отключается свойством `market.demo-data.enabled=false`).

### Эндпоинты

| Метод | URL                                                              | Результат                                              |
|-------|------------------------------------------------------------------|--------------------------------------------------------|
| GET   | `/` или `/items?search=&sort=NO\|ALPHA\|PRICE&pageNumber=1&pageSize=5` | шаблон `items`                                   |
| POST  | `/items?id=&action=PLUS\|MINUS&search=&sort=&pageNumber=&pageSize=` | редирект на `/items` с теми же параметрами         |
| GET   | `/items/{id}`                                                    | шаблон `item`                                          |
| POST  | `/items/{id}?action=PLUS\|MINUS`                                 | редирект на `/items/{id}`                              |
| GET   | `/cart/items`                                                    | шаблон `cart`                                          |
| POST  | `/cart/items?id=&action=PLUS\|MINUS\|DELETE`                     | редирект на `/cart/items`                              |
| POST  | `/buy`                                                           | редирект на `/orders/{id}?newOrder=true`; 402/503 при ошибке оплаты |
| GET   | `/orders`                                                        | шаблон `orders`                                        |
| GET   | `/orders/{id}?newOrder=false`                                    | шаблон `order`                                         |
| GET   | `/images/{itemId}`                                               | байты картинки товара                                  |
| GET   | `/admin/items/new`                                               | форма добавления товара                                |
| POST  | `/admin/items` (multipart: `title`, `description`, `price`, `image`) | редирект на `/items/{id}` нового товара            |

Все POST-запросы, меняющие данные, отвечают редиректом (Post/Redirect/Get): обновление страницы
в браузере не отправляет форму повторно и не меняет количество товара ещё раз.

### Структура пакетов

```
market-app/src/main/java/ru/yandex/practicum/mymarket
├── cache         # RedisCacheConfig, ReactiveRedisCache (cache-aside), ItemCacheProperties
├── config        # Clock, клиент сервиса платежей (PaymentClientConfig), загрузка демо-каталога
├── controller    # ItemController, CartController, OrderController, PurchaseController,
│                 # ImageController, ItemAdminController — возвращают Mono<Rendering>/Mono<String>
├── dto           # ItemDto, ItemCard, ItemSummary, CatalogPage, Checkout, CartDto, OrderDto, ...
├── exception     # NotFoundException и наследники, EmptyCartException,
│                 # InsufficientFundsException (402), PaymentServiceUnavailableException (503)
├── mapper        # преобразование сущностей в DTO
├── model         # сущности Spring Data Relational; CartLine; enum ItemSort, CartAction
├── repository    # реактивные репозитории R2dbcRepository (Mono/Flux)
└── service       # ItemService (каталог + кеш), CartService, OrderService, ShowcaseService,
                  # PaymentService (обёртка над сгенерированным клиентом), PurchaseService
```

Сгенерированный клиент лежит в пакете `ru.yandex.practicum.mymarket.payment.client` (`api.BalanceApi`,
`api.PaymentsApi`, `model.*`, `ApiClient`).

Слои взаимодействуют только сверху вниз: `controller → service → repository / кеш / клиент`. Вся цепочка обработки
запроса неблокирующая: контроллеры возвращают `Mono`, сервисы и репозитории — `Mono`/`Flux`, Redis и сервис платежей
вызываются через реактивные клиенты (Lettuce и WebClient на Reactor Netty).

Каталог (`ItemService`) не знает о корзине. Корзина (`CartService`) хранит в базе только id товаров и количество,
а данные товаров берёт из каталога. Витрину и карточку товара, где нужны и товары, и их количество в корзине,
собирает фасад `ShowcaseService`.

### Кеш товаров в Redis

Используется реактивный стек Spring Data Redis. Подключение (`ReactiveRedisConnectionFactory` на Lettuce) настраивает
Spring Boot по свойствам `spring.data.redis.*`. В `RedisCacheConfig` для каждого типа данных создаётся
`ReactiveRedisTemplate` со строковыми ключами и JSON-значениями (`Jackson2JsonRedisSerializer` с `ObjectMapper`
приложения) и оборачивается в `ReactiveRedisCache` — кеш вида cache-aside:

1. значение ищется в Redis по ключу;
2. при промахе загружается из базы и сохраняется в Redis с TTL;
3. пустой результат и ошибки (например, «товар не найден») не кешируются.

| Ключ                                              | Значение                                         | Где используется                  |
|---------------------------------------------------|--------------------------------------------------|-----------------------------------|
| `market:items:page:{sort}:{page}:{size}:{search}` | `CatalogPage`: товары (id, название, описание, цена) и общее количество | витрина: поиск, сортировка, пагинация |
| `market:items:card:{id}`                          | `ItemCard`: id, картинка (ссылка), название, цена, описание | карточка товара, корзина, покупка |
| `market:items:image:{id}`                         | `ImageData`: Content-Type и байты картинки       | `GET /images/{id}`                |

- Время жизни записей — **2 минуты** (`market.cache.ttl=2m`).
- Строка поиска в ключе приводится к нижнему регистру: поиск регистронезависимый, поэтому «Мяч» и «МЯЧ» — одна запись.
- При добавлении товара все закешированные страницы (`market:items:page:*`) удаляются через `SCAN` + `DEL`:
  новый товар может попасть на любую страницу. Удаление выполняется после фиксации транзакции в базе.
  Карточек и картинки нового товара в кеше ещё нет, поэтому их сбрасывать не нужно.
- Чтение из кеша не открывает транзакцию базы данных — соединение с H2 берётся только при промахе.
- Если Redis недоступен или запись в нём испорчена, ошибка логируется, а данные загружаются из базы:
  кеш ускоряет работу, но не ломает её. Таймауты Lettuce — 1 секунда.

### Интеграция с сервисом платежей

- Генератор `java` с библиотекой `webclient` создаёт `ApiClient`, `BalanceApi`, `PaymentsApi` и модели.
- `PaymentClientConfig` передаёт в `ApiClient` `WebClient`, собранный из автоконфигурированного `WebClient.Builder`
  (кодеки Jackson Spring Boot) с Reactor Netty-таймаутами: соединение — 1 с, ответ — 3 с. Адрес сервиса —
  `market.payment.url`.
- `PaymentService` переводит ответы в исключения предметной области: 402 → `InsufficientFundsException`
  (с сообщением из `ErrorResponse`), любые другие ошибки и таймауты → `PaymentServiceUnavailableException`.
- `PurchaseService.getCheckout` сравнивает баланс с суммой корзины для страницы корзины; недоступность сервиса —
  не ошибка страницы, а состояние «кнопка заблокирована». `PurchaseService.buy` сначала списывает деньги
  и только при успехе создаёт заказ и очищает корзину (в одной R2DBC-транзакции).

### Загрузка картинок

- Формат картинки проверяется на сервере по сигнатуре первых байтов файла (`ImageFormat`): принимаются
  только PNG, JPEG, GIF и WebP. Имя файла и Content-Type от браузера не учитываются, в базу сохраняется
  тип, определённый сервером. SVG не принимается, так как может содержать скрипты.
- `GET /images/{itemId}` отдаёт картинку с заголовками `Content-Disposition: attachment`,
  `X-Content-Type-Options: nosniff` и `Content-Security-Policy: ... sandbox`. В `<img>` картинка
  показывается как обычно, а при открытии по прямой ссылке браузер не исполнит активное содержимое
  (это касается SVG из демо-каталога).

### Особенности реактивного стека

- R2DBC не поддерживает связи между сущностями, поэтому сущности хранят внешние ключи (`itemId`, `orderId`),
  а связанные данные подгружаются явно: позиции всех заказов — одним запросом `findByOrderIdIn`,
  данные товаров корзины — из кеша.
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
не меняются при последующем изменении товара. Схема создаётся скриптом `market-app/src/main/resources/schema.sql`
при старте (`spring.sql.init.mode=always`).

## Конфигурация

`market-app/src/main/resources/application.properties` (любое свойство можно переопределить переменной окружения,
например `SPRING_DATA_REDIS_HOST`, `MARKET_PAYMENT_URL`):

| Свойство                            | По умолчанию              | Назначение                          |
|-------------------------------------|---------------------------|-------------------------------------|
| `server.port`                       | `8080`                    | порт витрины                        |
| `spring.data.redis.host` / `.port`  | `localhost` / `6379`      | сервер Redis                        |
| `market.cache.ttl`                  | `2m`                      | время жизни товаров в кеше          |
| `market.payment.url`                | `http://localhost:8081`   | адрес сервиса платежей              |
| `market.payment.connect-timeout`    | `1s`                      | таймаут соединения с сервисом платежей |
| `market.payment.response-timeout`   | `3s`                      | таймаут ответа сервиса платежей     |
| `market.demo-data.enabled`          | `true`                    | загрузка демо-каталога              |

`payment-service/src/main/resources/application.properties`:

| Свойство                            | По умолчанию | Назначение                                     |
|-------------------------------------|--------------|------------------------------------------------|
| `server.port`                       | `8081`       | порт сервиса платежей                          |
| `payment.account.initial-balance`   | `100000`     | начальный баланс счёта, руб. (переменная `PAYMENT_ACCOUNT_INITIAL_BALANCE`) |

## Требования

- JDK 21+
- Docker — для Redis, Docker Compose и интеграционных тестов витрины (Testcontainers)

Maven устанавливать не обязательно — в проекте есть Maven Wrapper (`./mvnw`).

## Сборка

Все команды выполняются из корня репозитория.

```bash
./mvnw clean package
```

Maven соберёт оба модуля: сгенерирует код по `openapi/payment-api.yaml`, прогонит тесты и создаст executable JAR:

- `payment-service/target/payment-service.jar`
- `market-app/target/market-app.jar`

Собрать только один модуль:

```bash
./mvnw -pl payment-service package
```

```bash
./mvnw -pl market-app package
```

Только сгенерировать код (например, после изменения спецификации, чтобы IDE увидела новые классы):

```bash
./mvnw generate-sources
```

## Запуск

### Docker Compose (всё сразу)

```bash
docker compose up --build
```

Поднимаются Redis (6379), сервис платежей (http://localhost:8081) и витрина (http://localhost:8080).
Начальный баланс можно задать переменной: `PAYMENT_INITIAL_BALANCE=5000 docker compose up --build`.
Остановка — `docker compose down`.

### Локально

1. Запустить Redis (локально установленный или в Docker):

   ```bash
   docker run -d --name market-redis -p 6379:6379 redis:7.4-alpine
   ```

2. Запустить сервис платежей:

   ```bash
   java -jar payment-service/target/payment-service.jar
   ```

3. Запустить витрину:

   ```bash
   java -jar market-app/target/market-app.jar
   ```

Без сборки JAR: `./mvnw -pl payment-service spring-boot:run` и `./mvnw -pl market-app spring-boot:run`.
В IntelliJ IDEA проект открывается как Maven-проект из корня; запускаются классы
`ru.yandex.practicum.payment.PaymentServiceApplication` и `ru.yandex.practicum.mymarket.MyMarketAppApplication`.

### Контейнеры вручную

Образы собираются из корня мультипроекта (им нужны родительский POM и спецификация):

```bash
docker build -f payment-service/Dockerfile -t my-market/payment-service .
```

```bash
docker build -f market-app/Dockerfile -t my-market/market-app .
```

Сборка образа сервиса платежей прогоняет его тесты. Тесты витрины при сборке образа не запускаются:
им нужен Docker для Testcontainers, а внутри `docker build` его нет. Они выполняются в `./mvnw verify` и в CI.

> База данных H2 и баланс счёта хранятся в памяти, поэтому корзина, заказы и баланс сбрасываются при перезапуске.

## Использование

1. Открыть http://localhost:8080 — витрина с демо-каталогом.
2. Найти товары поиском, отсортировать, переключить размер страницы. Повторное открытие той же страницы в течение
   2 минут обслуживается из Redis — ключи можно посмотреть: `docker compose exec redis redis-cli --scan --pattern 'market:*'`.
3. Положить товары в корзину кнопками `+`/`-` и открыть «Корзина». Под итоговой суммой показан баланс счёта.
4. Нажать «Купить»: сумма спишется в сервисе платежей, откроется страница нового заказа.
   Баланс можно проверить напрямую: `curl http://localhost:8081/api/balance`.
5. Если положить в корзину товаров больше, чем позволяет баланс, кнопка «Купить» заблокируется с сообщением
   о нехватке средств. Если остановить сервис платежей (`docker compose stop payment-service`), корзина сообщит,
   что он недоступен.
6. Добавить свой товар — кнопка «Добавить товар» на витрине; закешированные страницы витрины сбросятся сразу.

## Тесты

```bash
./mvnw verify
```

Для тестов витрины нужен запущенный Docker: Testcontainers поднимает контейнер `redis:7.4-alpine`.
Сервис платежей в тестах витрины заменён HTTP-заглушкой на MockWebServer, отвечающей по той же спецификации.

Тесты одного модуля или класса:

```bash
./mvnw -pl payment-service test
```

```bash
./mvnw -pl market-app test -Dtest='*CacheIntegrationTest'
```

### payment-service

| Уровень             | Аннотация / инструмент                            | Классы                                            |
|---------------------|---------------------------------------------------|---------------------------------------------------|
| Модульные           | JUnit 5 + `StepVerifier`                          | `service/AccountServiceTest` (в т. ч. параллельные списания) |
| Веб-слой            | `@WebFluxTest` + `WebTestClient` + `@MockitoBean` | `controller/*ControllerTest` (JSON-контракт, 400, 402) |
| Интеграционные      | `@SpringBootTest(RANDOM_PORT)` + `WebTestClient`  | `PaymentApiIntegrationTest`, `PaymentServiceApplicationTests` |

### market-app

| Уровень                 | Аннотация / инструмент                                  | Классы                                               |
|-------------------------|---------------------------------------------------------|------------------------------------------------------|
| Доступ к данным         | `@DataR2dbcTest` + `StepVerifier`                       | `repository/*RepositoryTest`                         |
| Кеш Redis               | `@DataRedisTest` + Testcontainers                       | `cache/ReactiveRedisCacheTest` (cache-aside, TTL, JSON, сброс) |
| Сервисы (модульные)     | JUnit 5 + Mockito + `StepVerifier`                      | `service/*ServiceTest`                               |
| Клиент платежей         | сгенерированный клиент + MockWebServer                  | `service/PaymentServiceTest` (402, 500, таймаут, сервис выключен) |
| Веб-слой                | `@WebFluxTest` + `WebTestClient`                        | `controller/*ControllerTest`                         |
| Интеграционные          | `@SpringBootTest` + `WebTestClient` + Redis в Testcontainers + заглушка платежей | `integration/*`, `MyMarketAppApplicationTests` |

Интеграционные тесты кеширования (`ItemCacheIntegrationTest`) меняют данные в базе в обход сервисов и проверяют,
что карточка, страница витрины, корзина и картинка продолжают отдаваться из Redis, что ключи создаются с TTL,
а добавление товара сбрасывает закешированные страницы. Тесты оплаты (`CheckoutIntegrationTest`,
`PurchaseServiceIntegrationTest`) проверяют кнопку «Купить» при разном балансе и при недоступном сервисе,
списание суммы заказа и то, что при отказе в оплате заказ не создаётся.

Перед каждым интеграционным тестом очищаются таблицы и Redis (`FLUSHDB`), а заглушка платежей возвращается
в исходное состояние: реактивные транзакции не откатываются тестовым фреймворком автоматически.

**Кеширование контекстов.** Тесты одного уровня имеют одинаковую конфигурацию, поэтому Spring TestContext
создаёт контекст один раз и переиспользует его:

- `@DataR2dbcTest`-тесты наследуют `AbstractRepositoryTest`;
- `@WebFluxTest`-тесты наследуют `AbstractControllerTest` с общим набором `@MockitoBean` (в обоих модулях);
- `@SpringBootTest`-тесты наследуют `AbstractIntegrationTest` (в обоих модулях): одинаковые свойства,
  в витрине — общий `@Import(TestcontainersConfiguration.class)` и одна статическая заглушка платежей,
  адрес которой регистрирует один и тот же `@DynamicPropertySource`.

Контейнер Redis — бин тестовой конфигурации с `@ServiceConnection`, поэтому он живёт вместе с закешированным
контекстом. На весь прогон поднимается четыре контекста в витрине (R2DBC, Redis, WebFlux, полный) и два
в сервисе платежей.

## Непрерывная интеграция

GitHub Actions (`.github/workflows/ci.yml`) на каждый push в `main` и ветки спринтов и на каждый pull request
в `main` запускает `./mvnw verify` для обоих модулей на JDK 21 (Docker на раннере нужен Testcontainers),
а затем собирает образы через `docker compose build`.
