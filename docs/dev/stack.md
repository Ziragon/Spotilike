# Spotilike Пет-проект: стек и план приложения

# Этот документ описывает эволюцию архитектуры проекта от MVP до глобального Production-решения.

---

## Phase 1: MVP (Minimum Viable Product)
**Цель:** Работающий "скелет". Пользователь регистрируется, загружает трек, слушает трек.
**Инфраструктура:** Docker Compose (Single Node).

### Backend Core
*   **Language:** Java 17+ (Spring Boot 3) для основных сервисов.
*   **Audio Processing:** Python (FastAPI) + FFmpeg.
    *   *Почему:* Python лучше работает с обертками библиотек обработки медиа.
*   **API Gateway:** Spring Cloud Gateway.
    *   *Функции:* Простой роутинг, проверка JWT.
*   **Service Discovery:** Docker Internal DNS.
    *   *Суть:* Обращение к сервисам по именам контейнеров (`http://user-service:8080`).

### Data & Storage
*   **Database:** PostgreSQL (Single Instance).
    *   *Схема:* Database per Service (логическое разделение).
*   **Object Storage:**
    *   *Local Dev:* MinIO.
    *   *Staging/Prod:* Cloudflare R2 (S3 API).
    *   *Logic:* Хранение `.ogg` исходников и `.ts` чанков.
*   **Cache:** Redis (Single Node).
    *   *Использование:* Хранение сессий, Blacklist токенов.

### Async & Events
*   **Message Broker:** Apache Kafka.
    *   *Использование:* События `upload.completed`, `processing.finished`.

### Audio Pipeline (Simplified)
1.  Загрузка файла -> MinIO/R2.
2.  Kafka Event -> Worker скачивает файл в `/tmp` (Stateless, без NFS!).
3.  FFmpeg -> Транскодинг в HLS (Multi-bitrate).
4.  Заливка чанков обратно в MinIO/R2.
5.  API отдает Presigned URL на `master.m3u8`.

---

## Phase 2: User Experience & Observability
**Цель:** Превращение "движка" в "продукт". Удобство, поиск, стабильность.

### Features
*   **Search Engine:** MeiliSearch.
    *   *Функция:* Индексация названий треков/артистов (Fuzzy search). Синхронизация через Kafka.
*   **Auth Enhancements:**
    *   OAuth2 (Google/GitHub login).
    *   Email Service (SendGrid/SMTP) для подтверждения почты.
*   **WebSockets:**
    *   Уведомления: "Ваш трек обработан".
    *   Social: "Друг начал слушать..." (заготовка).

### DevOps & Monitoring
*   **Logging:** ELK Stack (Elasticsearch, Logstash, Kibana) или PLG (Prometheus, Loki, Grafana).
    *   *Суть:* Сбор логов со всех контейнеров в одно место.
*   **Metrics:** Spring Boot Actuator + Prometheus + Grafana.
    *   *Дашборды:* RPS, Memory usage, Kafka lag.
*   **CI/CD:** GitHub Actions.
    *   Автосборка Docker образов и пуш в GHCR (GitHub Container Registry).

---

## Phase 3: High Load & Resilience (Production Readiness)
**Цель:** Система держит нагрузку 10k+ пользователей и не падает от сбоев.

### Resilience Patterns (Resilience4j)
*   **Circuit Breaker:** Отключение запросов к "умершему" микросервису.
*   **Retry:** Умные повторы при сетевых морганиях.
*   **Rate Limiter:** Защита API от спама (Redis-based, Bucket4j).
*   **Bulkhead:** Изоляция пулов потоков (чтобы тормозящий сервис не положил весь Gateway).

### Data Scaling
*   **PostgreSQL Scaling:**
    *   Настройка Master-Slave репликации.
    *   `AbstractRoutingDataSource` в Spring для разделения: Write -> Master, Read -> Slave.
*   **Database Migration:** Liquibase/Flyway для управления версиями схем БД.

### Tracing
*   **Distributed Tracing:** OpenTelemetry + Zipkin/Jaeger.
    *   *Цель:* Видеть путь запроса сквозь все микросервисы (TraceID).

---

## Phase 4: Global Scale (Скорее всего нереализуем)
**Цель:** Снижение задержек для пользователей из разных стран. "Enterprise" фичи.

### Global Architecture
*   **Cloudflare DNS:** Geo-routing (направление пользователя на ближайший сервер).
*   **BFF (Backend for Frontend):** Отдельный сервис-агрегатор для Mobile/Web клиентов (GraphQL или Aggregation API).
*   **Multi-Region Data:**
    *   **Kafka MirrorMaker:** Репликация событий между дата-центрами (US -> EU).
    *   Локальные Redis в каждом регионе для Real-time фич.

### Security & Advanced Audio
*   **DRM:** Интеграция Widevine (или заглушка прокси) для защиты контента.
*   **Fingerprinting:** Анализ IP/DeviceID для защиты от ботов и накруток прослушиваний.
*   **Testing:**
    *   Chaos Engineering (LitmusChaos) - убиваем сервисы в проде.
    *   Load Testing (k6) - симуляция 10k пользователей.

---

## 📋 Сводная таблица технологий

| Категория | Технология | Этап внедрения | Комментарий |
| :--- | :--- | :--- | :--- |
| **Языки** | Java, Python | Phase 1 | Spring Boot / FastAPI |
| **БД** | PostgreSQL | Phase 1 | Сначала Single, потом Replicated |
| **Поиск** | MeiliSearch | Phase 2 | Легче и быстрее ElasticSearch |
| **Кеш/Lock** | Redis | Phase 1 | |
| **Брокер** | Kafka | Phase 1 | |
| **Storage** | MinIO / R2 | Phase 1 | MinIO для Dev, R2 для Prod |
| **Gateway** | Spring Cloud | Phase 1 | |
| **Discovery** | DNS / K8s | Phase 1 | Eureka не нужна |
| **Monitoring**| Grafana/Loki | Phase 2 | |
| **Tracing** | OpenTelemetry | Phase 3 | |
| **Deploy** | Docker/K8s | Phase 1/3 | |
