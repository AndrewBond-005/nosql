# Library Events Service

Веб-приложение на Spring Boot для управления заявками на книги-события.

## Терминология

| В интерфейсе | Что это | В API и базе |
|---|---|---|
| Каталог | Книга, которую можно взять | `events`, таблица `events`, `/api/events` |
| Событие | Факт взятия или возврата книги | `orders`, таблица `orders`, `/api/orders` |
| Временная заявка | Короткоживущая бронь с TTL | Etcd, `/api/temp-requests` |

Статусы события: `PENDING` — «Запрошена», `CONFIRMED` — «Выдана», `COMPLETED` — «Возвращена», `CANCELLED` — «Отменена».

Жизненный цикл события: взять книгу → `POST /api/orders` (копия резервируется) → выдать `PUT /api/orders/{id}/status?status=CONFIRMED` (сохраняется `issuedAt`) → вернуть `POST /api/orders/{id}/return` (сохраняется `returnedAt`, копия снова в каталоге). Вернуть можно только выданную книгу и только её владельцу; повторный возврат возвращает `409 Conflict`. Выданную книгу нельзя отменить — из выданного события есть только возврат.

## Где хранятся данные

| Данные | Хранилище |
|---|---|
| Книги (каталог) | PostgreSQL, таблица `events` |
| События (взятия и возвраты) | PostgreSQL, таблица `orders` |
| Настройки пользователей | PostgreSQL, таблица `user_settings` |
| Временные заявки | Etcd, ключи `librarytemp:{uuid}` с lease TTL |

Режима `inmemory` больше нет. Приложению всегда нужны PostgreSQL и Etcd.

## Требования

- JDK 17 или новее
- Maven 3.9+
- Docker Desktop с Docker Compose

Проверка:

```bash
java -version
mvn -version
docker --version
docker compose version
```

## Запуск с нуля

### 1. Запустить PostgreSQL и Etcd

Из корня проекта:

```bash
docker compose up -d
```

Если на компьютере уже установлен и запущен PostgreSQL, сначала проверь порт `5432`: локальный сервер и контейнер не должны одновременно занимать один порт.

```bash
netstat -ano | findstr :5432
```

Останови локальный PostgreSQL либо используй его вместо контейнера PostgreSQL из Compose.

Проверить состояние контейнеров:

```bash
docker compose ps
```

PostgreSQL должен перейти в состояние `healthy`, Etcd — `running`.

Проверить PostgreSQL:

```bash
docker compose exec postgres pg_isready -U library -d library
```

Ожидаемый ответ:

```text
/var/run/postgresql:5432 - accepting connections
```

Проверить Etcd:

```bash
docker compose exec etcd etcdctl endpoint health --endpoints=http://127.0.0.1:2379
```

### 2. Собрать и проверить приложение

```bash
mvn clean verify
```

Тесты `ConcurrentOrderTest` сами запускают временные контейнеры PostgreSQL и Etcd через Testcontainers. Docker должен быть запущен. Если Docker недоступен, тесты будут пропущены.

Готовый файл появится здесь:

```text
target/library-events-service-1.0.0.jar
```

### 3. Запустить приложение

```bash
java -jar target/library-events-service-1.0.0.jar
```

После запуска:

- приложение: http://localhost:8081
- PostgreSQL: `localhost:5432`
- Etcd: `localhost:2379`

Spring Boot автоматически создаст таблицы `events`, `orders`, `users` и `user_settings` при первом подключении к PostgreSQL. При пустой базе `DataInitializer` добавит демонстрационные книги. Пользователей нужно создавать через интерфейс: менеджером события всегда является авторизованный пользователь, отдельные сущности «менеджер» в проекте нет.

## Настройки подключения

Значения по умолчанию уже совпадают с `docker-compose.yml`:

| Переменная | Значение по умолчанию |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/library` |
| `SPRING_DATASOURCE_USERNAME` | `library` |
| `SPRING_DATASOURCE_PASSWORD` | `library` |
| `ETCD_ENDPOINTS` | `http://localhost:2379` |
| `ETCD_NAMESPACE` | `library` |
| `APP_CACHE_LOGGING` | `true` |

PowerShell:

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/library"
$env:SPRING_DATASOURCE_USERNAME = "library"
$env:SPRING_DATASOURCE_PASSWORD = "library"
$env:ETCD_ENDPOINTS = "http://localhost:2379"
java -jar target/library-events-service-1.0.0.jar
```

Чтобы передать настройки напрямую:

```bash
java -jar target/library-events-service-1.0.0.jar \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/library \
  --spring.datasource.username=library \
  --spring.datasource.password=library \
  --etcd.endpoints=http://localhost:2379
```

## Проверка данных

### PostgreSQL

```bash
docker compose exec postgres psql -U library -d library -c "SELECT id, title, available_copies, view_count FROM events;"
docker compose exec postgres psql -U library -d library -c "SELECT id, event_id, user_id, status, issued_at, returned_at FROM orders;"
docker compose exec postgres psql -U library -d library -c "SELECT * FROM user_settings;"
```

### Etcd

```bash
docker compose exec etcd etcdctl get --prefix librarytemp: --endpoints=http://127.0.0.1:2379
```

Создать временную заявку с TTL 30 секунд:

```bash
curl -X POST "http://localhost:8081/api/temp-requests?eventId=EVENT_ID&userId=user01&purpose=reservation&ttlSeconds=30"
```

Ключ временной заявки должен исчезнуть из Etcd автоматически примерно через 30 секунд.

## Проверка API

Создать событие:

```bash
curl -X POST http://localhost:8081/api/events \
  -H "Content-Type: application/json" \
  -d "{\"title\":\"Мастер и Маргарита\",\"description\":\"Роман\",\"author\":\"Михаил Булгаков\",\"category\":\"Классика\",\"availableCopies\":3}"
```

Получить события:

```bash
curl http://localhost:8081/api/events
```

Увеличить число просмотров:

```bash
curl -X POST http://localhost:8081/api/events/EVENT_ID/views
```

Создать заказ:

```bash
curl -X POST "http://localhost:8081/api/orders" \
  -H "Content-Type: application/json" \
  -d "{\"eventId\":\"EVENT_ID\"}"
```

Заказчик и менеджер заказа — всегда текущий авторизованный пользователь, отдельного менеджера выбирать не нужно.

Выдать и вернуть книгу:

```bash
curl -X PUT "http://localhost:8081/api/orders/ORDER_ID/status?status=CONFIRMED" \
  -H "Authorization: Basic BASE64_USER01_PASSWORD"

curl -X POST "http://localhost:8081/api/orders/ORDER_ID/return" \
  -H "Authorization: Basic BASE64_USER01_PASSWORD"
```

Прочитать и сохранить предпочтительную тему оформления (значения `LIGHT` и `DARK`):

```bash
curl -X PUT http://localhost:8081/api/settings/user01/theme \
  -H "Authorization: Basic BASE64_USER01_PASSWORD" \
  -H "Content-Type: application/json" \
  -d "{\"theme\":\"DARK\"}"

curl http://localhost:8081/api/settings/user01/theme \
  -H "Authorization: Basic BASE64_USER01_PASSWORD"
```

Тема хранится в профиле пользователя и отдаётся из кэша `userThemes`, поэтому повторный запрос не обращается к PostgreSQL.

### Как увидеть, что кэш работает

В `target/app.log` каждая операция с кэшем пишется одной строкой: `CACHE MISS`, `CACHE HIT`, `CACHE PUT`, `CACHE EVICT`, `CACHE CLEAR`. Выполните запрос дважды и сравните:

```bash
curl http://localhost:8081/api/events/EVENT_ID -H "Authorization: Basic BASE64_USER01_PASSWORD"
curl http://localhost:8081/api/events/EVENT_ID -H "Authorization: Basic BASE64_USER01_PASSWORD"
```

Первый запрос даёт `CACHE MISS` и `CACHE PUT`, второй — `CACHE HIT` без обращения к PostgreSQL. После бронирования или возврата копии в логе появляется `CACHE EVICT`, и следующий запрос снова читает базу. Отключить логи можно флагом `--app.cache-logging=false`.

### Демонстрация на защите

Откройте лог во втором окне PowerShell, чтобы видеть операции с кэшем в момент кликов:

```bash
Get-Content target\app.log -Wait -Encoding UTF8 | Select-String "CACHE |Button |Theme |Catalog read|Book created"
```

Дальше работайте в браузере на `http://localhost:8081`. Каждое действие пользователя даёт строку в логе:

| Действие в интерфейсе | Что появится в логе |
|---|---|
| Вход и загрузка страницы | `CACHE MISS` + `CACHE PUT` по `userThemes` при первом входе, дальше `CACHE HIT` |
| «Обновить» в каталоге | `Catalog read from PostgreSQL, the list is not cached: size=N` |
| «Добавить книгу» | `Book created in PostgreSQL, cache events has no entry for the new key: eventId=...` |
| «Открыть» у книги, первый раз | `CACHE MISS cache=events` + `CACHE PUT` |
| «Открыть» у той же книги, второй раз | `CACHE HIT cache=events` — база не запрашивалась |
| Клик по счётчику просмотров | `CACHE EVICT cache=events` |
| «Оформить» | `availableCopies decremented...` + `CACHE EVICT cache=events` + `Button 'Take the book' applied: orderId=...` |
| «Открыть» после брони | `CACHE MISS` + `CACHE PUT`, в карточке `Копий: 0` — кэш не отдал устаревшее значение |
| «Выдать» | `Button 'CONFIRMED' applied: orderId=..., PENDING -> CONFIRMED, ... cache events stays valid` — копии не менялись, поэтому кэш не трогаем |
| «Вернуть» | `availableCopies incremented...` + `CACHE EVICT cache=events` + `Button 'Return the book' applied: ... status=COMPLETED` |
| «Открыть» после возврата | `CACHE MISS` + `CACHE PUT`, в карточке `Копий: 1` |
| «Тёмная тема» в шапке | `CACHE EVICT cache=userSettings` + `CACHE PUT cache=userThemes value=DARK` |
| Перезагрузка страницы | `CACHE HIT cache=userThemes value=DARK` |
| «Удалить» книгу | `CACHE EVICT cache=events` |

Главная мысль демонстрации: попадание в кэш видно по `CACHE HIT` без обращения к базе, а любое изменение в PostgreSQL сразу вытесняет запись, поэтому устаревшее значение показать невозможно.

Тот же сценарий можно прогнать автоматически без браузера:

```bash
powershell -ExecutionPolicy Bypass -File demo-cache.ps1
```

Дополнительный аргумент — показать, что при попадании в кэш запрос в базу не уходит:

```bash
java -jar target/library-events-service-1.0.0.jar --spring.jpa.show-sql=true
```

Первый запрос сопровождается `Hibernate: select ... from events`, второй идёт без SQL, вместо него в логе только `CACHE HIT`.

Останавливать PostgreSQL во время демонстрации не нужно: при каждом запросе HTTP Basic-аутентификация читает пользователя из `users`, поэтому запросы всё равно обращаются к базе, даже когда данные книги отдаются из кэша.

## Остановка

Остановить приложение клавишами `Ctrl+C` в его терминале.

Остановить контейнеры:

```bash
docker compose down
```

Данные сохранятся в Docker volumes.

Полностью удалить локальные базы и данные Etcd:

```bash
docker compose down -v
```

Команда удаляет локальные данные PostgreSQL и Etcd без возможности восстановления.

## Частые проблемы

| Ошибка | Причина | Решение |
|---|---|---|
| `Connection refused` для PostgreSQL | PostgreSQL не запущена | `docker compose up -d`, затем `docker compose ps` |
| `Connection refused` для Etcd | Etcd не запущена | `docker compose up -d`, затем `docker compose exec etcd etcdctl endpoint health --endpoints=http://127.0.0.1:2379` |
| `password authentication failed` | Неверный пароль | Для локального запуска используй `library` / `library` |
| `port is already allocated` | Порт 5432 или 2379 занят | Останови процесс на порту либо измени порт в `docker-compose.yml` и настройках приложения |
| `Unable to rename ... .jar.original` | Приложение запущено из `target` | Останови приложение и повтори `mvn clean package` |
| Таблиц нет в PostgreSQL | Приложение ещё не подключалось | Запусти приложение и проверь `docker compose exec postgres psql -U library -d library -c "\dt"` |
