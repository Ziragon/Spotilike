# Spotilike Пет-проект: Микросервисная Архитектура

# Это техническая документация архитектуры Spotify-like проекта. Файл содержит:
- Полный список микросервисов с их ответственностью
- Рекомендации по языкам (Java, Python, Go и т.д.)
- Чеклист для продакшена

---

## MVP Микросервисы (5 минимальных)

### 1. **Gateway Service**
**Статус:** Обязателен для MVP
**Язык:** Java (Spring Cloud)

**Что входит:**
- JWT валидация (stateless, public key из User Service)
- Rate Limiting (Redis-based, 100 req/min per IP)
- CORS configuration
- Circuit Breaker (Resilience4j)
- Routing ко всем микросервисам
- Load Balancing (Eureka integration)
- Timeout & Retry policies (централизованно)

**Пояснение:** Единая точка входа, безопасность.

---

### 2. **User Service**
**Статус:** Обязателен для MVP  
**Язык:** Java (Spring Boot 3 + Spring Security)

**Что входит:**
- User CRUD (регистрация, профиль, настройки)
- JWT generation & validation (RSA256: private/public keys)
- OAuth2 integration (Google, GitHub)
- Password reset flow (email)
- Fingerprinting & IP-based bot detection
- Stripe Customer creation (без подписки в MVP)
- Role management: USER, ARTIST, ADMIN

**БД:** PostgreSQL (основная)  
**Кеш:** Redis (сессии, access token blacklist)  

**Пояснение:** Без авторизации нет персонализации и защиты контента.

---

### 3. **Catalog Service**
**Статус:** Обязателен для MVP  
**Язык:** Java (Spring Boot) 

**Что входит:**
- Artist CRUD (админ-эндпоинты)
- Album CRUD с валидацией метаданных
- Track CRUD с привязкой к альбому
- Genre management
- Базовый поиск по точному совпадению
- Валидация: duplicate detection по названию + артисту
- Kafka Producer: `catalog.track.created`, `catalog.track.deleted`

**БД:** PostgreSQL (Master-Slave через AbstractRoutingDataSource)  
**Кеш:** Redis (TTL 5 минут для часто запрашиваемых альбомов)  

**Пояснение:** Основа — без каталога треков нет что слушать.

---

### 4. **Audio Processing Service**
**Статус:** Обязателен для MVP  
**Язык:** Python (FastAPI + Celery) или Go

**Что входит:**
- Kafka Consumer: `upload.completed` events
- Загрузка OGG из Cloudflare R2 -> NFS-share
- FFmpeg pipeline: OGG -> 3 битрейта (96k, 160k, 320k) → HLS chunks (.ts)
- Генерация .m3u8 master playlists
- Экстракция метаданных (duration, peaks для waveформы)
- Загрузка результатов обратно в R2
- Cleanup worker (удаляет временные файлы раз в час)
- REST API: GET `/processing/status/{trackId}`

**Инфраструктура:** 
- NFS-server (общий volume между workers)
- Celery workers (4-8 concurrency per pod)
- FFmpeg с GPU acceleration (опционально)

**БД:** PostgreSQL (jobs table)  
**Кафка:** Consumer + Producer (`processing.completed`)  

**Пояснение:** Без обработки загруженные треки останутся неиграбельными.

---

### 5. **Streaming Service**
**Статус:** Обязателен для MVP  
**Язык:** Java (Spring WebFlux) или Go 

**Что входит:**
- GET `/stream/{trackId}/master.m3u8` — валидация JWT + права
- GET `/stream/{trackId}/{quality}/{segment}.ts` — проксирование с R2
- Подписанные URL к R2 (TTL 5 минут, кеш в Redis)
- Playback event producer: `playback.started`, `playback.ended`
- IP-based rate limit: 50 запросов/сек на user
- Range Requests поддержка (для скачивания)
- HLS bitrate switching (клиент сам выбирает)

**БД:** PostgreSQL (только для проверки прав)  
**Кеш:** Redis (signed URLs)  

**Пояснение:** Суть приложения — воспроизведение музыки.

---

## Постепенное внедрение (Phase 2-4)

### Phase 2: User Experience (после 1-2 месяцев)

#### 6. **Playlist Service**
**Статус:** Phase 2  
**Язык:** Java (Spring Boot)

**Что входит:**
- CRUD плейлистов (приватные/публичные)
- Добавление/удаление треков
- Лайк/дизлайк треков (`user_liked_tracks` таблица)
- User Library ("Моя музыка") — агрегирует лайки
- Kafka consumer: `catalog.track.deleted` (чтобы удалить из плейлистов)

**БД:** PostgreSQL  
**Кеш:** Redis (кешировать состав публичных плейлистов)  

---

#### 7. **Search Service**
**Статус:** Phase 2  
**Язык:** Go (Gin) или Python (FastAPI)

**Что входит:**
- Полнотекстовый поиск по MeiliSearch
- Автодополнение (suggestions, 3 символа минимум)
- Фильтры: по жанру, году, артисту
- Fuzzy search (опечатки)
- Кеширование популярных запросов в Redis (TTL 10 минут)
- Kafka consumer: индексация новых треков 

---

### Phase 3: Social & Real-time (после 3-4 месяцев)

#### 8. **Social & Activity Service**
**Статус:** Phase 3  
**Язык:** Go (Gorilla WebSocket)   

**Что входит:**
- WebSocket endpoint: `/ws/social` (JWT auth handshake)
- "Сейчас слушает" (real-time статус)
- Подписки на артистов/пользователей
- Activity Feed (недавнее от друзей)
- Kafka consumer: `playback.started` (для трансляции статуса)
- Redis pub/sub → WebSocket broadcast

**Особенности:**
- Horizontal scaling: Redis хранит socket sessions

---

#### 9. **Analytics & History Service**
**Статус:** Phase 3  
**Рекомендованный язык:** Go или Java Spring

**Что входит:**
- Kafka consumer: `playback.*`, `user.*` events
- Хранение в PostgreSQL с TimescaleDB (time-series)
- Эндпоинты: 
  - `/analytics/user/history` (личная история)
  - `/analytics/artist/stats` (для артистов: прослушивания)
- GDPR compliance: export/delete user data
- Batch inserts (1000 events) для снижения нагрузки

**БД:** PostgreSQL (TimescaleDB extension)  
**Кеш:** Нет — данные всегда свежие  

---

### Phase 4: ML и масштаб (после 6+ месяцев)

#### 10. **Recommendation Service**
**Статус:** Phase 4  
**Рекомендованный язык:** Python (FastAPI)  
**Альтернативы:** Нет — ML без Python = самоубийство  

**Что входит:**
- "Discover Weekly" алгоритм (collaborative filtering)
- "Radio" на основе сид-трека
- "Because you listened to..." (content-based)
- Kafka consumer: `playback.ended` (история)
- Batch training раз в неделю
- Redis: кеш готовых рекомендаций (TTL 24 часа)

---

#### 11. **Notification Service**
**Статус:** Phase 4  
**Язык:** Java (Spring Boot)

**Что входит:**
- Email: SendGrid/AWS SES integration
- Воркеры: password reset, payment reminders
- Kafka consumer: `user.registered`, `subscription.failed`
- Templates (Thymeleaf)
- Retry logic с exponential backoff
- Dead-letter queue для failed emails

**БД:** PostgreSQL (email queue)
---

#### 12. **BFF (Backend for Frontend)**
**Статус:** Опционально, Phase 4  
**Язык:** Go (Gin)

**Что входит:**
- GraphQL endpoint: `/graphql` или REST aggregation
- Parallel fetching от сервисов
- Client-specific responses (Web vs Mobile)
- Batch запросы (DataLoader pattern)
- Rate limiting per client type

**Когда нужен:** 
- 3+ клиента (Web, iOS, Android) с разными требованиями
- Frontend жалуется на "chattiness" API

---

### Phase 5: Инфраструктура и DevOps

#### 13. **Config Server**
**Статус:** Phase 2+  
**Язык:** Java (Spring Cloud Config)  
**Задача:** Централизованная конфигурация всех сервисов

#### 14. **Eureka Server**
**Статус:** Phase 2+  
**Язык:** Java  
**Задача:** Service Discovery для микросервисов

#### 15. **Prometheus + Grafana + Loki**
**Статус:** Phase 1 (сразу после MVP)  
**Задача:** Метрики, логи, трейсы