# Spotilike Пет-проект: Roadmap (MVP)

# Этот документ описывает поэтапный план разработки backend-системы для стриминга музыки.

---

## Phase 0: Инфраструктура и Среда (Local Env)
**Цель:** Подготовить почву. Никакой бизнес-логики, только "трубы" и контейнеры.

### Задачи
- [ ] **Docker Compose:**
    - [ ] PostgreSQL (с volume для данных).
    - [ ] Redis (для кеша).
    - [ ] Kafka + Zookeeper (или Kraft mode).
    - [ ] **MinIO** (Локальная замена S3/Cloudflare R2).
- [ ] **Project Shells:** Сгенерировать пустые Spring Boot/Python проекты для микросервисов (`gateway`, `user`, `catalog`, `processor`).

### Definition of Done (DoD)
1. Команда `docker-compose up -d` поднимает все инфраструктурные контейнеры без ошибок.
2. Доступна админка MinIO (`localhost:9001`) через браузер.
3. Можно подключиться к Postgres через DBeaver и видеть созданные пустые базы данных.

---

## Phase 1: Gateway & Identity (User Service)
**Цель:** Реализовать вход в систему. Без этого нет смысла делать остальное.

### Задачи
- [ ] **User Service:**
    - [ ] Подключить PostgreSQL + Flyway/Liquibase и Hibernate.
    - [ ] Создать таблицы `users`, `roles`, `refresh_tokens`.
    - [ ] Реализовать API: Registration, Login (Return Access + Refresh Tokens).
    - [ ] Реализовать валидацию токенов (Public Key endpoint).
- [ ] **Gateway Service:**
    - [ ] Настроить Spring Cloud Gateway.
    - [ ] Реализовать `GlobalFilter` для проверки JWT в заголовке `Authorization`.
    - [ ] Настроить роутинг запросов (`/auth/**` -> User Service, `/users/**` -> User Service).

### Definition of Done (DoD)
1. Postman: `POST /auth/register` создает пользователя в БД.
2. Postman: `POST /auth/login` возвращает JWT.
3. Postman: `GET /users/me` (с токеном) возвращает профиль.
4. Postman: `GET /users/me` (без токена) возвращает 401 Unauthorized от Gateway.

---

## Phase 2: Catalog Service (Core Domain)
**Цель:** Реализовать структуру метаданных (Артисты, Альбомы, Треки).
**Важно:** Разделение понятий User и Artist (см. схему БД).

### Задачи
- [ ] **Database Schema:** Реализовать таблицы `artists`, `artist_managers`, `albums`, `tracks`, `track_artists`.
- [ ] **Artist API:**
    - [ ] "Стать артистом" (создание профиля + привязка менеджера).
    - [ ] Редактирование био/аватара (проверка прав менеджера).
- [ ] **Content API:**
    - [ ] Создание Альбома.
    - [ ] Создание метаданных Трека (статус `NO_FILE`).
- [ ] **Integration:** Сервис должен извлекать `userId` из JWT заголовка, проброшенного Гейтвеем.

### Definition of Done (DoD)
1. Пользователь может создать профиль Артиста.
2. Пользователь может создать Альбом и добавить в него Трек (только название, без аудио).
3. Попытка редактировать чужого артиста возвращает 403 Forbidden.

---

## Phase 3: Audio Processing Pipeline (Hardcore)
**Цель:** Загрузка файла, транскодинг в HLS, сохранение.

### Задачи
- [ ] **Upload Flow (Catalog Service):**
    - [ ] Endpoint `POST /tracks/{id}/upload`.
    - [ ] Загрузка файла в MinIO (bucket `raw`).
    - [ ] Отправка события в Kafka: `topic: upload.completed` (payload: `{trackId, fileKey}`).
- [ ] **Processing Service (Worker):**
    - [ ] Kafka Consumer для `upload.completed`.
    - [ ] Скачивание файла из MinIO во временную директорию.
    - [ ] **FFmpeg:** Конвертация OGG/MP3 -> HLS (.m3u8 + .ts chunks).
    - [ ] Загрузка результата в MinIO (bucket `public`).
    - [ ] Отправка события: `topic: processing.completed`.
- [ ] **State Update:**
    - [ ] Catalog Service слушает `processing.completed`.
    - [ ] Обновляет статус трека: `is_available = true` и `file_key`.

### Definition of Done (DoD)
1. Загружаем `.mp3` файл через API.
2. Через ~30-60 секунд в MinIO бакете `public` появляется папка с `.ts` сегментами.
3. В базе данных у трека меняется статус на доступный.

---

## Phase 4: Streaming Service
**Цель:** Доставка контента пользователю.

### Задачи
- [ ] **Endpoint:** `GET /stream/{trackId}`.
- [ ] **Security:** Проверка, имеет ли пользователь право слушать (валидация подписки - заглушка).
- [ ] **Presigned URLs:** Генерация временной ссылки на `master.m3u8` файл в MinIO/S3.
- [ ] **Playback History:** Асинхронная запись факта прослушивания в БД (или отправка события в Kafka).

### Definition of Done (DoD)
1. Запрос к API возвращает длинную ссылку на MinIO.
2. Эту ссылку можно вставить в VLC Player или Safari Browser и музыка играет.
3. Переключение качества (если реализовано в FFmpeg) работает.

---

## Tech Stack Summary

| Component | Technology | Purpose |
| :--- | :--- | :--- |
| **Language** | Java 17/21 (Spring Boot 3) | User, Catalog, Gateway |
| **Processing** | Python (FastAPI) or Java | FFmpeg wrapping |
| **Database** | PostgreSQL 15+ | Relational Data |
| **Message Broker** | Kafka | Async communication |
| **Cache** | Redis | Sessions, Rate Limiting |
| **Storage** | MinIO (Dev) / Cloudflare R2 | Object Storage (Audio) |
| **Discovery** | DNS / Docker Network | Simple Service Discovery |