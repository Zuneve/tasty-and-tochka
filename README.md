# Tasty and Tochka

Учебный REST API заказа еды на самовывоз из одного ресторана. Сервер реализован на Kotlin и Spring Boot как модульный монолит с PostgreSQL.

## Возможности

- Просмотр категорий и меню, фильтрация товаров по категории.
- Оформление заказа с проверкой существования и доступности товаров.
- Расчёт стоимости по ценам каталога; сохранение названий и цен на момент покупки.
- Получение заказа, изменение статуса и отмена нового заказа.
- Оптимистическая блокировка: устаревшее изменение возвращает `409`.
- Атомарное сохранение заказа и позиций, миграции Liquibase, единый JSON ошибок.
- Логи создания заказа и изменения статуса после успешного коммита транзакции.

Статусы: `CREATED → PREPARING → READY → COMPLETED`. Отмена `CREATED → CANCELLED` разрешена до начала приготовления. Повторные переходы и изменения завершённых заказов запрещены.

В этой версии нет авторизации, оплаты, доставки, бонусов и управления складом. Операции ресторана доступны через учебный API. Корзина передаётся списком позиций при оформлении.

## Стек и модули

Kotlin, Java 21, Spring Boot, Spring MVC, Spring JDBC, PostgreSQL 17, Liquibase, Bean Validation, JUnit и Testcontainers. SQL-запросы выполняются через `JdbcClient`.

```text
catalog/    категории, товары, публичный интерфейс CatalogQuery
ordering/   оформление заказа, бизнес-правила, статусы, хранение заказов
app/        запуск Spring Boot, общая обработка HTTP-ошибок, интеграционные тесты
```

`app` подключает оба бизнес-модуля, `ordering` обращается к каталогу через `catalog.api`. В бизнес-модулях разделены `domain`, `application` и `infrastructure` с HTTP- и PostgreSQL-адаптерами. Доменные модели не зависят от Spring, HTTP или БД.

Миграции каждого модуля лежат в его `src/main/resources/db/changelog`. Корневой changelog в `app` подключает их в порядке создания каталога, таблиц заказов и начального меню. При первом запуске появляются три категории и шесть товаров, один из которых недоступен для заказа.

## Запуск через Docker Compose

Нужен запущенный Docker с Docker Compose v2 или новее и поддержкой BuildKit. Java и Gradle на компьютере для этого способа запуска не требуются.

Команды ниже выполняются из корня проекта в Bash или Zsh:

```bash
cp -n .env.example .env
docker compose up --build --wait
```

Ключ `-n` сохраняет существующий `.env`. Если файл уже есть, добавь недостающие переменные из `.env.example`, сохранив свои настройки.

Сборка выполняется внутри Docker. При первом запуске нужен доступ к сети для скачивания базовых образов, Gradle и зависимостей. Compose ждёт готовности PostgreSQL, затем приложение запускает Liquibase. Проверка здоровья приложения читает категории через HTTP и тем самым проверяет доступ к БД. Параметр `--wait` ждёт готовности обоих сервисов; без него `docker compose up --build` запускает их с выводом логов в терминал.

API доступен по адресу `http://localhost:8080/api/v1`. Проверка запуска:

```bash
docker compose ps
curl --fail http://localhost:8080/api/v1/categories
docker compose logs --tail=100 app
```

После изменения кода повтори `docker compose up --build --wait`.

## Конфигурация

Значения `.env.example` предназначены для локального запуска. Файл `.env` исключён из Git и Docker build context.

| Переменная | Назначение | Пример / значение по умолчанию |
|---|---|---|
| `POSTGRES_DB` | Имя БД, обязательно | `tasty_and_tochka` |
| `POSTGRES_USER` | Пользователь БД, обязательно | `tasty` |
| `POSTGRES_PASSWORD` | Пароль БД, обязательно | значение из локального `.env` |
| `POSTGRES_PORT` | Порт PostgreSQL на компьютере | `5432` |
| `APP_PORT` | Порт HTTP API на компьютере при запуске через Compose | `8080` |
| `DB_URL` | JDBC URL при запуске приложения из IDE или Gradle | `jdbc:postgresql://localhost:5432/tasty_and_tochka` |
| `LOG_LEVEL` | Общий уровень логирования | `INFO` |
| `APP_LOG_LEVEL` | Уровень логирования кода приложения | `INFO` |

В контейнере приложение подключается к `postgres:5432`: `postgres` — имя сервиса в сети Compose. Compose формирует JDBC URL из `POSTGRES_DB` и не использует локальный `DB_URL`. Порты приложения и PostgreSQL публикуются только на `127.0.0.1`.

Если порт занят, поменяй `APP_PORT` или `POSTGRES_PORT` в `.env`. При изменении `APP_PORT` используй новый порт в примерах HTTP-запросов. При запуске приложения вне Docker также исправь `DB_URL` под выбранные имя БД и порт; локальный HTTP-порт Spring Boot по умолчанию равен `8080` и настраивается через `SERVER_PORT`.

PostgreSQL применяет имя БД, пользователя и пароль при инициализации пустого volume. Изменение этих переменных не перенастраивает уже существующую БД.

## API и пример сценария

Для примеров нужен `curl`. Все денежные значения передаются целыми числами в копейках. Например, `9900` означает 99 рублей.

| Метод | Путь | Назначение |
|---|---|---|
| `GET` | `/api/v1/categories` | Категории |
| `GET` | `/api/v1/products` | Меню |
| `GET` | `/api/v1/products?categoryId={uuid}` | Товары категории |
| `POST` | `/api/v1/orders` | Создать заказ |
| `GET` | `/api/v1/orders/{uuid}` | Получить заказ |
| `PATCH` | `/api/v1/orders/{uuid}/status` | Изменить статус с проверкой версии |

### 1. Посмотреть меню

```bash
curl --fail http://localhost:8080/api/v1/categories
curl --fail http://localhost:8080/api/v1/products
curl --fail 'http://localhost:8080/api/v1/products?categoryId=10000000-0000-0000-0000-000000000001'
```

### 2. Создать заказ

```bash
curl -i -X POST http://localhost:8080/api/v1/orders \
  -H 'Content-Type: application/json' \
  -d '{"items":[{"productId":"20000000-0000-0000-0000-000000000001","quantity":2},{"productId":"20000000-0000-0000-0000-000000000003","quantity":1}]}'
```

Ответ: `201 Created`, заголовок `Location: /api/v1/orders/{uuid}` и JSON заказа. Для начального меню сумма равна `31700` копеек, статус — `CREATED`, версия — `0`.

Заказ содержит от 1 до 100 разных товаров, количество каждого — от 1 до 99. Повторы товаров отклоняются. Клиент передаёт идентификаторы и количество; цены сервер получает из каталога.

### 3. Получить заказ и провести его по статусам

Скопируй `id` созданного заказа в переменную:

```bash
ORDER_ID='UUID_ИЗ_ОТВЕТА'
curl --fail "http://localhost:8080/api/v1/orders/$ORDER_ID"

curl --fail -X PATCH "http://localhost:8080/api/v1/orders/$ORDER_ID/status" \
  -H 'Content-Type: application/json' \
  -d '{"status":"PREPARING","version":0}'

curl --fail -X PATCH "http://localhost:8080/api/v1/orders/$ORDER_ID/status" \
  -H 'Content-Type: application/json' \
  -d '{"status":"READY","version":1}'

curl --fail -X PATCH "http://localhost:8080/api/v1/orders/$ORDER_ID/status" \
  -H 'Content-Type: application/json' \
  -d '{"status":"COMPLETED","version":2}'
```

Успешное изменение возвращает `200` и новую версию. В запросе передавай версию из последнего ответа сервера. Сохранение выполняет условный `UPDATE ... WHERE id = ... AND version = ...`: из двух изменений одной версии успешно применится одно. После `409 ORDER_VERSION_CONFLICT` перечитай заказ и реши, актуально ли ещё действие.

Для отмены создай другой новый заказ и отправь для него `{"status":"CANCELLED","version":0}`. Отмена после начала приготовления возвращает `409`.

### Ошибки

Ответы ошибок имеют общий формат:

```json
{
  "code": "ORDER_VERSION_CONFLICT",
  "message": "Order has changed; reload it before retrying",
  "productIds": null
}
```

`message` содержит описание, а стабильный `code` предназначен для обработки клиентом. При ошибках выбора товаров `productIds` содержит их идентификаторы.

| HTTP | Примеры кодов | Причина |
|---|---|---|
| `400` | `INVALID_REQUEST`, `INVALID_ORDER` | Некорректные поля, JSON или состав заказа |
| `404` | `ORDER_NOT_FOUND`, `NOT_FOUND` | Заказ или ресурс не найден |
| `405` | `METHOD_NOT_ALLOWED` | Метод не поддерживается; допустимые методы указаны в `Allow` |
| `406` | `NOT_ACCEPTABLE` | Запрошен неподдерживаемый формат ответа |
| `409` | `ORDER_VERSION_CONFLICT`, `INVALID_ORDER_STATUS_TRANSITION` | Устаревшая версия или запрещённый переход |
| `415` | `UNSUPPORTED_MEDIA_TYPE` | Неподдерживаемый `Content-Type` |
| `422` | `PRODUCTS_NOT_FOUND`, `PRODUCTS_UNAVAILABLE`, `ORDER_AMOUNT_TOO_LARGE` | Заказ нельзя оформить |
| `500` | `INTERNAL_ERROR` | Неожиданная ошибка; подробности остаются в серверном логе |

## Тесты

Для тестирования нужны JDK 21 и запущенный Docker. Gradle Wrapper скачивает нужную версию Gradle самостоятельно.

```bash
./gradlew test --no-daemon
./gradlew :app:bootJar --no-daemon
```

Unit-тесты проверяют доменные правила без Spring и БД. Интеграционные тесты поднимают отдельные PostgreSQL-контейнеры через Testcontainers, применяют миграции и выполняют настоящие HTTP-запросы. Локальный `.env` для тестов не нужен, данные Compose не используются.

Проверяются оформление и полный жизненный цикл заказа, отказ для неверных данных, сохранность цены, перезапуск приложения, откат при ошибке сохранения и коммита, отсутствие ложных логов успеха и конкурентные изменения одной версии. В тесте гонки оба запроса доходят до записи до снятия тестовой блокировки строки.

Dockerfile собирает исполняемый JAR через `bootJar`; тесты запускаются отдельно указанной командой. Полученный локально JAR находится в `app/build/libs/app.jar`.

## Запуск из IDE или Gradle

```bash
docker compose up -d --wait postgres
set -a
source .env
set +a
./gradlew :app:bootRun
```

Перед этим останови контейнер приложения, если он занимает тот же HTTP-порт: `docker compose stop app`. При запуске из IDE передай значения `.env` в переменные окружения конфигурации запуска `TastyAndTochkaApplicationKt`.

## Остановка и сохранность данных

```bash
docker compose down
docker compose up --build --wait
```

Именованный volume PostgreSQL сохраняется, поэтому после повторного запуска доступны прежние заказы, а Liquibase не повторяет выполненные changeset'ы.

Для намеренного удаления всех локальных данных проекта и следующего запуска с пустой БД:

```bash
docker compose down --volumes
docker compose up --build --wait
```

Первая команда в этом последнем примере удаляет volume и все заказы. Для обычного завершения работы достаточно `docker compose down` без `--volumes`.
