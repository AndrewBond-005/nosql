# README

## Оглавление
* [Что это за проект](#что-это-за-проект)
* [Профили](#профили)
* [Структура проекта](#структура-проекта)
* [Как поднять всё и заставить работать](#как-поднять-всё-и-заставить-работать)
  * [Версия 1. Подробная (для первого запуска)](#версия-1-подробная-для-первого-запуска)
  * [Версия 2. Короткая (для тех, кто уже в теме)](#версия-2-короткая-для-тех-кто-уже-в-теме)
* [Частые проблемы](#частые-проблемы)
* [Что где лежит (карта ключей в etcd)](#что-где-лежит-карта-ключей-в-etcd)

---

## Что это за проект

Веб-приложение на Spring Boot — сервис заявок библиотеки. Данные хранятся в etcd (key-value БД). Менеджер оформляет заказы на книги-события, работает TTL для временных заявок, кэш для настроек, атомарный счётчик просмотров.

## Профили

В проекте два профиля Spring — они определяют, **где хранятся данные**:

| Профиль | Нужен ли etcd | Где данные | Когда использовать |
|---|---|---|---|
| `inmemory` (по умолчанию) | Нет | В памяти Java (HashMap) | Быстрая проверка логики, юнит-тесты |
| `etcd` | Да, обязательно | В etcd | Полная проверка задания: lease, CAS-счётчик, snapshot |

Переключение: `--spring.profiles.active=etcd` (или `inmemory`).

**Важно:** без явного указания профиля приложение поднимется в `inmemory` — etcd ему не нужна, но данные исчезнут при перезапуске.

---

## Структура проекта

```text
.
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── example/
│       │           └── library/
│       │               ├── LibraryApplication.java      # Точка входа Spring Boot
│       │               ├── config/                      # Конфигурация etcd и бинов
│       │               ├── controller/                  # REST-контроллеры (events, orders)
│       │               ├── model/                       # DTO и доменные модели
│       │               └── service/                     # Бизнес-логика (InMemory / Etcd реализации)
│       └── resources/
│           ├── application.properties                   # Общие настройки приложения
│           ├── application-etcd.properties              # Настройки для etcd профиля
│           └── application-inmemory.properties          # Настройки для inmemory профиля
├── docker-compose.yml                                   # Скрипт запуска etcd в Docker
├── pom.xml                                              # Зависимости Maven (jetcd, awaitility)
└── README.md                                            # Данная документация
```

---

# Как поднять всё и заставить работать

## Версия 1. Подробная (для первого запуска)

### Шаг 0. Что нужно установить заранее

* **JDK 17+** (`java -version`)
* **Maven** (`mvn -version`)
* **Docker + Docker Compose** (`docker --version`, `docker compose version`)
* **etcdctl** — для проверки и snapshot (опционально, но полезно)

Проверь, что порт **2379** свободен:
```bash
# Linux/Mac
lsof -i :2379
# Windows
netstat -ano | findstr 2379
```
Если занят — останови процесс или поменяй порт в `docker-compose.yml` и `application.properties`.

### Шаг 1. Собрать приложение

Из корня проекта:
```bash
mvn clean package
```
После успешной сборки появится `target/library-events-service-1.0.0.jar`.

Если сборка пападают — проверь версию JDK и наличие зависимостей в `pom.xml` (jetcd 0.8.6, awaitility).

### Шаг 2. Поднять etcd

```bash
docker-compose up -d
```

Проверить, что контейнер жив:
```bash
docker ps
# должен быть контейнер с etcd на порту 2379
```

Проверить, что etcd отвечает:
```bash
etcdctl endpoint health --endpoints=localhost:2379
# ожидаемо: localhost:2379 is healthy: successfully committed proposal
```

Если `etcdctl` не установлен — можно зайти внутрь контейнера:
```bash
docker exec -it <container_name> etcdctl endpoint health
```

### Шаг 3. Запустить приложение с профилем etcd

```bash
java -jar target/library-events-service-1.0.0.jar \
     --server.port=8081 \
     --spring.profiles.active=etcd
```

Проверить лог старта: не должно быть ошибок про `Connection refused` / `UNAVAILABLE` от jetcd. Если есть — etcd не поднялась или указан неверный адрес в `application.properties`.

### Шаг 4. Проверить, что всё работает end-to-end

Создай книгу-событие:
```bash
curl -X POST http://localhost:8081/events \
     -H "Content-Type: application/json" \
     -d '{"title":"Мастер и Маргарита","author":"Булгаков","copies":3}'
```

Посмотри, что реально лежит в etcd:
```bash
etcdctl get --prefix event:
```
Ты должен увидеть свой ключ `event:{uuid}` с JSON-значением. Если пусто — приложение работает не с той etcd или поднялось в `inmemory`.

Оформи заказ (проверка основного сценария):
```bash
curl -X POST http://localhost:8081/orders \
     -H "Content-Type: application/json" \
     -d '{"eventId":"<uuid из шага выше>","userId":42}'
```

Проверить счётчик просмотров (атомарный механизм):
```bash
curl http://localhost:8081/events/<uuid>
etcdctl get views:<uuid>
# значение должно увеличиваться с каждым GET
```

Проверить TTL (временная заявка):
```bash
etcdctl get --prefix temp:
# через N секунд после создания — ключ исчезает сам
```

### Шаг 5. Snapshot (сохранение/восстановление)

```bash
# Сохранить снимок
etcdctl snapshot save backup.db

# Восстановить (etcd должна быть остановлена)
docker-compose down
etcdctl snapshot restore backup.db --data-dir=/tmp/etcd-restore
```

### Шаг 6. Остановить всё

```bash
# Приложение — Ctrl+C в терминале, где оно запущено
docker-compose down
```

---

## Версия 2. Короткая (для тех, кто уже в теме)

```bash
# 1. Сборка
mvn clean package

# 2. etcd
docker-compose up -d
etcdctl endpoint health --endpoints=localhost:2379

# 3. Приложение с etcd
java -jar target/library-events-service-1.0.0.jar \
     --server.port=8081 \
     --spring.profiles.active=etcd

# 4. Проверка
curl -X POST localhost:8081/events -H "Content-Type: application/json" \
     -d '{"title":"Test","author":"A","copies":3}'
etcdctl get --prefix event:
```

Без etcd (только для быстрой проверки логики):
```bash
java -jar target/library-events-service-1.0.0.jar --server.port=8081
# профиль inmemory подхватится по умолчанию
```

---

## Частые проблемы

| Симптом | Причина | Что делать |
|---|---|---|
| `Connection refused` при старте | etcd не запущена | `docker-compose up -d`, проверить `docker ps` |
| `etcdctl get --prefix event:` пусто, а приложение отвечает 200 | поднялся профиль `inmemory` | добавить `--spring.profiles.active=etcd` |
| `docker-compose up` падает с «port is already allocated» | порт 2379 занят | освободить порт или поменять в `docker-compose.yml` |
| Приложение пишет в etcd, но `etcdctl` её не видит | разные endpoints | сверить адрес в `application.properties` и `docker-compose.yml` |
| Snapshot не восстанавливается | etcd ещё работает | сначала `docker-compose down` |

## Что где лежит (карта ключей в etcd)

```text
event:{uuid}       → JSON книги-события
manager:{uuid}     → JSON менеджера
order:{uuid}       → JSON заказа
temp:{uuid}        → JSON временной заявки (с lease, самоудаляется)
settings:{userId}  → JSON настроек пользователя
views:{eventId}    → "123" (строка с числом, атомарный инкремент)
```

Префиксы позволяют выбирать все объекты типа: `etcdctl get --prefix event:`.
