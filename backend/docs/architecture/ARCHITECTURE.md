# Minishop — Architecture & Roadmap

## System Context

```

                          ┌──────────┐
                          │  Client  │
                          │ (Browser/│
                          │  Mobile) │
                          └────┬─────┘
                               │ HTTPS
                               ▼
                        ┌─────────────┐
                        │ API Gateway │
                        │   :8080     │
                        └──────┬──────┘
                               │ JWT validation, routing, rate limiting
               ┌───────────────┼───────────────┐
               ▼               ▼               ▼
        ┌────────────┐  ┌────────────┐  ┌─────────────┐
        │   User     │  │  Product   │  │   Order     │
        │  Service   │  │  Service   │  │  Service    │
        │   :8081    │  │   :8082    │  │   :8083     │
        └─────┬──────┘  └─────┬──────┘  └──┬──────┬──┘
              │               │             │      │
              ▼               ▼             │      │ Kafka
         ┌─────────┐    ┌─────────┐        │      │ (order-created)
         │ user_db │    │product_db│        │      │
         └─────────┘    │ + Redis  │        │      ▼
                        └─────────┘        │  ┌──────────────┐
                                           │  │  Inventory   │
                                           │  │  Service     │
                                           │  │   :8084      │
                                           │  └──────┬───────┘
                                           │         │
                                           ▼         ▼
                                      ┌─────────┐ ┌──────────────┐
                                      │order_db │ │inventory_db  │
                                      └─────────┘ └──────────────┘
```

## Services

| Service | Port | Database | Responsibilities |
|---------|------|----------|-----------------|
| **api-gateway** | 8080 | — | Route requests, validate JWT, rate limit, circuit breaker fallbacks |
| **user-service** | 8081 | user_db | Registration, login, JWT issuing, user profiles, role management |
| **product-service** | 8082 | product_db | Product CRUD, categories, search, Redis caching for hot data |
| **order-service** | 8083 | order_db | Order placement, status tracking, publishes `order-created` events |
| **inventory-service** | 8084 | inventory_db | Stock levels, reservations, consumes `order-created` events |

## Communication Patterns

### Synchronous (OpenFeign + REST)

```
order-service ──Feign──▶ product-service    (validate product exists, get price)
order-service ──Feign──▶ inventory-service  (check stock availability)
```

All Feign calls are wrapped with **Resilience4j** circuit breaker + retry + rate limiter.

### Asynchronous (Kafka)

```
order-service ──publish──▶ [order-created] ──consume──▶ inventory-service
                            (Kafka topic)                (reserve stock)
```

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 3.3.5 |
| Gateway | Spring Cloud Gateway (reactive) |
| REST clients | Spring Cloud OpenFeign |
| Messaging | Apache Kafka (Confluent 7.7) |
| Databases | PostgreSQL 16 (one per service) |
| Migrations | Flyway |
| Caching | Redis 7 |
| Security | JWT (jjwt 0.12.6), Spring Security |
| Resilience | Resilience4j (circuit breaker, retry, rate limiter) |
| Mapping | MapStruct 1.5.5 |
| API docs | SpringDoc OpenAPI 2.6 |
| Observability | Actuator + Micrometer → Prometheus → Grafana |
| Testing | JUnit 5, Testcontainers |
| Build | Maven 3.9 (wrapper included) |
| Containers | Docker Compose |

## Key Data Flows

### 1. User Registration & Login

```
Client ──POST /api/auth/register──▶ Gateway ──▶ user-service
                                                  │
                                                  ├─ validate input
                                                  ├─ hash password (BCrypt)
                                                  ├─ save to user_db
                                                  └─ return 201

Client ──POST /api/auth/login──▶ Gateway ──▶ user-service
                                                │
                                                ├─ verify credentials
                                                ├─ generate JWT (access + refresh)
                                                └─ return tokens
```

### 2. Place Order (the most complex flow)

```
Client ──POST /api/orders──▶ Gateway (JWT validated)
                                │
                                ▼
                          order-service
                                │
                     ┌──────────┼──────────┐
                     ▼                     ▼
              product-service       inventory-service
              (Feign: get price)    (Feign: check stock)
                     │                     │
                     └──────────┬──────────┘
                                │
                          order-service
                                │
                                ├─ create order (status: PENDING)
                                ├─ save to order_db
                                ├─ publish OrderCreatedEvent → Kafka
                                └─ return 201
                                            │
                                            ▼ (async)
                                      inventory-service
                                            │
                                            ├─ consume event
                                            ├─ reserve stock
                                            └─ update inventory_db
```

### 3. Browse Products (cached)

```
Client ──GET /api/products/:id──▶ Gateway ──▶ product-service
                                                    │
                                                    ├─ check Redis cache
                                                    │    ├─ HIT → return cached
                                                    │    └─ MISS ↓
                                                    ├─ query product_db
                                                    ├─ write to Redis (TTL)
                                                    └─ return product
```

## Project Structure

```
minishop/
├── pom.xml                        parent POM (BOM, plugin management)
├── docker-compose.yml             all infrastructure
├── mvnw / mvnw.cmd               Maven Wrapper
│
├── common/                        shared library (no Spring Boot main)
│   └── src/main/java/.../common/
│       ├── dto/                   ApiResponse, PagedResponse, ErrorResponse
│       ├── exception/             base exceptions, global handler
│       ├── security/              JwtUtil, Role enum, constants
│       └── util/                  shared utilities
│
├── api-gateway/                   reactive gateway
│   └── .../gateway/
│       ├── config/                route definitions, CORS
│       ├── filter/                JWT authentication filter
│       └── fallback/              circuit breaker fallback controllers
│
├── user-service/
│   └── .../user/
│       ├── config/                SecurityConfig, password encoder
│       ├── controller/            AuthController, UserController
│       ├── entity/                User, Role
│       ├── security/              JwtProvider, UserDetailsServiceImpl
│       ├── service/               AuthService, UserService
│       └── ...                    dto, mapper, repository, exception
│
├── product-service/
│   └── .../product/
│       ├── config/                RedisConfig, OpenApiConfig
│       ├── entity/                Product, Category
│       └── ...                    controller, service, etc.
│
├── order-service/
│   └── .../order/
│       ├── event/                 OrderCreatedEvent (Kafka payload)
│       ├── feign/                 ProductClient, InventoryClient
│       └── ...                    controller, service, entity, etc.
│
├── inventory-service/
│   └── .../inventory/
│       ├── event/                 OrderCreatedEventListener (Kafka)
│       └── ...                    controller, service, entity, etc.
│
├── docker/
│   ├── postgres/init.sql          creates 4 databases
│   ├── prometheus/prometheus.yml  scrape config for all services
│   └── grafana/provisioning/      auto-configured datasources
│
└── docs/
    ├── architecture/              this file, ADRs
    └── api/                       exported OpenAPI specs
```

## Security Model

```
┌─────────────────────────────────────────────────────────────┐
│                        API Gateway                          │
│                                                             │
│  1. Receive request                                         │
│  2. Check if route is public (/auth/login, /auth/register)  │
│     ├─ YES → forward without JWT check                      │
│     └─ NO  → extract & validate JWT                         │
│              ├─ INVALID → 401 Unauthorized                  │
│              └─ VALID   → add X-User-Id, X-User-Role        │
│                           headers, forward to service        │
│                                                             │
│  Internal service-to-service calls carry a shared secret    │
│  (X-Internal-Token header) to prevent direct access         │
│  bypassing the gateway.                                     │
└─────────────────────────────────────────────────────────────┘

Roles: USER, ADMIN
Token: JWT (HS256), issued by user-service
Propagation: Gateway validates → injects headers → services authorize by role
```

### Token Lifecycle (Refresh Token Rotation)

```
Client ──POST /auth/login──▶ user-service
                                │
                                └─ returns: { accessToken (15min), refreshToken (7d) }

Client ──POST /auth/refresh──▶ user-service
                                │
                                ├─ validate refresh token (exists in DB, not revoked)
                                ├─ issue NEW access token + NEW refresh token
                                ├─ revoke the OLD refresh token (one-time use)
                                └─ return new token pair

If a revoked refresh token is reused → revoke ALL tokens for that user (breach detected)
```

### Internal Service Authentication

```
┌──────────────┐                    ┌──────────────┐
│ order-service │──Feign call──▶    │ inventory-   │
│              │  X-Internal-Token  │   service    │
│              │  (shared secret)   │              │
└──────────────┘                    └──────┬───────┘
                                          │
                                   verify X-Internal-Token
                                   matches expected secret
                                          │
                                   ├─ VALID → process request
                                   └─ INVALID → 403 Forbidden

Secret is injected via environment variable, same across all services.
Prevents external clients from calling services directly (bypassing gateway).
```

## Reliability Patterns

### Transactional Outbox (Order → Kafka)

```
order-service
    │
    │ BEGIN TRANSACTION
    │   ├─ INSERT INTO orders (...)
    │   ├─ INSERT INTO outbox_events (topic, payload, status='PENDING')
    │ COMMIT
    │
    │ (no direct Kafka publish inside the transaction)
    │
    ▼
OutboxPoller (scheduled, every 500ms)
    │
    ├─ SELECT * FROM outbox_events WHERE status='PENDING'
    ├─ publish each event to Kafka
    ├─ UPDATE outbox_events SET status='SENT'
    └─ (if Kafka is down, events stay PENDING — retried next poll)

Guarantees: no event is lost even if Kafka is temporarily unavailable.
Solves the dual-write problem (DB + Kafka can't both commit atomically).
```

### Idempotency Keys (Order Creation)

```
Client ──POST /api/orders──▶ order-service
         Header: Idempotency-Key: <uuid>
                                    │
                                    ├─ check: does this key exist in DB?
                                    │   ├─ YES → return the cached response (no re-processing)
                                    │   └─ NO  → process order, store key + response
                                    └─ return result

Prevents duplicate orders from retries, network issues, or double-clicks.
```

---

## Roadmap

### Phase 1 — Foundation (Weeks 1–3) `← current`
- [x] Project structure and Maven multi-module setup
- [x] Docker Compose (Postgres, Redis, Kafka, Prometheus, Grafana)
- [ ] Common module (DTOs, exceptions, JWT utility)
- [ ] user-service with full JWT auth (register, login, profile)
- [ ] Refresh token rotation (access + refresh token pair, one-time use)
- [ ] API Gateway with JWT filter and route config
- [ ] Internal service authentication (shared secret via X-Internal-Token)
- [ ] First Flyway migrations
- [ ] Basic Actuator + Prometheus metrics exposed

### Phase 2 — Core Services (Weeks 4–6)
- [ ] product-service CRUD + category management
- [ ] Redis caching for product reads
- [ ] inventory-service with stock management
- [ ] Kafka integration via transactional outbox pattern (order-created → inventory-update)
- [ ] order-service with Feign calls to product + inventory
- [ ] Resilience4j circuit breaker on all Feign clients
- [ ] SpringDoc OpenAPI per service

### Phase 3 — Hardening (Weeks 7–8)
- [ ] Role-based access control (ADMIN vs USER endpoints)
- [ ] Input validation and consistent error responses
- [ ] Pagination and sorting on list endpoints
- [ ] Product search (by name, category, price range)
- [ ] Order status workflow (PENDING → CONFIRMED → SHIPPED → DELIVERED)
- [ ] Idempotency keys for order creation (prevents duplicate orders)

### Phase 4 — Testing (Weeks 9–10)
- [ ] Unit tests for all service layers (JUnit 5 + Mockito)
- [ ] Integration tests with Testcontainers (Postgres, Kafka)
- [ ] API tests for critical flows (register → login → order)
- [ ] Contract tests for Feign clients
- [ ] Target: 70%+ line coverage

### Phase 5 — Observability & Polish (Weeks 11–12)
- [ ] Grafana dashboards (request rate, latency, error rate, JVM)
- [ ] Structured logging (JSON) with correlation IDs across services
- [ ] Dockerfile per service (multi-stage builds)
- [ ] GitHub Actions CI pipeline (build + test)
- [ ] README with setup guide, screenshots, architecture diagrams
- [ ] API documentation aggregated at gateway

### Future / Stretch Goals
- [ ] Notification service (email confirmation via Kafka)
- [ ] Payment service mock (Stripe-like webhook flow)
- [ ] Kubernetes manifests (Helm charts)
- [ ] Service discovery (Spring Cloud Consul or Eureka)
- [ ] Distributed tracing (Micrometer Tracing + Zipkin)
- [ ] Rate limiting per user at gateway (Redis-backed)
- [ ] Admin dashboard (simple frontend)
