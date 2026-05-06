# 🚀 Curious Sync

Built with a stack leveraging **Spring Boot 4**, **Kafka**, and **Redis**.

---

## 🏗️ Architecture Overview

The system follows a reactive producer-consumer pattern to decouple API requests from database persistence:

1.  **Ingestion Layer**: `LikesController` receives reaction events and immediately hands them off to Kafka.
2.  **Streaming Layer**: **Apache Kafka** acts as the high-throughput message broker.
3.  **Caching Layer**: **Redis** provides a high-speed state cache (`LikeStateCache`) to coalesce rapid-fire updates from the same user.
4.  **Processing Layer**: `LikesBatchProcessor` consumes events in large windows and performs bulk database operations.
5.  **Persistence Layer**: **PostgreSQL** stores the final state.

---

## 🛠️ Technology Stack

- **Backend**: Java 21 / Spring Boot 4.0.5
- **Message Broker**: Apache Kafka
- **Caching**: Redis
- **Database**: PostgreSQL
- **Build Tool**: Gradle
- **Utilities**: Node.js
- **Testing**: k6

---

## 🚀 Getting Started

### Prerequisites

- **Java 21**
- **Redis Cloud Acconut**
- **Kafak locally installed**
- **Postgres locally installed**
- **Node.js** (for auxiliary scripts)

### Configuration

Create a `.env` file in the root directory based on `.env.example`:

```bash
cp .env.example .env
```

After configuring the .env file
```npm i```

### Prerequisite Setup
- use the commands specified in kafka-commands.txt to setup the kafka
- setup redis cloud account
- setup postgres locally

### Running the Application

```bash
./gradlew bootRun
```

---

## 🧪 Testing & Utilities

### Database Seeding
Populate your environment with millions of users and posts:
```bash
node scripts/seed-users.js
node scripts/seed-posts.js
```

### Load Testing (k6)
Simulate traffic using the provided k6 scripts:
```bash
# spam likes
k6 run k6/spamLikes.js
```

---

## 📂 Project Structure

- `src/main/java/curious/sync/`
    - `configurations/`: Infrastructure setup (Kafka, Redis, Request Coalescing).
    - `controllers/`: REST API endpoints.
    - `services/`: Core logic, including Kafka producers and batch processors.
    - `models/`: JPA entities.
- `scripts/`: Data management and seeding utilities.
- `k6/`: Load testing scenarios.

---
