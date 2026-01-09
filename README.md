# Spotilike

# Spotilike Backend

> Scalable Microservices Audio Streaming Platform

![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3-6DB33F?style=flat&logo=spring&logoColor=white)
![Kafka](https://img.shields.io/badge/Apache_Kafka-231F20?style=flat&logo=apachekafka&logoColor=white)
![Postgres](https://img.shields.io/badge/PostgreSQL-15-336791?style=flat&logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat&logo=docker&logoColor=white)

**Spotilike** — это пет-проект, реализующий архитектуру стримингового сервиса (аналог Spotify). Проект фокусируется на построении отказоустойчивой микросервисной архитектуры, работе с потоковым аудио (HLS) и высокими нагрузками.

---

## Документация

Вся техническая информация вынесена в отдельные файлы для удобства навигации:

| Документ | Описание |
| :--- | :--- |
| **[Roadmap](docs/dev/roadmapMVP.md)** | План разработки до MVP |
| **[Архитектура](docs/dev/architecture.md)** | Описание микросервисов и их ответственности. |
| **[Стек и Фазы](docs/dev/stack.md)** | Детальный список технологий и этапы их внедрения. |
| **[База Данных](docs/dev/databaseMVP.md)** | Схемы таблиц (User, Catalog, Streaming). |
| **[Визуализация БД](docs/dev/spotilike_diagrammMVP.png)** | MVP Диаграмма базы данных и таблиц |

---

## Быстрый старт (Local Dev)

Для запуска инфраструктуры (БД, брокеры, S3) не нужно ничего устанавливать, кроме Docker.

### Предварительные требования
*   Docker & Docker Compose
*   Java 17+ (для запуска кода в IDE)

### Запуск
1. **Клонируйте репозиторий:**
   ```bash
   git clone https://github.com/your-username/spotilike-backend.git
   cd spotilike-backend