# My Market — витрина интернет-магазина, сервис платежей и сервер авторизации

Maven-мультипроект на **Spring Boot 3.5** из трёх приложений:

- **`market-app`** — веб-приложение «Витрина интернет-магазина» (WebFlux + Thymeleaf + R2DBC + Spring Security).
  Покупатели входят по логину и паролю, у каждого своя корзина и свои заказы. Анонимный посетитель видит только
  витрину и карточки товаров. Товары кешируются в **Redis**, оплата идёт REST-запросами в сервис платежей
  с access-токеном OAuth2.
- **`payment-service`** — RESTful-сервис платежей (WebFlux), **сервер ресурсов OAuth2**: баланс и списание со счёта
  покупателя. Принимает запросы только с JWT, выданным витрине сервером авторизации.
- **`auth-server`** — **сервер авторизации OAuth2** на Spring Authorization Server
  (`spring-boot-starter-oauth2-authorization-server`). В нём зарегистрированы витрина как клиент и сервис платежей
  как сервер ресурсов; токены выдаются по **Client Credentials Flow**.

Интеграция витрины и сервиса платежей описана OpenAPI-спецификацией [`openapi/payment-api.yaml`](openapi/payment-api.yaml).
По ней при сборке генерируются реактивный HTTP-клиент витрины (WebClient) и интерфейсы REST-контроллеров
сервиса платежей.

```
                       браузер (логин/пароль, сессия, CSRF)
                              │ HTML
                              ▼
   ┌──────────────────────────────────┐  (1) POST /oauth2/token          ┌──────────────────────┐
   │            market-app            │      client_credentials,         │      auth-server     │
   │              :8080               │ ───  market-app:secret ────────▶ │        :9000         │
   │  Spring Security: форма входа,   │ ◀──  JWT (aud=payment-service) ─ │ Spring Authorization │
   │  пользователи в БД (BCrypt)      │                                  │ Server               │
   │  WebFlux, R2DBC/H2               │                                  └──────────▲───────────┘
   └───────┬──────────────────┬───────┘                                             │
           │                  │ (2) GET  /api/accounts/{login}/balance              │ (3) JWK Set
           │                  │     POST /api/accounts/{login}/payments             │     /oauth2/jwks
           │                  │     Authorization: Bearer <JWT>                     │
           │                  ▼                                                     │
           │        ┌──────────────────────┐                                        │
           │        │   payment-service    │ ── проверяет подпись, iss, aud, ───────┘
           │        │        :8081         │    срок и скоупы токена
           │        │ OAuth2 Resource Srv  │
           │        └──────────────────────┘
           │ кеш товаров (Lettuce, reactive)
           ▼
   ┌────────────────┐
   │  Redis :6379   │
   └────────────────┘
```

## Технологии

| Что                 | Чем                                                                              |
|---------------------|----------------------------------------------------------------------------------|
| Язык                | Java 21                                                                          |
| Фреймворк           | Spring Boot 3.5 (Spring WebFlux, Project Reactor, встроенный Netty; Spring MVC — в сервере авторизации) |
| Безопасность        | Spring Security 6.5: form login для покупателей, OAuth2 Client (Client Credentials), OAuth2 Resource Server (JWT), Spring Authorization Server |
| Шаблоны             | Thymeleaf (reactive view resolver) + `thymeleaf-extras-springsecurity6` + Bootstrap 5 |
| Доступ к данным     | Spring Data R2DBC, H2 (in-memory) через `r2dbc-h2`                               |
| Кеш                 | Spring Data Redis, реактивный стек (`spring-boot-starter-data-redis-reactive`, Lettuce) |
| Интеграция          | OpenAPI 3, `openapi-generator-maven-plugin`: генераторы `java/webclient` и `spring` (reactive) |
| Тесты               | JUnit 5, Mockito, Reactor Test, Spring Boot Test, Spring Security Test, `@WebFluxTest`, `@DataR2dbcTest`, `@DataRedisTest`, MockMvc, Testcontainers, MockWebServer |
| Сборка              | Maven (мультимодульный проект, Maven Wrapper), executable JAR                    |
| Контейнеризация     | Docker (multi-stage build), Docker Compose                                       |

## Структура мультипроекта

```
.
├── pom.xml                    # родительский POM: модули, Java 21, версии генератора и MockWebServer, общие плагины
├── openapi/payment-api.yaml   # спецификация интеграции витрины и сервиса платежей (с OAuth2-схемой)
├── auth-server/               # сервер авторизации OAuth2
├── payment-service/           # сервис платежей (сервер ресурсов)
│   └── pom.xml                # + генерация серверных интерфейсов
├── market-app/                # витрина интернет-магазина (клиент OAuth2)
│   └── pom.xml                # + генерация клиента сервиса платежей
├── docker-compose.yml         # Redis + auth-server + payment-service + market-app
└── mvnw                       # Maven Wrapper для всего проекта
```

В каждом модуле — свой `Dockerfile` и `src/`. Модули друг от друга не зависят: витрину и сервис платежей связывает
контракт `openapi/payment-api.yaml`, а с сервером авторизации их связывают только настройки OAuth2.
Сгенерированный код не хранится в репозитории — он создаётся на фазе `generate-sources` в `target/generated-sources/openapi`.

## Авторизация

### Покупатели витрины (логин и пароль)

- Пользователи хранятся в таблице `users` витрины: логин и **BCrypt-хеш** пароля в формате `{bcrypt}$2...`
  (`DelegatingPasswordEncoder`). Открытые пароли нигде не хранятся.
- Два покупателя создаются при старте скриптом `market-app/src/main/resources/data.sql`:

  | Логин   | Пароль     | Счёт в сервисе платежей                       |
  |---------|------------|-----------------------------------------------|
  | `alice` | `password` | 100 000 руб.                                  |
  | `bob`   | `password` | 3 000 руб. — чтобы показать нехватку средств  |

- Новых покупателей можно зарегистрировать формой `/register` (логин — 3–50 латинских букв, цифр и `._-`,
  пароль — от 6 символов). Счёт в сервисе платежей открывается при первом обращении с балансом 100 000 руб.
- Вход — своя страница `/login`, логин и пароль проверяет фильтр Spring Security
  (`UserDetailsRepositoryReactiveAuthenticationManager` + `MarketUserDetailsService` + `PasswordEncoder`).
  Контекст безопасности хранится в `WebSession`. Principal — `MarketUserDetails` с id покупателя:
  контроллеры получают его через `@AuthenticationPrincipal` и работают только с корзиной и заказами этого покупателя.
- Выход — кнопка «Выйти» (`POST /logout`): очищается контекст безопасности, сессия инвалидируется,
  **все куки браузера** удаляются (`Max-Age=0`), по HTTPS дополнительно отправляется `Clear-Site-Data`.
- CSRF-защита включена: все формы содержат скрытое поле `_csrf` (`th:action` + `CsrfRequestDataValueProcessor`).

### Права доступа

| Страница / действие                                   | Аноним                 | Покупатель |
|-------------------------------------------------------|------------------------|------------|
| Витрина `/`, `/items`, карточка `/items/{id}`, картинки | да (без кнопок корзины) | да         |
| Вход `/login`, регистрация `/register`                | да                     | да         |
| `+`/`-` на витрине и в карточке (`POST /items`, `POST /items/{id}`) | **401**   | да         |
| Корзина `/cart/items`, покупка `POST /buy`            | **401**                | да         |
| Заказы `/orders`, `/orders/{id}`                      | **401**                | только свои (чужой заказ — 404) |
| Добавление товара `/admin/items/new`                  | **401**                | да         |

Ограничения действуют на трёх уровнях:

1. **HTML**: ссылки на корзину, заказы, добавление товара и кнопки изменения корзины выводятся только для
   `sec:authorize="isAuthenticated()"`; аноним видит кнопки «Войти» и «Регистрация» (`isAnonymous()`).
2. **URL**: правила `authorizeExchange` в `SecurityConfig`; анонимный запрос к закрытому адресу получает
   страницу ошибки **401** «Войдите в магазин…» с кнопкой входа.
3. **Методы контроллеров**: `@PreAuthorize("isAuthenticated()")` (`@EnableReactiveMethodSecurity`) — защита
   не зависит от того, не забыли ли обновить правила по URL.

### Межсервисная авторизация (OAuth2 Client Credentials Flow)

**Сервер авторизации** (`auth-server`, порт 9000):

- клиент **`market-app`** («Витрина интернет-магазина»): `client_secret_basic`, grant type `client_credentials`,
  скоупы `payments.read` и `payments.write`, access-токен — JWT на 5 минут. Секрет в конфигурации хранится как
  BCrypt-хеш; открытое значение `market-app-secret` знает только витрина;
- сервер ресурсов **`payment-service`**: `auth.resource-servers.payment-service.scopes=payments.read,payments.write`.
  Если клиенту выданы его скоупы, идентификатор `payment-service` добавляется в claim `aud` токена
  (`OAuth2TokenCustomizer`) — так сервис платежей проверяет, что токен выпущен для него;
- ключ подписи RSA генерируется при старте, публичная часть доступна в JWK Set `/oauth2/jwks`;
  метаданные — `/.well-known/oauth-authorization-server`. Всё, что не относится к протоколу OAuth2, закрыто.

**Витрина** (клиент OAuth2):

- регистрация клиента — свойства `spring.security.oauth2.client.registration.payment-service.*`
  и `spring.security.oauth2.client.provider.auth-server.token-uri`;
- `AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager` получает токен по Client Credentials Flow,
  хранит его и сам обновляет по истечении срока;
- фильтр `ClientCredentialsExchangeFilter` в `WebClient` сгенерированного клиента добавляет к каждому запросу
  `Authorization: Bearer <token>`. Токен принадлежит витрине как приложению, а не покупателю, поэтому он один
  на всех покупателей; покупатель передаётся логином в пути (`/api/accounts/{username}/...`);
- если сервис платежей ответил 401 (например, сервер авторизации перезапустился и сменил ключ), токен удаляется
  и следующий запрос получит новый;
- если сервер авторизации не выдал токен (неверный секрет, клиент не зарегистрирован, сервер недоступен),
  запрос в сервис платежей **не отправляется**: в корзине кнопка «Купить» заблокирована с сообщением
  о недоступности оплаты, `POST /buy` отвечает 503 «Витрина не авторизована в сервисе платежей».

**Сервис платежей** (сервер ресурсов OAuth2):

- `spring.security.oauth2.resourceserver.jwt.*`: подпись проверяется ключами из JWK Set сервера авторизации,
  кроме неё — срок действия, издатель (`iss` = адрес сервера авторизации) и получатель (`aud` содержит `payment-service`);
- `GET /api/accounts/{username}/balance` требует скоуп `payments.read`, `POST /api/accounts/{username}/payments` —
  `payments.write`;
- без токена или с недействительным токеном — **401** (`WWW-Authenticate: Bearer`), без нужного скоупа — **403**.
  Сессий, CSRF, формы входа и HTTP Basic нет.

## Сервис платежей

### API

| Метод | URL                                  | Скоуп            | Тело запроса       | Ответ                                         |
|-------|--------------------------------------|------------------|--------------------|-----------------------------------------------|
| GET   | `/api/accounts/{username}/balance`   | `payments.read`  | —                  | `200 {"amount": 100000}`                      |
| POST  | `/api/accounts/{username}/payments`  | `payments.write` | `{"amount": 5000}` | `200 {"amount": 5000, "balance": 95000}`      |
|       |                                      |                  |                    | `402 {"code": "INSUFFICIENT_FUNDS", ...}` — не хватает средств, баланс не меняется |
|       |                                      |                  |                    | `400 {"code": "INVALID_REQUEST", ...}` — сумма не положительная, тело некорректно или недопустимый логин |
|       |                                      |                  |                    | `401` — нет токена или он недействителен; `403` — нет скоупа |

Все суммы — целые рубли, формат обмена — JSON.

### Как устроен

- Генератор `spring` с опциями `reactive`, `interfaceOnly`, `useTags`, `useBeanValidation` создаёт интерфейсы
  `BalanceApi` и `PaymentsApi` и модели с ограничениями Bean Validation из спецификации
  (`minimum: 1` → `@Min(1)`, шаблон логина → `@Pattern`).
- `BalanceController` и `PaymentController` реализуют эти интерфейсы, `ApiExceptionHandler` переводит ошибки
  в `ErrorResponse` из спецификации, `SecurityConfig` задаёт правила доступа по скоупам.
- `AccountService` хранит счета покупателей в памяти, по одному на логин. Счёт открывается при первом обращении
  с балансом `payment.account.initial-balance` (100 000), для отдельных логинов баланс задаётся
  `payment.account.balances.<логин>` (у `bob` — 3 000). Списание атомарное (compare-and-set),
  поэтому параллельные платежи не уводят баланс в минус. При перезапуске балансы сбрасываются.

## Витрина интернет-магазина

### Функциональность

- **Витрина** (`/`, `/items`): плитка товаров по три в ряд, поиск по вхождению строки в название или описание,
  сортировка по алфавиту или цене, пагинация по 2/5/10/20/50/100 товаров. Вошедший покупатель видит кнопки `+`/`-`
  с количеством товара в своей корзине. Страницы берутся из кеша Redis.
- **Карточка товара** (`/items/{id}`): картинка, название, описание, цена; для покупателя — управление количеством
  в корзине. Карточка и картинка берутся из кеша Redis.
- **Корзина** (`/cart/items`): товары покупателя с количеством и ценой (данные товаров — из кеша), итоговая сумма,
  удаление товара, баланс счёта покупателя и кнопка «Купить». Кнопка доступна, только если баланса хватает;
  если не хватает или сервис платежей недоступен (или витрина не авторизована в нём), кнопка заблокирована
  и показано сообщение.
- **Покупка** (`POST /buy`): сумма корзины списывается со счёта покупателя; после успешной оплаты корзина
  переносится в новый заказ этого покупателя и очищается. При отказе (402) или недоступности (503) заказ
  не создаётся, корзина не меняется.
- **Заказы** (`/orders`, `/orders/{id}`): только заказы текущего покупателя; чужой заказ по номеру — 404.
- **Вход, выход, регистрация**: `/login`, кнопка «Выйти», `/register`.
- **Добавление товаров** (`/admin/items/new`): веб-форма с загрузкой картинки, доступна вошедшим покупателям.
- **Демо-каталог**: при старте пустая витрина заполняется товарами из `market-app/src/main/resources/demo/catalog.json`
  (отключается свойством `market.demo-data.enabled=false`).

### Эндпоинты

| Метод | URL                                                              | Доступ     | Результат                                    |
|-------|------------------------------------------------------------------|------------|----------------------------------------------|
| GET   | `/` или `/items?search=&sort=NO\|ALPHA\|PRICE&pageNumber=1&pageSize=5` | все  | шаблон `items`                               |
| POST  | `/items?id=&action=PLUS\|MINUS&search=&sort=&pageNumber=&pageSize=` | покупатель | редирект на `/items` с теми же параметрами |
| GET   | `/items/{id}`                                                    | все        | шаблон `item`                                |
| POST  | `/items/{id}?action=PLUS\|MINUS`                                 | покупатель | редирект на `/items/{id}`                    |
| GET   | `/cart/items`                                                    | покупатель | шаблон `cart`                                |
| POST  | `/cart/items?id=&action=PLUS\|MINUS\|DELETE`                     | покупатель | редирект на `/cart/items`                    |
| POST  | `/buy`                                                           | покупатель | редирект на `/orders/{id}?newOrder=true`; 402/503 при ошибке оплаты |
| GET   | `/orders`                                                        | покупатель | шаблон `orders`                              |
| GET   | `/orders/{id}?newOrder=false`                                    | покупатель | шаблон `order`                               |
| GET   | `/images/{itemId}`                                               | все        | байты картинки товара                        |
| GET   | `/admin/items/new`                                               | покупатель | форма добавления товара                      |
| POST  | `/admin/items` (multipart: `title`, `description`, `price`, `image`) | покупатель | редирект на `/items/{id}` нового товара  |
| GET   | `/login?error`, `/login?registered`                              | все        | шаблон `login`                               |
| POST  | `/login` (`username`, `password`)                                | все        | фильтр Spring Security: редирект на `/items` или `/login?error` |
| POST  | `/logout`                                                        | покупатель | редирект на `/items?logout`, сессия и куки удалены |
| GET   | `/register`                                                      | все        | шаблон `register`                            |
| POST  | `/register` (`username`, `password`, `confirmPassword`)          | все        | редирект на `/login?registered` или форма с ошибками |

Все POST-запросы, меняющие данные, отвечают редиректом (Post/Redirect/Get).

### Структура пакетов

```
market-app/src/main/java/ru/yandex/practicum/mymarket
├── cache         # RedisCacheConfig, ReactiveRedisCache (cache-aside), ItemCacheProperties
├── config        # Clock, клиент сервиса платежей (PaymentClientConfig, ClientCredentialsExchangeFilter),
│                 # загрузка демо-каталога
├── controller    # ItemController, CartController, OrderController, PurchaseController,
│                 # ImageController, ItemAdminController, AuthController (вход и регистрация)
├── dto           # ItemDto, ItemCard, ItemSummary, CatalogPage, Checkout, CartDto, OrderDto, RegistrationForm, ...
├── exception     # NotFoundException и наследники, EmptyCartException, InsufficientFundsException (402),
│                 # PaymentServiceUnavailableException и PaymentAuthorizationException (503), UsernameTakenException
├── mapper        # преобразование сущностей в DTO
├── model         # сущности Spring Data Relational (User, Item, CartItem, Order, ...); CartLine; enum ItemSort, CartAction
├── repository    # реактивные репозитории R2dbcRepository (Mono/Flux)
├── security      # SecurityConfig, MarketUserDetails, MarketUserDetailsService,
│                 # ExpireCookiesServerLogoutHandler, SecurityControllerAdvice (CSRF-токен и контекст для шаблонов)
└── service       # ItemService (каталог + кеш), CartService, OrderService, ShowcaseService,
                  # PaymentService (обёртка над сгенерированным клиентом), PurchaseService, UserService
```

Каталог (`ItemService`) общий для всех и не знает о покупателях. Корзина (`CartService`) и заказы (`OrderService`)
принимают id покупателя в каждом методе и работают только с его строками. Витрину и карточку товара собирает фасад
`ShowcaseService`; для анонимного посетителя корзину он не запрашивает.

### Кеш товаров в Redis

Используется реактивный стек Spring Data Redis. Для каждого типа данных создаётся `ReactiveRedisTemplate`
со строковыми ключами и JSON-значениями и оборачивается в `ReactiveRedisCache` — кеш вида cache-aside:
значение ищется в Redis, при промахе загружается из базы и сохраняется с TTL; пустой результат и ошибки не кешируются.

| Ключ                                              | Значение                                         | Где используется                  |
|---------------------------------------------------|--------------------------------------------------|-----------------------------------|
| `market:items:page:{sort}:{page}:{size}:{search}` | `CatalogPage`: товары (id, название, описание, цена) и общее количество | витрина: поиск, сортировка, пагинация |
| `market:items:card:{id}`                          | `ItemCard`: id, картинка (ссылка), название, цена, описание | карточка товара, корзина, покупка |
| `market:items:image:{id}`                         | `ImageData`: Content-Type и байты картинки       | `GET /images/{id}`                |

- Время жизни записей — **2 минуты** (`market.cache.ttl=2m`).
- Кешируются только данные каталога, общие для всех покупателей; количество в корзине читается из базы.
- При добавлении товара закешированные страницы (`market:items:page:*`) удаляются (`SCAN` + `DEL`).
- Если Redis недоступен, ошибка логируется, а данные загружаются из базы. Таймауты Lettuce — 1 секунда.

### Интеграция с сервисом платежей

- Генератор `java` с библиотекой `webclient` создаёт `ApiClient`, `BalanceApi`, `PaymentsApi` и модели.
- `PaymentClientConfig` передаёт в `ApiClient` `WebClient` с таймаутами Reactor Netty (соединение — 1 с, ответ — 3 с)
  и фильтром `ClientCredentialsExchangeFilter` (токен OAuth2).
- `PaymentService` переводит ответы в исключения: 402 → `InsufficientFundsException`, отказ в токене или 401/403
  от сервиса платежей → `PaymentAuthorizationException`, остальное → `PaymentServiceUnavailableException`.
- `PurchaseService.buy` сначала списывает деньги со счёта покупателя и только при успехе создаёт заказ
  и очищает его корзину (в одной R2DBC-транзакции).

### Загрузка картинок

- Формат картинки проверяется по сигнатуре байтов: принимаются только PNG, JPEG, GIF и WebP, SVG — нет.
- `GET /images/{itemId}` отдаёт картинку с `Content-Disposition: attachment`, `X-Content-Type-Options: nosniff`
  и `Content-Security-Policy: ... sandbox`.

### Схема базы данных

```
users (id, username UNIQUE, password)                        -- покупатели, password = {bcrypt}-хеш
items (id, title, description, price)
item_images (item_id PK/FK → items, content_type, content)
cart_items (id, user_id FK → users, item_id FK → items, quantity > 0, UNIQUE (user_id, item_id))
orders (id, user_id FK → users, created_at, total_sum)
order_items (id, order_id FK → orders, item_id FK → items, title, price, quantity)
```

Схема создаётся скриптом `market-app/src/main/resources/schema.sql`, демо-покупатели — `data.sql`
(`spring.sql.init.mode=always`).

## Конфигурация

Любое свойство можно переопределить переменной окружения (`SPRING_DATA_REDIS_HOST`, `MARKET_PAYMENT_URL`, ...).

`market-app`:

| Свойство                                                             | По умолчанию                          | Назначение                               |
|----------------------------------------------------------------------|---------------------------------------|------------------------------------------|
| `server.port`                                                        | `8080`                                | порт витрины                             |
| `spring.data.redis.host` / `.port`                                   | `localhost` / `6379`                  | сервер Redis                             |
| `market.cache.ttl`                                                   | `2m`                                  | время жизни товаров в кеше               |
| `market.payment.url`                                                 | `http://localhost:8081`               | адрес сервиса платежей                   |
| `market.payment.connect-timeout` / `.response-timeout`               | `1s` / `3s`                           | таймауты запросов в сервис платежей      |
| `market.payment.client-registration-id`                              | `payment-service`                     | регистрация OAuth2-клиента для платежей  |
| `spring.security.oauth2.client.registration.payment-service.client-id` | `market-app`                        | client_id витрины                        |
| `...registration.payment-service.client-secret`                      | `market-app-secret` (`MARKET_APP_CLIENT_SECRET`) | client_secret витрины         |
| `...registration.payment-service.scope`                              | `payments.read,payments.write`        | запрашиваемые скоупы                     |
| `spring.security.oauth2.client.provider.auth-server.token-uri`       | `http://localhost:9000/oauth2/token` (`AUTH_SERVER_URL`) | token endpoint        |
| `market.demo-data.enabled`                                           | `true`                                | загрузка демо-каталога                   |

`payment-service`:

| Свойство                                               | По умолчанию                          | Назначение                                     |
|--------------------------------------------------------|---------------------------------------|------------------------------------------------|
| `server.port`                                          | `8081`                                | порт сервиса платежей                          |
| `payment.account.initial-balance`                      | `100000`                              | начальный баланс нового счёта (`PAYMENT_ACCOUNT_INITIAL_BALANCE`) |
| `payment.account.balances.<логин>`                     | `bob=3000`                            | начальный баланс отдельного покупателя         |
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | `http://localhost:9000` (`AUTH_SERVER_URL`) | ожидаемый `iss` токенов                  |
| `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`| `http://localhost:9000/oauth2/jwks`   | ключи подписи                                  |
| `spring.security.oauth2.resourceserver.jwt.audiences`  | `payment-service`                     | ожидаемый `aud` токенов                        |

`auth-server`:

| Свойство                                                         | По умолчанию                | Назначение                          |
|------------------------------------------------------------------|-----------------------------|-------------------------------------|
| `server.port`                                                    | `9000`                      | порт сервера авторизации            |
| `spring.security.oauth2.authorizationserver.issuer`              | `http://localhost:9000` (`AUTH_SERVER_ISSUER`) | `iss` выдаваемых токенов |
| `spring.security.oauth2.authorizationserver.client.market-app.*` | см. выше                    | регистрация клиента «Витрина»       |
| `auth.resource-servers.payment-service.scopes`                   | `payments.read,payments.write` | регистрация сервера ресурсов «Сервис платежей» |

> Адрес сервера авторизации у сервиса платежей (`AUTH_SERVER_URL`) должен совпадать с `issuer` сервера авторизации:
> локально оба — `http://localhost:9000`, в Docker Compose — `http://auth-server:9000`.

## Требования

- JDK 21+
- Docker — для Redis, Docker Compose и интеграционных тестов витрины (Testcontainers)

Maven устанавливать не обязательно — в проекте есть Maven Wrapper (`./mvnw`).

## Сборка

Все команды выполняются из корня репозитория.

```bash
./mvnw clean package
```

Maven соберёт все три модуля: сгенерирует код по `openapi/payment-api.yaml`, прогонит тесты и создаст executable JAR:

- `auth-server/target/auth-server.jar`
- `payment-service/target/payment-service.jar`
- `market-app/target/market-app.jar`

Собрать один модуль:

```bash
./mvnw -pl auth-server package
```

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

Поднимаются Redis (6379), сервер авторизации (http://localhost:9000), сервис платежей (http://localhost:8081)
и витрина (http://localhost:8080). Остановка — `docker compose down`.

### Локально

1. Запустить Redis:

   ```bash
   docker run -d --name market-redis -p 6379:6379 redis:7.4-alpine
   ```

2. Запустить сервер авторизации, сервис платежей и витрину (каждый в своём терминале):

   ```bash
   java -jar auth-server/target/auth-server.jar
   ```

   ```bash
   java -jar payment-service/target/payment-service.jar
   ```

   ```bash
   java -jar market-app/target/market-app.jar
   ```

Без сборки JAR: `./mvnw -pl auth-server spring-boot:run`, `./mvnw -pl payment-service spring-boot:run`,
`./mvnw -pl market-app spring-boot:run`. В IntelliJ IDEA проект открывается как Maven-проект из корня;
запускаются классы `ru.yandex.practicum.auth.AuthServerApplication`, `ru.yandex.practicum.payment.PaymentServiceApplication`
и `ru.yandex.practicum.mymarket.MyMarketAppApplication`.

> База данных H2, балансы счетов и ключ подписи сервера авторизации хранятся в памяти и сбрасываются при перезапуске.
> После перезапуска сервера авторизации витрина один раз получит 401 на старый токен и сразу запросит новый.

## Использование

1. Открыть http://localhost:8080 — витрина с демо-каталогом. Без входа видны товары, но нет кнопок корзины,
   а по прямой ссылке http://localhost:8080/cart/items открывается ошибка 401.
2. Нажать «Войти» и войти как `alice` / `password` (или зарегистрироваться). Появятся кнопки `+`/`-`,
   «Корзина», «Заказы», «Добавить товар», имя покупателя и «Выйти».
3. Положить товары в корзину, открыть «Корзина»: под суммой показан баланс счёта alice из сервиса платежей.
   Нажать «Купить» — сумма спишется, откроется страница заказа.
4. Выйти и войти как `bob` / `password`: корзина и заказы у bob свои, заказы alice ему не видны. На счёте bob
   3 000 руб., поэтому на дорогую корзину кнопка «Купить» заблокируется с сообщением о нехватке средств.
5. Проверить защиту сервиса платежей:

   ```bash
   curl -i http://localhost:8081/api/accounts/alice/balance
   ```

   Ответ — `401`. Получить токен так же, как витрина, и повторить запрос:

   ```bash
   TOKEN=$(curl -s -u market-app:market-app-secret -d grant_type=client_credentials -d 'scope=payments.read payments.write' http://localhost:9000/oauth2/token | sed 's/.*"access_token":"\([^"]*\)".*/\1/')
   ```

   ```bash
   curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/accounts/alice/balance
   ```

   С неверным секретом сервер авторизации отвечает `401 invalid_client`, с токеном только `payments.read` списание
   отвечает `403`.

## Тесты

```bash
./mvnw verify
```

Для тестов витрины нужен запущенный Docker: Testcontainers поднимает контейнер `redis:7.4-alpine`.
Сервер авторизации и сервис платежей в тестах витрины заменены HTTP-заглушками на MockWebServer
(`AuthorizationServerStub` выдаёт токены по Client Credentials, `PaymentServiceStub` принимает только запросы
с выданным им токеном). В тестах сервиса платежей сервер авторизации заменён `TestAuthorizationServer`:
он публикует JWK Set тестового ключа и подписывает им настоящие JWT.

Тесты одного модуля или класса:

```bash
./mvnw -pl payment-service test
```

```bash
./mvnw -pl market-app test -Dtest='*IntegrationTest'
```

### auth-server

| Уровень        | Аннотация / инструмент                     | Классы                                                       |
|----------------|--------------------------------------------|--------------------------------------------------------------|
| Интеграционные | `@SpringBootTest` + MockMvc + `httpBasic()` | `TokenEndpointIntegrationTest`: выдача токена, `aud` и скоупы, неверный секрет, неизвестный клиент, чужой скоуп и grant type, JWK Set, закрытые эндпоинты |

### payment-service

| Уровень        | Аннотация / инструмент                            | Классы                                            |
|----------------|---------------------------------------------------|---------------------------------------------------|
| Модульные      | JUnit 5 + `StepVerifier`                          | `service/AccountServiceTest`: счета по логинам, параллельные списания |
| Веб-слой       | `@WebFluxTest` + `SecurityConfig` + `mockJwt()`   | `controller/*ControllerTest`: JSON-контракт, 400, 402, 401 без токена, 403 без скоупа |
| Интеграционные | `@SpringBootTest(RANDOM_PORT)` + настоящие JWT    | `PaymentApiIntegrationTest`, `PaymentApiSecurityIntegrationTest` (чужой ключ, issuer, audience, истёкший токен, скоупы), `PaymentServiceApplicationTests` |

### market-app

| Уровень                 | Аннотация / инструмент                                  | Классы                                               |
|-------------------------|---------------------------------------------------------|------------------------------------------------------|
| Доступ к данным         | `@DataR2dbcTest` + `StepVerifier`                       | `repository/*RepositoryTest`: корзины и заказы по покупателям, уникальность логина |
| Кеш Redis               | `@DataRedisTest` + Testcontainers                       | `cache/ReactiveRedisCacheTest`                       |
| Сервисы (модульные)     | JUnit 5 + Mockito + `StepVerifier`                      | `service/*ServiceTest`, в т. ч. `UserServiceTest` (BCrypt-хеш, занятый логин) |
| Клиент платежей         | сгенерированный клиент + MockWebServer + заглушка сервера авторизации | `service/PaymentServiceTest`: токен по Client Credentials, Bearer-заголовок, повторное использование токена, замена отклонённого токена, отказ в токене, 402, 500, таймаут |
| Веб-слой                | `@WebFluxTest` + `SecurityConfig` + `mockAuthentication()` / `csrf()` | `controller/*ControllerTest`: 401 для анонима, 403 без CSRF, скрытие кнопок в HTML, данные только текущего покупателя; `AuthControllerTest`: вход с BCrypt, ошибки входа, очистка кук при выходе, регистрация |
| Интеграционные          | `@SpringBootTest` + `WebTestClient` + Redis в Testcontainers + заглушки | `AuthenticationIntegrationTest` (настоящий вход по паролю из БД, смена id сессии, выход, регистрация), `UserDataIsolationIntegrationTest` (корзины, заказы и счета alice и bob не пересекаются), `PaymentAuthorizationIntegrationTest` (токен витрины в запросах, витрина без авторизации не может платить), `CheckoutIntegrationTest`, `ShopFlowIntegrationTest`, `ItemCacheIntegrationTest`, `PurchaseServiceIntegrationTest`, `MyMarketAppApplicationTests` |

### Кеширование контекстов

У тестов одного уровня общий базовый класс с одинаковой конфигурацией (`AbstractIntegrationTest`,
`AbstractControllerTest`, `AbstractRepositoryTest`), поэтому Spring поднимает контекст один раз на уровень
и берёт его из кеша: в витрине — 4 контекста на 204 теста, в сервисе платежей — 2, в сервере авторизации — 1.
Заглушки внешних сервисов — статические и одни на все тесты, их адреса не меняют конфигурацию контекста.
Перед каждым интеграционным тестом очищаются таблицы и Redis, заглушки сбрасываются, а сохранённый витриной
токен удаляется.
