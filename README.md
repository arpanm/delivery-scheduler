# Delivery Scheduler

Real-time food delivery scheduling system with **VRPTW** (Vehicle Routing Problem with Time Windows) optimization. Handles thousands of restaurants, hundreds of riders, dynamic order batching, prep-time prediction, SLA tracking, and continuous route re-optimization.

![Java 17](https://img.shields.io/badge/Java-17-orange) ![Spring Boot 3.3.5](https://img.shields.io/badge/Spring%20Boot-3.3.5-green) ![Tests 27 passing](https://img.shields.io/badge/tests-27%20passing-brightgreen) ![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16%20%2B%20PostGIS-blue) ![Redis 7](https://img.shields.io/badge/Redis-7-red)

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Core Algorithms](#core-algorithms)
- [Database Schema](#database-schema)
- [API Reference](#api-reference)
- [Event System](#event-system)
- [Observability & Metrics](#observability--metrics)
- [Project Structure](#project-structure)
- [Test Suite](#test-suite)
- [Local Development Setup](#local-development-setup)
- [Production Deployment](#production-deployment)
- [Configuration Reference](#configuration-reference)
- [Simulation](#simulation)
- [Pending Tasks & Future Improvements](#pending-tasks--future-improvements)
- [License](#license)

---

## Overview

The Delivery Scheduler is a backend system that assigns incoming food delivery orders to available riders in real time, constructing optimized multi-stop routes while respecting time-window constraints, prep-time estimates, and delivery promises.

### Key Capabilities

- **Real-time order assignment** via insertion heuristic (O(R x N^2) per order)
- **Continuous route optimization** via local search with 3 neighborhood operators
- **Multi-order batching** — riders carry up to 3 concurrent orders
- **Prep-time prediction** — per-restaurant linear regression with peak-hour adjustment
- **Prediction variance** — residual std error for uncertainty estimation
- **SLA tracking** — automatic detection and metrics for late deliveries
- **Geofencing** — PostGIS polygon-based zone boundaries
- **Rider location cache** — Redis GEORADIUS for nearest-rider queries
- **Order cancellation** — removes stops and re-propagates arrival times
- **Freeze window** — protects committed stops from optimizer rearrangement
- **Retry logic** — unassignable orders retried with configurable backoff
- **Startup recovery** — rebuilds in-memory schedules from database on restart
- **Prometheus metrics** — 8 production-grade metrics for monitoring
- **WebSocket tracking** — real-time order tracking updates

### Project Stats

| Metric | Count |
|--------|-------|
| Source files | 81 |
| Test files | 8 (7 test classes + 1 test factory) |
| Unit tests | 27 |
| Database tables | 7 |
| REST endpoints | 10 |
| Domain events | 6 |
| Flyway migrations | 7 |

---

## Architecture

### High-Level Layers

```
+-------------------------------------------------------------+
|                    REST API + WebSocket                       |
|         OrderController . RiderController . Admin            |
+-------------------------------------------------------------+
|                      Service Layer                           |
|    OrderService . TripService . RiderService . Tracking      |
+-------------------------------------------------------------+
|                   Scheduler Engine                            |
|  +--------------+  +----------------+  +-----------------+   |
|  |  Orchestrator |->| Insertion      |->| Local Search    |  |
|  |  (event-     |  | Heuristic      |  | Optimizer       |  |
|  |   driven)    |  | (real-time)    |  | (periodic)      |  |
|  +--------------+  +----------------+  +-----------------+   |
|        |                   |                    |            |
|  +-----+-----+  +---------+-------+  +--------+--------+   |
|  | Constraint |  |  Cost Function  |  |  Neighborhood   |   |
|  | Engine     |  |  (7-term)       |  |  Operators (3)  |   |
|  +-----------+  +-----------------+  +-----------------+   |
+-------------------------------------------------------------+
|                    Domain Model                              |
|   Order . Rider . Restaurant . Trip . Zone . GeoLocation     |
+-------------------------------------------------------------+
|                   Infrastructure                             |
|  PostgreSQL/PostGIS  .  Redis  .  Flyway  .  Micrometer      |
+-------------------------------------------------------------+
```

### Event-Driven Flow

```
Customer places order
  -> OrderPlacedEvent
    -> SchedulerOrchestrator.onOrderPlaced()
      -> InsertionHeuristic.findBestInsertion()
        -> ConstraintEngine validates
        -> CostFunction scores
      -> Best rider assigned -> Trip created/updated
      -> OrderAssignedEvent published
      -> WebSocket tracking update sent

Every 10 seconds (per zone):
  -> LocalSearchOptimizer.optimize()
    -> Compute frozen orders (within 5-min window)
    -> RelocateMove / TwoOptMove / OrOptMove
    -> Accept improving moves (steepest descent)
    -> Update schedules in ConcurrentHashMap
```

### Concurrency Model

- **Zone-level locking**: `ConcurrentHashMap<Long, RiderSchedule>` per zone
- **Read/write locks**: `ReentrantReadWriteLock` guards schedule access
- **Async event handling**: `@Async` listeners for non-blocking order processing
- **Retry executor**: `ScheduledExecutorService` for deferred retry of unassignable orders

---

## Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| **Java** | 17 | Language runtime |
| **Spring Boot** | 3.3.5 | Application framework |
| **Spring Web** | — | REST API |
| **Spring WebSocket** | — | Real-time tracking |
| **Spring Data JPA** | — | ORM / repository layer |
| **Spring Data Redis** | — | Rider location cache |
| **Spring Validation** | — | Request validation |
| **Spring Actuator** | — | Health + metrics endpoints |
| **PostgreSQL** | 16 | Primary database |
| **PostGIS** | 3.4 | Geospatial extensions (zone boundaries, spatial indexing) |
| **Redis** | 7 | Rider geolocation cache (GEORADIUS), rider status (TTL) |
| **Flyway** | — | Database migrations (7 migrations) |
| **Hibernate Spatial** | — | JPA <-> PostGIS integration |
| **JTS Topology Suite** | 1.19.0 | Geometry operations |
| **Apache Commons Math3** | 3.6.1 | Linear regression for prep-time prediction |
| **Micrometer** | — | Metrics instrumentation |
| **Prometheus Registry** | — | Metrics export to Prometheus |
| **H2** | — | In-memory test database |
| **JUnit 5** | — | Unit testing framework |

---

## Core Algorithms

### 1. Insertion Heuristic

The insertion heuristic assigns each new order to the best available rider in real time.

**Complexity**: O(R x N^2) per order — where R = candidate riders, N = stops per rider

**Algorithm**:
```
for each candidate rider (up to 20, within 5km):
    for each pickup insertion position i in [0..N]:
        for each delivery insertion position j in [i+1..N+1]:
            if ConstraintEngine.isFeasible(schedule with new stops):
                cost = CostFunction.insertionCost(before, after, order)
                if cost < bestCost:
                    bestCost = cost
                    bestCandidate = (rider, i, j)
    early-terminate if current rider cost > 2x best known
return bestCandidate
```

**Key properties**:
- Considers all valid pickup-delivery position pairs
- Constraint engine validates time windows, precedence (pickup before delivery), rider capacity (max 3 orders)
- Early termination skips riders that cannot beat current best by 2x
- Falls back to retry queue if no feasible insertion exists

### 2. Local Search Optimizer

Periodic re-optimization of all routes within each zone using steepest descent.

**Parameters**: 500 max iterations, 2-second time budget per zone, runs every 10 seconds

**Three Neighborhood Operators**:

| Operator | Description | Scope |
|----------|-------------|-------|
| **RelocateMove** | Move an order (pickup + delivery pair) from one rider to another | Cross-rider |
| **TwoOptMove** | Reverse a segment of stops within a single rider's route | Intra-rider |
| **OrOptMove** | Relocate a chain of 1-3 consecutive stops within a rider's route | Intra-rider |

**Algorithm**:
```
frozen_orders = stops arriving within 5-minute freeze window
for iteration in [0..500] (or until 2s elapsed):
    bestMove = null
    for each operator in [Relocate, TwoOpt, OrOpt]:
        moves = operator.generateMoves(schedules, frozen_orders)
        for each move:
            delta = CostFunction.absoluteCost(after) - CostFunction.absoluteCost(before)
            if operator == Relocate and move is cross-rider:
                delta += reassignmentPenalty
            if delta < bestDelta:
                bestMove = move
    if bestMove improves cost:
        apply bestMove
    else:
        break  // local minimum reached
```

**Freeze Window**: Stops with estimated arrival within 5 minutes of the current time are marked as frozen. Frozen orders cannot be rearranged, relocated, or reversed — this protects riders who are already en route.

### 3. Cost Function (7 Terms)

The cost function scores route quality for both insertion decisions and optimization moves.

| Term | Weight | Formula | Purpose |
|------|--------|---------|---------|
| **Distance** | 1.0 | Sum of Haversine distances between consecutive stops | Minimize total travel distance |
| **Time** | 0.5 | Sum of travel times (distance / 6.0 m/s) | Minimize total travel time |
| **Detour** | 2.0 | Additional distance vs. direct pickup-to-delivery | Penalize circuitous routes |
| **Idle** | 0.3 | Wait time at stops when arriving before time window opens | Penalize wasted waiting time |
| **SLA Risk** | 3.0 | `exp(-slack / 180)` for deliveries where slack < 300s | Exponentially penalize approaching deadlines |
| **Batching Bonus** | 1.5 | -1.0 if new pickup is within 200m of existing pickup | Reward restaurant-proximate batching |
| **Reassignment Penalty** | 0.8 | Flat penalty for cross-rider relocations | Discourage unnecessary rider swaps |

### 4. Constraint Engine

Forward time-propagation validates schedule feasibility by computing estimated arrival at each stop sequentially.

**Three Constraints**:

| Constraint | Rule |
|------------|------|
| **TimeWindowConstraint** | Arrival must be <= latest allowed time for each stop |
| **PrecedenceConstraint** | Pickup stop must precede delivery stop for the same order |
| **RiderCapacityConstraint** | Max 3 concurrent (picked up but not delivered) orders |

**Time Propagation**:
```
currentTime = now
currentLocation = rider.location
for each stop in schedule:
    travelTime = Haversine(currentLocation, stop.location) * detourFactor / citySpeed
    arrival = currentTime + travelTime
    if arrival < stop.timeWindow.earliest:
        arrival = stop.timeWindow.earliest  // wait
    stop.estimatedArrival = arrival
    currentTime = arrival + stop.serviceTime
    currentLocation = stop.location
```

### 5. Prep-Time Prediction

Per-restaurant machine learning model using linear regression on historical order data.

**Model**: `prepTime = B0 + B1 * itemCount` (trained per restaurant)

**Algorithm**:
```
if restaurant has >= 20 historical samples:
    regression = OLS fit on (itemCount -> actualPrepTime)
    prediction = regression.predict(itemCount)
    if isPeakHour(now):  // 12-1pm or 7-9pm
        prediction *= 1.25
    blended = 0.7 * prediction + 0.3 * P90(historical)
    return max(blended, 120)  // minimum 2 minutes
else:
    return restaurant.defaultPrepTime  // 600 seconds
```

**Variance Estimation**: Residual standard error from the regression model provides uncertainty bounds, stored as `prep_time_variance_seconds` on each order for downstream SLA risk calculation.

---

## Database Schema

### Entity-Relationship Diagram

```
+----------+     +--------------+     +----------+
|  zones   |<----|  restaurants |     |  riders  |
|          |     |              |     |          |
| id       |     | id           |     | id       |
| name     |     | name         |     | name     |
| boundary |     | latitude     |     | phone    |
|          |     | longitude    |     | status   |
+----------+     | zone_id  -->|     | zone_id  |
      ^          | avg_prep_time|     | max_conc |
      |          +------+-------+     +----+-----+
      |                 |                   |
      |          +------+-------+     +----+-----+
      |          |   orders     |     |  trips   |
      |          |              |     |          |
      +----------|  id          |     | id       |
                 | restaurant_id|     | rider_id |
                 | delivery_lat |     | status   |
                 | delivery_lon |     +----+-----+
                 | status       |          |
                 | promised_by  |   +------+------+
                 | trip_id   ---|-->| trip_stops  |
                 | item_count   |   |             |
                 | prep_variance|   | id          |
                 +------+-------+   | trip_id     |
                        |           | stop_type   |
                 +------+--------+  | location    |
                 | prep_time_    |  | tw_earliest |
                 | history       |  | tw_latest   |
                 |               |  | est_arrival |
                 | restaurant_id |  | completed   |
                 | order_id      |  +-------------+
                 | item_count    |
                 | actual_prep   |
                 +---------------+
```

### Migration Files

| Migration | Table | Key Columns |
|-----------|-------|-------------|
| **V1** | `zones` | `id`, `name`, `boundary` (PostGIS Polygon, GIST-indexed) |
| **V2** | `restaurants` | `id`, `name`, `latitude`, `longitude`, `zone_id` (FK), `avg_prep_time_seconds` |
| **V3** | `riders` | `id`, `name`, `phone`, `status`, `zone_id` (FK), `last_known_lat/lon`, `max_concurrent_orders` |
| **V4** | `trips`, `trip_stops` | trips: `rider_id` (FK), `status`; trip_stops: `trip_id` (FK), `sequence_index`, `stop_type`, location, time windows, `estimated_arrival`, `completed` |
| **V5** | `orders` | `id`, `restaurant_id` (FK), delivery location, `status`, `promised_delivery_by`, `estimated_prep_time_seconds`, `trip_id` (FK), `item_count` |
| **V6** | `prep_time_history` | `restaurant_id` (FK), `order_id` (FK), `item_count`, `actual_prep_time_seconds` |
| **V7** | `orders` (ALTER) | Adds `prep_time_variance_seconds` column |

---

## API Reference

Base URL: `http://localhost:8080/api/v1`

### Orders

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| `POST` | `/orders` | Place a new order | `OrderRequest` | `201` + `OrderResponse` |
| `GET` | `/orders/{orderId}` | Get order details | — | `200` + `OrderResponse` |
| `POST` | `/orders/{orderId}/cancel` | Cancel an order | — | `200` + `OrderResponse` |

**OrderRequest**:
```json
{
  "restaurantId": 1,
  "deliveryLocation": { "latitude": 12.96, "longitude": 77.61 },
  "customerName": "John Doe",
  "customerPhone": "555-0100",
  "itemsSummary": "2x Burger, 1x Fries",
  "totalItemCount": 3,
  "requestedDeliveryBy": "2025-01-15T13:00:00Z"
}
```

### Riders

| Method | Endpoint | Description | Request/Params | Response |
|--------|----------|-------------|----------------|----------|
| `POST` | `/riders/{riderId}/location` | Update rider GPS | `RiderLocationUpdate` | `200` |
| `POST` | `/riders/{riderId}/status` | Change rider status | `?status=AVAILABLE` | `200` |
| `GET` | `/riders/{riderId}/current-trip` | Get active trip | — | `200` + `TripResponse` |
| `POST` | `/riders/{riderId}/stops/{stopId}/complete` | Mark stop done | — | `200` |

### Restaurants

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| `POST` | `/restaurants` | Create restaurant | `CreateRestaurantRequest` | `201` + `RestaurantResponse` |
| `GET` | `/restaurants/{id}` | Get restaurant | — | `200` + `RestaurantResponse` |

### Tracking

| Method | Endpoint | Description | Response |
|--------|----------|-------------|----------|
| `GET` | `/orders/{orderId}/tracking` | Get live tracking | `200` + `TrackingUpdate` |

### Admin

| Method | Endpoint | Description | Response |
|--------|----------|-------------|----------|
| `GET` | `/admin/scheduler/metrics` | Scheduler stats | `200` + metrics JSON |
| `POST` | `/admin/simulation/run` | Run simulation | `200` + `SimulationReport` |

### Actuator

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Application health check |
| `/actuator/prometheus` | Prometheus metrics scrape endpoint |
| `/actuator/info` | Application info |

---

## Event System

The system uses Spring application events for loose coupling between components.

| Event | Publisher | Listener(s) | Trigger |
|-------|-----------|-------------|---------|
| `OrderPlacedEvent` | `OrderService` | `SchedulerOrchestrator` | New order created |
| `OrderAssignedEvent` | `SchedulerOrchestrator` | `TrackingService` | Order assigned to rider |
| `OrderCancelledEvent` | `OrderService` | `SchedulerOrchestrator` | Order cancelled |
| `RiderLocationUpdatedEvent` | `RiderService` | `RiderLocationCache` | Rider GPS update |
| `TripUpdatedEvent` | `TripService` | `TrackingService` | Trip state change |
| `StopCompletedEvent` | `TripService` | `SchedulerOrchestrator` | Pickup/delivery completed |

**Extensibility**: The `EventPublisher` interface abstracts event dispatching. The default `SpringEventPublisher` uses in-process Spring events. Swap to Kafka by implementing `EventPublisher` with a Kafka producer — no changes to domain or service code required.

---

## Observability & Metrics

### Prometheus Metrics

All metrics are registered via `SchedulerMetrics` and exported at `/actuator/prometheus`.

| Metric Name | Type | Description |
|-------------|------|-------------|
| `scheduler.assignment.latency` | Timer | Time to find best insertion for an order |
| `scheduler.optimization.duration` | Timer | Time spent in local search per zone cycle |
| `scheduler.orders.assigned` | Counter | Total orders successfully assigned |
| `scheduler.orders.unassignable` | Counter | Orders with no feasible rider |
| `scheduler.orders.cancelled` | Counter | Total cancelled orders |
| `scheduler.orders.late` | Counter | Deliveries that missed SLA promise |
| `scheduler.batch.size` | Distribution Summary | Number of stops per rider schedule |
| `scheduler.route.slack` | Distribution Summary | Time slack to delivery deadline |

### Health & Management

```yaml
# Exposed endpoints
/actuator/health       # UP/DOWN status
/actuator/prometheus   # Prometheus scrape
/actuator/info         # App metadata
```

---

## Project Structure

```
src/main/java/com/deliveryscheduler/
|-- DeliverySchedulerApplication.java          # Spring Boot entry point
|
|-- api/                                        # HTTP layer (13 files)
|   |-- dto/                                    # Request/response DTOs (7)
|   |   |-- GeoLocationDto.java
|   |   |-- OrderRequest.java
|   |   |-- OrderResponse.java
|   |   |-- RiderLocationUpdate.java
|   |   |-- TrackingUpdate.java
|   |   |-- TripResponse.java
|   |   +-- TripStopResponse.java
|   |-- rest/                                   # REST controllers (5)
|   |   |-- AdminController.java
|   |   |-- OrderController.java
|   |   |-- RestaurantController.java
|   |   |-- RiderController.java
|   |   +-- TrackingController.java
|   +-- websocket/                              # WebSocket handler (1)
|
|-- config/                                     # Configuration (4 files)
|   |-- AsyncConfig.java                        # Async thread pool
|   |-- RedisConfig.java                        # Redis connection
|   |-- SchedulerProperties.java                # Algorithm parameters
|   +-- WebSocketConfig.java                    # STOMP/SockJS setup
|
|-- domain/                                     # Domain model (25 files)
|   |-- event/                                  # Domain events (6)
|   |   |-- OrderPlacedEvent.java
|   |   |-- OrderAssignedEvent.java
|   |   |-- OrderCancelledEvent.java
|   |   |-- RiderLocationUpdatedEvent.java
|   |   |-- StopCompletedEvent.java
|   |   +-- TripUpdatedEvent.java
|   |-- model/                                  # JPA entities & value objects (13)
|   |   |-- GeoLocation.java                    # Embeddable lat/lon
|   |   |-- Order.java                          # Order entity
|   |   |-- OrderStatus.java                    # PLACED -> DELIVERED/CANCELLED
|   |   |-- PrepTimeRecord.java                 # Historical prep time data
|   |   |-- Restaurant.java                     # Restaurant entity
|   |   |-- Rider.java                          # Rider entity
|   |   |-- RiderStatus.java                    # OFFLINE/AVAILABLE/ON_TRIP
|   |   |-- StopType.java                       # PICKUP / DELIVERY
|   |   |-- TimeWindow.java                     # Embeddable [earliest, latest]
|   |   |-- Trip.java                           # Trip entity (rider assignment)
|   |   |-- TripStatus.java                     # PLANNED/ACTIVE/COMPLETED
|   |   |-- TripStop.java                       # Individual stop in trip
|   |   +-- Zone.java                           # Delivery zone with boundary
|   +-- repository/                             # Spring Data repositories (6)
|       |-- OrderRepository.java
|       |-- PrepTimeRepository.java
|       |-- RestaurantRepository.java
|       |-- RiderRepository.java
|       |-- TripRepository.java
|       +-- ZoneRepository.java
|
|-- geofencing/                                 # Geofencing (1 file)
|   +-- GeofencingService.java                  # Point-in-polygon zone lookup
|
|-- infrastructure/                             # Infrastructure adapters (4 files)
|   |-- cache/
|   |   +-- RiderLocationCache.java             # Redis GEORADIUS + status cache
|   |-- messaging/
|   |   |-- EventPublisher.java                 # Interface (swappable to Kafka)
|   |   +-- SpringEventPublisher.java           # Default Spring event impl
|   +-- metrics/
|       +-- SchedulerMetrics.java               # 8 Micrometer metrics
|
|-- prediction/                                 # Prep-time prediction (3 files)
|   |-- PrepTimeModel.java                      # Per-restaurant regression model
|   |-- PrepTimePredictionService.java          # Training + prediction service
|   +-- PredictionResult.java                   # mean + variance record
|
|-- routing/                                    # Travel time estimation (2 files)
|   |-- TravelTimeEstimator.java                # Haversine + detour factor
|   +-- TravelTimeProvider.java                 # Interface (swappable to OSRM)
|
|-- scheduler/                                  # Core scheduling engine (16 files)
|   |-- SchedulerOrchestrator.java              # Event-driven orchestration
|   |-- constraint/                             # Constraint validation (5)
|   |   |-- ConstraintEngine.java               # Time propagation + validation
|   |   |-- PrecedenceConstraint.java           # Pickup before delivery
|   |   |-- RiderCapacityConstraint.java        # Max 3 concurrent orders
|   |   |-- ScheduleConstraint.java             # Constraint interface
|   |   +-- TimeWindowConstraint.java           # Arrival within window
|   |-- cost/
|   |   +-- CostFunction.java                   # 7-term multi-objective scoring
|   |-- insertion/
|   |   +-- InsertionHeuristic.java             # O(R*N^2) greedy insertion
|   |-- model/                                  # Scheduler-internal models (3)
|   |   |-- InsertionCandidate.java             # Insertion result
|   |   |-- RiderSchedule.java                  # In-memory rider route
|   |   +-- ScheduledStop.java                  # Planned stop with time window
|   +-- optimization/                           # Local search (6)
|       |-- LocalSearchOptimizer.java           # Steepest descent driver
|       |-- Move.java                           # Generic move representation
|       |-- NeighborhoodOperator.java           # Operator interface
|       |-- OrOptMove.java                      # Chain relocation (1-3 stops)
|       |-- RelocateMove.java                   # Cross-rider order relocation
|       +-- TwoOptMove.java                     # Intra-route segment reversal
|
|-- service/                                    # Business services (5 files)
|   |-- OrderService.java                       # Order CRUD + events
|   |-- RestaurantService.java                  # Restaurant CRUD
|   |-- RiderService.java                       # Rider management
|   |-- TrackingService.java                    # WebSocket tracking
|   +-- TripService.java                        # Trip lifecycle + SLA tracking
|
+-- simulation/                                 # Load testing simulation (7 files)
    |-- CityGenerator.java                      # Generate zones, restaurants, riders
    |-- MetricsCollector.java                   # Collect simulation statistics
    |-- OrderGenerator.java                     # Simulate incoming orders
    |-- RiderSimulator.java                     # Simulate rider movement
    |-- SimulationConfig.java                   # Simulation parameters
    |-- SimulationReport.java                   # Report DTO
    +-- SimulationRunner.java                   # Orchestrate simulation

src/test/java/com/deliveryscheduler/
|-- TestDataFactory.java                        # Test utility (reflection-based ID setting)
|-- prediction/
|   |-- PrepTimePredictionServiceTest.java      # 4 tests
|   +-- PredictionVarianceTest.java             # 3 tests
|-- scheduler/
|   |-- ConstraintEngineTest.java               # 4 tests
|   |-- CostFunctionTest.java                   # 3 tests
|   |-- InsertionHeuristicTest.java             # 4 tests
|   +-- LocalSearchOptimizerTest.java           # 3 tests
+-- service/
    +-- OrderCancellationTest.java              # 6 tests

src/main/resources/
|-- application.yml                             # Main configuration
+-- db/migration/                               # Flyway migrations (V1-V7)
```

---

## Test Suite

### Summary

**27 tests** across **7 test classes**, all pure unit tests using JUnit 5 (no Mockito, no Spring context). Tests use `TestDataFactory` with reflection-based ID setting to create domain objects without a database.

```bash
mvn clean test    # Runs all 27 tests (~3 seconds, H2 in-memory)
```

### Test Inventory

#### ConstraintEngineTest (4 tests)

| Test | Validates |
|------|-----------|
| `feasibleSchedule_shouldPass` | Valid schedule passes all constraints |
| `violatedTimeWindow_shouldFail` | Arrival after latest time is rejected |
| `violatedPrecedence_shouldFail` | Delivery before pickup is rejected |
| `arrivalTimePropagation_shouldBeCorrect` | Forward time propagation computes correct ETAs |

#### InsertionHeuristicTest (4 tests)

| Test | Validates |
|------|-----------|
| `emptySchedule_shouldFindInsertion` | Order assigned to empty rider schedule |
| `multipleRiders_shouldSelectCloserRider` | Closer rider selected by cost function |
| `tightTimeWindow_shouldReturnNullOrInfeasible` | Infeasible if rider too far for deadline |
| `batchingWithExistingOrder_shouldFindFeasibleInsertion` | Second order batched with first (4 total stops) |

#### PrepTimePredictionServiceTest (4 tests)

| Test | Validates |
|------|-----------|
| `trainAndPredict_shouldReturnReasonableEstimate` | Regression model produces valid prediction |
| `moreItems_shouldTakeLonger` | Higher item count leads to longer prep time |
| `peakHour_shouldPredictLonger` | 25% multiplier during peak hours |
| `minimumPrediction_shouldBe120Seconds` | Floor of 120 seconds enforced |

#### PredictionVarianceTest (3 tests)

| Test | Validates |
|------|-----------|
| `variance_isPositiveAfterTrainingWithVariedData` | Residual std error > 0 for noisy data |
| `predictionResult_containsMeanAndVariance` | `PredictionResult` record has mean + variance |
| `fallback_returnsDefaultVariance` | Returns 0 variance when using default prep time |

#### CostFunctionTest (3 tests)

| Test | Validates |
|------|-----------|
| `slaRiskPenalty_increasesAsDeadlineApproaches` | Exponential penalty grows as slack decreases |
| `batchingBonus_appliedForNearbyPickups` | Cost reduction for pickups within 200m |
| `reassignmentCost_returnsConfiguredWeight` | Cross-rider penalty equals configured weight |

#### LocalSearchOptimizerTest (3 tests)

| Test | Validates |
|------|-----------|
| `frozenStops_areNotMoved` | Stops within freeze window are immutable |
| `optimize_respectsTimeBudget` | Optimizer terminates within 2-second limit |
| `relocate_findsImprovement` | Cross-rider relocation improves total cost |

#### OrderCancellationTest (6 tests)

| Test | Validates |
|------|-----------|
| `cancelOrder_removesStopsFromSchedule` | Pickup + delivery stops removed from rider schedule |
| `cancelOrder_propagatesArrivalTimesCorrectly` | Remaining stops' ETAs recomputed after removal |
| `cancelledEvent_containsOrderAndTimestamp` | `OrderCancelledEvent` has order reference + timestamp |
| `cancelOrder_completedStopsArePreserved` | Already-completed stops are not removed |
| `cancelNonExistentOrder_removesNothing` | Cancelling unknown order is a no-op |
| `frozenStop_identifiedCorrectly` | `isFrozen()` correctly identifies stops within freeze window |

---

## Local Development Setup

### Prerequisites

- **Java 17+** (JDK, not JRE)
- **Maven 3.9+**
- **Docker & Docker Compose** (for PostgreSQL + Redis)

### Quick Start (Docker Compose)

```bash
# 1. Clone the repository
git clone <repository-url>
cd delivery-scheduler

# 2. Start PostgreSQL (PostGIS) and Redis
docker-compose up -d

# 3. Verify containers are running
docker-compose ps
# postgres: postgis/postgis:16-3.4  ->  port 5432
# redis:    redis:7-alpine          ->  port 6379

# 4. Build the project
mvn clean install

# 5. Run the application
mvn spring-boot:run
# OR:
java -jar target/delivery-scheduler-0.1.0-SNAPSHOT.jar

# 6. Verify: health check
curl http://localhost:8080/actuator/health
# -> {"status":"UP"}
```

### VM / Bare-Metal Setup (No Docker)

#### Install PostgreSQL 16 + PostGIS

```bash
# Ubuntu/Debian
sudo apt-get install postgresql-16 postgresql-16-postgis-3
sudo systemctl start postgresql

# Create database and user
sudo -u postgres psql <<EOF
CREATE USER delivery WITH PASSWORD 'delivery';
CREATE DATABASE delivery_scheduler OWNER delivery;
\c delivery_scheduler
CREATE EXTENSION postgis;
EOF
```

#### Install Redis 7

```bash
# Ubuntu/Debian
sudo apt-get install redis-server
sudo systemctl start redis-server

# Verify
redis-cli ping
# -> PONG
```

#### Configure and Run

```bash
# Set environment variables (or edit application.yml)
export DB_USERNAME=delivery
export DB_PASSWORD=delivery
export REDIS_HOST=localhost
export REDIS_PORT=6379

# Build and run
mvn clean install
java -jar target/delivery-scheduler-0.1.0-SNAPSHOT.jar
```

Flyway migrations run automatically on startup, creating all 7 tables.

### Running Tests

```bash
# All 27 unit tests (no Docker needed — uses H2 in-memory database)
mvn clean test

# Run a specific test class
mvn test -Dtest=InsertionHeuristicTest

# Run with verbose output
mvn test -X
```

---

## Production Deployment

### Docker Deployment

#### Dockerfile (Multi-Stage Build)

```dockerfile
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/delivery-scheduler-0.1.0-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### docker-compose.prod.yml

```yaml
services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/delivery_scheduler
      - DB_USERNAME=delivery
      - DB_PASSWORD=${DB_PASSWORD}
      - REDIS_HOST=redis
      - REDIS_PORT=6379
      - JAVA_OPTS=-Xmx512m -Xms256m
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    restart: unless-stopped

  postgres:
    image: postgis/postgis:16-3.4
    environment:
      POSTGRES_DB: delivery_scheduler
      POSTGRES_USER: delivery
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U delivery"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redisdata:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

volumes:
  pgdata:
  redisdata:
```

```bash
# Production launch
DB_PASSWORD=<secure-password> docker-compose -f docker-compose.prod.yml up -d
```

### VM Deployment

#### systemd Service

```ini
# /etc/systemd/system/delivery-scheduler.service
[Unit]
Description=Delivery Scheduler
After=network.target postgresql.service redis.service

[Service]
Type=simple
User=delivery
WorkingDirectory=/opt/delivery-scheduler
ExecStart=/usr/bin/java -Xmx512m -Xms256m -jar delivery-scheduler-0.1.0-SNAPSHOT.jar
Environment=DB_USERNAME=delivery
Environment=DB_PASSWORD=<secure-password>
Environment=REDIS_HOST=localhost
Environment=REDIS_PORT=6379
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable delivery-scheduler
sudo systemctl start delivery-scheduler
sudo journalctl -u delivery-scheduler -f  # view logs
```

#### Reverse Proxy (nginx)

```nginx
# /etc/nginx/sites-available/delivery-scheduler
upstream delivery_app {
    server 127.0.0.1:8080;
}

server {
    listen 80;
    server_name api.delivery.example.com;

    location / {
        proxy_pass http://delivery_app;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /ws {
        proxy_pass http://delivery_app;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

---

## Configuration Reference

All configuration lives in `src/main/resources/application.yml`. Override via environment variables or Spring profiles.

### Scheduler Properties

| Property | Default | Description |
|----------|---------|-------------|
| `scheduler.insertion.max-candidate-riders` | `20` | Max riders evaluated per order |
| `scheduler.insertion.max-radius-meters` | `5000` | Search radius for candidate riders |
| `scheduler.optimization.interval-ms` | `10000` | Local search run frequency |
| `scheduler.optimization.max-iterations` | `500` | Max iterations per optimization run |
| `scheduler.optimization.max-time-ms` | `2000` | Time budget per optimization run |
| `scheduler.optimization.freeze-window-seconds` | `300` | Stops within this window are frozen (5 min) |
| `scheduler.cost.weight-distance` | `1.0` | Distance cost weight |
| `scheduler.cost.weight-time` | `0.5` | Travel time cost weight |
| `scheduler.cost.weight-detour` | `2.0` | Detour penalty weight |
| `scheduler.cost.weight-idle` | `0.3` | Idle/wait time weight |
| `scheduler.cost.weight-sla-risk` | `3.0` | SLA risk penalty weight |
| `scheduler.cost.weight-batching-bonus` | `1.5` | Batching reward weight |
| `scheduler.cost.weight-reassignment-penalty` | `0.8` | Cross-rider relocation penalty |
| `scheduler.cost.sla-risk-threshold-seconds` | `300` | SLA risk activates below this slack |
| `scheduler.retry.max-attempts` | `3` | Retry attempts for unassignable orders |
| `scheduler.retry.delay-ms` | `30000` | Delay between retry attempts |

### Prediction Properties

| Property | Default | Description |
|----------|---------|-------------|
| `prediction.min-samples-for-model` | `20` | Min data points to train regression |
| `prediction.model-refresh-interval-ms` | `3600000` | Model retrain frequency (1 hour) |
| `prediction.default-prep-time-seconds` | `600` | Fallback prep time (10 min) |

### Routing Properties

| Property | Default | Description |
|----------|---------|-------------|
| `routing.city-speed-mps` | `6.0` | Assumed rider speed (m/s) |
| `routing.detour-factor` | `1.4` | Haversine to road distance multiplier |
| `routing.pickup-service-time-seconds` | `120` | Time spent at restaurant (2 min) |
| `routing.delivery-service-time-seconds` | `90` | Time spent at customer (1.5 min) |

### Database & Redis

| Property | Default | Description |
|----------|---------|-------------|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/delivery_scheduler` | PostgreSQL connection |
| `spring.datasource.username` | `delivery` (env: `DB_USERNAME`) | DB username |
| `spring.datasource.password` | `delivery` (env: `DB_PASSWORD`) | DB password |
| `spring.data.redis.host` | `localhost` (env: `REDIS_HOST`) | Redis host |
| `spring.data.redis.port` | `6379` (env: `REDIS_PORT`) | Redis port |

---

## Simulation

The built-in simulation engine generates a synthetic city with zones, restaurants, riders, and order flow for load testing.

### Run a Simulation

```bash
# Default configuration
curl -X POST http://localhost:8080/api/v1/admin/simulation/run

# Custom configuration
curl -X POST http://localhost:8080/api/v1/admin/simulation/run \
  -H "Content-Type: application/json" \
  -d '{
    "numZones": 3,
    "restaurantsPerZone": 10,
    "ridersPerZone": 5,
    "durationSeconds": 300,
    "timeAccelerationFactor": 10.0
  }'
```

### What It Simulates

1. **City generation**: Creates zones with boundaries, restaurants at random coordinates, riders distributed across zones
2. **Order generation**: Simulates incoming orders at realistic rates with varying item counts
3. **Rider movement**: Simulates riders moving between stops at configured city speed
4. **Stop completion**: Automatically completes stops when riders arrive at locations
5. **Metrics collection**: Tracks assignment rate, on-time delivery rate, average slack, rider utilization

---

## Pending Tasks & Future Improvements

### Critical (Production Blockers)

- [ ] **Dockerfile** — Create production Dockerfile and CI/CD build pipeline
- [ ] **Integration tests** — Testcontainers-based tests with real PostgreSQL and Redis
- [ ] **Authentication & authorization** — Secure API endpoints (JWT / OAuth2)
- [ ] **Rate limiting** — Throttle order placement and public-facing endpoints
- [ ] **Input sanitization** — Validate all incoming coordinates, IDs, and string fields

### High Priority

- [ ] **OSRM / Google Directions API** — Replace Haversine estimator with real road routing (swap `TravelTimeProvider` implementation)
- [ ] **Kafka event bus** — Replace Spring events with Kafka for durability and cross-service communication (swap `EventPublisher` implementation)
- [ ] **WebSocket authentication** — Secure WebSocket connections with per-order subscription tokens
- [ ] **HikariCP tuning** — Configure database connection pool sizes for production load
- [ ] **Structured logging** — JSON logs via Logback for ELK / CloudWatch integration
- [ ] **Graceful shutdown** — Drain in-flight requests and persist scheduler state on SIGTERM

### Medium Priority

- [ ] **H3 hex indexing** — Replace PostGIS GEORADIUS with Uber H3 for faster geospatial queries at scale
- [ ] **jsprit / OR-Tools** — Plug in established VRPTW solver for large-scale optimization (>100 riders per zone)
- [ ] **XGBoost / LightGBM prep-time model** — Replace linear regression with gradient-boosted trees for higher accuracy
- [ ] **Order batching heuristic** — Pre-group same-restaurant orders before insertion for efficiency
- [ ] **Multi-zone optimization** — Allow cross-zone rider assignments near zone borders
- [ ] **Rider shift management** — Availability windows, break scheduling, shift handoff
- [ ] **Customer notifications** — Push notifications and SMS for order status updates

### Low Priority

- [ ] **Admin dashboard UI** — React or Vue frontend for real-time monitoring
- [ ] **A/B testing framework** — Experiment with different cost function weights
- [ ] **Historical analytics** — Reporting dashboard for delivery performance metrics
- [ ] **Load testing harness** — Gatling or k6 scripts for capacity planning
- [ ] **Kubernetes Helm chart** — K8s deployment manifests with auto-scaling
- [ ] **CI/CD pipeline** — GitHub Actions for build, test, and deployment automation

---

## License

This project is proprietary. All rights reserved.
