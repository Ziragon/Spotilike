# Spotilike Пет-проект: База данных (MVP)

# Описание схем баз данных для микросервисов.
**Принцип:** Database per Service. Связи между базами — логические (по ID), без физических Foreign Keys.

---

## 1. User Service Database (`user_db`)
**Ответственность:** Аутентификация, хранение учетных данных пользователей.
**Технология:** PostgreSQL

### Table: `users`
Базовая сущность пользователя (слушателя).

| Column | Type | Constraints | Description |
|:---|:---|:---|:---|
| `id` | UUID | PK, DEFAULT gen_random_uuid() | Уникальный идентификатор пользователя (sub в JWT) |
| `email` | VARCHAR(255) | UNIQUE, NOT NULL | Логин пользователя |
| `password_hash` | VARCHAR(255) | NOT NULL | Хеш пароля (BCrypt/Argon2) |
| `username` | VARCHAR(50) | UNIQUE, NOT NULL | Отображаемое имя пользователя (не артиста!) |
| `avatar_url` | VARCHAR(512) | NULL | Ссылка на аватар в хранилище (MinIO/R2) |
| `is_verified` | BOOLEAN | DEFAULT FALSE | Подтвержден ли email |
| `created_at` | TIMESTAMP | DEFAULT NOW() | Дата регистрации |
| `updated_at` | TIMESTAMP | DEFAULT NOW() | |

### Table: `roles`
Ролевая модель (RBAC).

| Column | Type | Constraints | Description |
|:---|:---|:---|:---|
| `id` | SERIAL | PK | |
| `name` | VARCHAR(20) | UNIQUE, NOT NULL | 'ROLE_USER', 'ROLE_ADMIN' |

### Table: `user_roles`
Связь пользователей и ролей.

| Column | Type | Constraints | Description |
|:---|:---|:---|:---|
| `user_id` | UUID | FK -> users.id | |
| `role_id` | INT | FK -> roles.id | |
| **PK** | (user_id, role_id) | | Составной первичный ключ |

### Table: `refresh_tokens`
Хранилище сессий. **Важно:** Храним хеш токена и IP адрес для безопасности.

| Column | Type | Constraints | Description |
|:---|:---|:---|:---|
| `id` | BIGSERIAL | PK | Уникальный ID записи |
| `user_id` | UUID | FK -> users.id | Владелец сессии |
| `token_hash` | VARCHAR(64) | UNIQUE, NOT NULL | SHA-256 хеш от Refresh токена (сам токен у клиента) |
| `ip_address` | INET | NOT NULL | IP адрес входа (поддерживает IPv4 и IPv6) |
| `device_info` | VARCHAR(255) | NULL | User-Agent (Chrome, iPhone) или Fingerprint |
| `expires_at` | TIMESTAMP | NOT NULL | Дата истечения срока жизни токена |
| `revoked` | BOOLEAN | DEFAULT FALSE | Флаг отзыва (True = сессия завершена/взломана) |
| `created_at` | TIMESTAMP | DEFAULT NOW() | Дата создания сессии |

---

## 2. Catalog Service Database (`catalog_db`)
**Ответственность:** Метаданные треков, альбомов, профили артистов, структура контента.
**Технология:** PostgreSQL

### Table: `artists`
Публичная страница исполнителя. Отделена от пользователя.

| Column | Type | Constraints | Description |
|:---|:---|:---|:---|
| `id` | UUID | PK, DEFAULT gen_random_uuid() | Публичный ID артиста |
| `name` | VARCHAR(100) | NOT NULL, INDEX | Сценическое имя (Eminem, Queen) |
| `bio` | TEXT | NULL | Биография |
| `avatar_url` | VARCHAR(512)| NULL | Ссылка на изображение в R2 |
| `is_verified` | BOOLEAN | DEFAULT FALSE | Галочка верификации |

### Table: `artist_managers` (CRITICAL)
**Связь:** Позволяет пользователю (`user_db`) управлять артистом (`catalog_db`).

| Column | Type | Constraints | Description |
|:---|:---|:---|:---|
| `id` | BIGSERIAL | PK | |
| `artist_id` | UUID | FK -> artists.id | Артист, которым управляют |
| `user_id` | UUID | NOT NULL, INDEX | ID из `user_db`. Логическая связь. |
| `permissions` | JSONB | DEFAULT '["ALL"]' | Права: `["UPLOAD", "EDIT_PROFILE"]` |
| `created_at` | TIMESTAMP | DEFAULT NOW() | |

### Table: `albums`
| Column | Type | Constraints | Description |
|:---|:---|:---|:---|
| `id` | UUID | PK, DEFAULT gen_random_uuid() | |
| `title` | VARCHAR(150) | NOT NULL | Название альбома |
| `release_date` | DATE | NOT NULL | Дата выхода |
| `cover_url` | VARCHAR(512) | NOT NULL | Обложка альбома |
| `type` | VARCHAR(20) | DEFAULT 'ALBUM' | 'SINGLE', 'EP', 'ALBUM' |

### Table: `tracks`
| Column | Type | Constraints | Description |
|:---|:---|:---|:---|
| `id` | UUID | PK, DEFAULT gen_random_uuid() | |
| `album_id` | UUID | FK -> albums.id | Привязка к альбому |
| `title` | VARCHAR(150) | NOT NULL | Название трека |
| `duration_sec` | INT | NOT NULL | Длительность |
| `track_number` | INT | NOT NULL | Позиция в альбоме |
| `file_key` | VARCHAR(255) | UNIQUE, NOT NULL | Путь к папке HLS в R2 |
| `is_available` | BOOLEAN | DEFAULT FALSE | True, когда обработка FFmpeg завершена |

### Table: `track_artists` (Many-to-Many)
**Связь:** Реализует фиты (Feat). Один трек — много артистов.

| Column | Type | Constraints | Description |
|:---|:---|:---|:---|
| `track_id` | UUID | FK -> tracks.id | |
| `artist_id` | UUID | FK -> artists.id | |
| `artist_type` | VARCHAR(20) | DEFAULT 'MAIN' | 'MAIN', 'FEATURED', 'REMIXER' |
| `display_order`| INT | DEFAULT 1 | Порядок отображения имен |
| **PK** | (track_id, artist_id) | | |

### Table: `genres`
| Column | Type | Constraints | Description |
|:---|:---|:---|:---|
| `id` | SERIAL | PK | |
| `name` | VARCHAR(50) | UNIQUE | 'Rock', 'Hip-Hop' |

### Table: `track_genres`
| Column | Type | Constraints | Description |
|:---|:---|:---|:---|
| `track_id` | UUID | FK -> tracks.id | |
| `genre_id` | INT | FK -> genres.id | |
| **PK** | (track_id, genre_id) | | |

---

## 3. Audio Processing Database (`processing_db`)
**Ответственность:** Очередь задач конвертации, идемпотентность воркеров.
**Технология:** PostgreSQL

### Table: `processing_jobs`
| Column | Type | Constraints | Description |
|:---|:---|:---|:---|
| `id` | UUID | PK | ID задачи (Correlation ID) |
| `track_id` | UUID | NOT NULL | Ссылка на трек в `catalog_db` |
| `source_bucket`| VARCHAR(100) | NOT NULL | Бакет R2 с исходником |
| `source_key` | VARCHAR(255) | NOT NULL | Путь к исходному OGG файлу |
| `status` | VARCHAR(20) | NOT NULL | 'PENDING', 'PROCESSING', 'COMPLETED', 'FAILED' |
| `worker_pod` | VARCHAR(100) | NULL | ID пода/контейнера, взявшего задачу (для отладки) |
| `error_log` | TEXT | NULL | Вывод ошибки FFmpeg |
| `created_at` | TIMESTAMP | DEFAULT NOW() | |
| `updated_at` | TIMESTAMP | DEFAULT NOW() | |

---

## 4. Streaming Service Database (`streaming_db`)
**Ответственность:** История прослушиваний, аналитика для выплат (в будущем).
**Технология:** PostgreSQL (в будущем TimescaleDB)

### Table: `playback_history`
Журнал событий (Append-only).

| Column | Type | Constraints | Description |
|:---|:---|:---|:---|
| `id` | BIGSERIAL | PK | |
| `user_id` | UUID | NOT NULL | Кто слушал (из `user_db`) |
| `track_id` | UUID | NOT NULL | Что слушал (из `catalog_db`) |
| `listened_sec` | INT | NOT NULL | Сколько секунд прослушал |
| `timestamp` | TIMESTAMP | DEFAULT NOW() | Время начала |
| `ip_address` | INET | NULL | Для защиты от накруток |
| `platform` | VARCHAR(20) | NULL | 'WEB', 'IOS', 'ANDROID' |

---

## Схема связей (Logical ER Diagram)

```mermaid
erDiagram
    %% USER CONTEXT
    USERS ||--o{ ARTIST_MANAGERS : "manages"
    
    %% CATALOG CONTEXT
    ARTISTS ||--o{ ARTIST_MANAGERS : "managed by"
    ARTISTS ||--o{ ALBUMS : "releases"
    ALBUMS ||--|{ TRACKS : "contains"
    TRACKS }|--|{ TRACK_ARTISTS : "performed by"
    ARTISTS }|--|{ TRACK_ARTISTS : "performs"
    TRACKS }|--|{ TRACK_GENRES : "classified as"
    GENRES }|--|{ TRACK_GENRES : "classifies"

    %% PROCESSING CONTEXT
    TRACKS ||--|| PROCESSING_JOBS : "triggers"
    
    %% STREAMING CONTEXT
    USERS ||--o{ PLAYBACK_HISTORY : "listens"
    TRACKS ||--o{ PLAYBACK_HISTORY : "is listened"