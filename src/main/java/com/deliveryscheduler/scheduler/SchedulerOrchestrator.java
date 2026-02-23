package com.deliveryscheduler.scheduler;

import com.deliveryscheduler.config.SchedulerProperties;
import com.deliveryscheduler.domain.event.OrderAssignedEvent;
import com.deliveryscheduler.domain.event.OrderCancelledEvent;
import com.deliveryscheduler.domain.event.OrderPlacedEvent;
import com.deliveryscheduler.domain.event.TripUpdatedEvent;
import com.deliveryscheduler.domain.model.*;
import com.deliveryscheduler.domain.repository.OrderRepository;
import com.deliveryscheduler.domain.repository.RiderRepository;
import com.deliveryscheduler.domain.repository.TripRepository;
import com.deliveryscheduler.geofencing.GeofencingService;
import com.deliveryscheduler.infrastructure.cache.RiderLocationCache;
import com.deliveryscheduler.infrastructure.cache.RiderLocationCache.RiderWithDistance;
import com.deliveryscheduler.infrastructure.messaging.EventPublisher;
import com.deliveryscheduler.infrastructure.metrics.SchedulerMetrics;
import com.deliveryscheduler.scheduler.constraint.ConstraintEngine;
import com.deliveryscheduler.scheduler.insertion.InsertionHeuristic;
import com.deliveryscheduler.scheduler.model.InsertionCandidate;
import com.deliveryscheduler.scheduler.model.RiderSchedule;
import com.deliveryscheduler.scheduler.model.ScheduledStop;
import com.deliveryscheduler.scheduler.optimization.LocalSearchOptimizer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class SchedulerOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SchedulerOrchestrator.class);

    private final InsertionHeuristic insertionHeuristic;
    private final LocalSearchOptimizer localSearchOptimizer;
    private final ConstraintEngine constraintEngine;
    private final GeofencingService geofencingService;
    private final RiderLocationCache riderLocationCache;
    private final RiderRepository riderRepository;
    private final TripRepository tripRepository;
    private final OrderRepository orderRepository;
    private final EventPublisher eventPublisher;
    private final SchedulerProperties properties;
    private final SchedulerMetrics metrics;

    // In-memory state: current working schedules per zone
    private final ConcurrentHashMap<Long, ConcurrentHashMap<Long, RiderSchedule>> zoneSchedules = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ReentrantReadWriteLock> zoneLocks = new ConcurrentHashMap<>();

    // Retry state for failed assignments (GAP 7)
    private final ConcurrentHashMap<Long, Integer> retryCount = new ConcurrentHashMap<>();
    private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "scheduler-retry");
        t.setDaemon(true);
        return t;
    });

    public SchedulerOrchestrator(InsertionHeuristic insertionHeuristic,
                                  LocalSearchOptimizer localSearchOptimizer,
                                  ConstraintEngine constraintEngine,
                                  GeofencingService geofencingService,
                                  RiderLocationCache riderLocationCache,
                                  RiderRepository riderRepository,
                                  TripRepository tripRepository,
                                  OrderRepository orderRepository,
                                  EventPublisher eventPublisher,
                                  SchedulerProperties properties,
                                  SchedulerMetrics metrics) {
        this.insertionHeuristic = insertionHeuristic;
        this.localSearchOptimizer = localSearchOptimizer;
        this.constraintEngine = constraintEngine;
        this.geofencingService = geofencingService;
        this.riderLocationCache = riderLocationCache;
        this.riderRepository = riderRepository;
        this.tripRepository = tripRepository;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
        this.metrics = metrics;
    }

    /**
     * Recover in-memory state from database on startup (GAP 8).
     * Rebuilds zoneSchedules from active/planned trips so that
     * restarting the process doesn't orphan in-flight deliveries.
     */
    @PostConstruct
    public void recoverState() {
        try {
            List<Trip> activeTrips = tripRepository.findByStatusIn(
                    List.of(TripStatus.PLANNED, TripStatus.ACTIVE));

            int recovered = 0;
            for (Trip trip : activeTrips) {
                try {
                    if (trip.getOrders().isEmpty() || trip.getStops().isEmpty()) continue;

                    Order firstOrder = trip.getOrders().get(0);
                    Long zoneId = resolveZoneId(firstOrder);
                    RiderSchedule schedule = rebuildScheduleFromTrip(trip);

                    zoneSchedules.computeIfAbsent(zoneId, k -> new ConcurrentHashMap<>())
                            .put(trip.getRider().getId(), schedule);
                    zoneLocks.computeIfAbsent(zoneId, k -> new ReentrantReadWriteLock());
                    recovered++;
                } catch (Exception e) {
                    log.warn("Failed to recover trip {}: {}", trip.getId(), e.getMessage());
                }
            }

            if (recovered > 0) {
                log.info("Recovered {} active schedules from DB on startup", recovered);
            }
        } catch (Exception e) {
            log.warn("State recovery failed (may be expected on first run): {}", e.getMessage());
        }
    }

    /**
     * Called when a new order arrives. Finds the best rider and inserts the order.
     */
    @Async("schedulerExecutor")
    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        Order order = event.order();
        long startTime = System.currentTimeMillis();

        try {
            GeoLocation restaurantLocation = order.getRestaurant().getLocation();
            Long zoneId = resolveZoneId(order);

            ReentrantReadWriteLock lock = zoneLocks.computeIfAbsent(zoneId,
                    k -> new ReentrantReadWriteLock());
            lock.writeLock().lock();
            try {
                ConcurrentHashMap<Long, RiderSchedule> schedules = zoneSchedules.computeIfAbsent(
                        zoneId, k -> new ConcurrentHashMap<>());

                List<RiderSchedule> candidateSchedules = gatherCandidateSchedules(
                        restaurantLocation, schedules);

                InsertionCandidate best = insertionHeuristic.findBestInsertion(order, candidateSchedules);

                if (best != null && best.isFeasible()) {
                    applyInsertion(best, schedules, order);
                    long elapsed = System.currentTimeMillis() - startTime;
                    metrics.recordAssignment(elapsed);
                    metrics.recordBatchSize(best.getResultingSchedule().uncompletedStopCount() / 2);
                    retryCount.remove(order.getId());
                    log.info("Order {} assigned to rider {} in {}ms (cost: {:.2f})",
                            order.getId(), best.getRiderId(), elapsed, best.getInsertionCost());
                } else {
                    handleUnassignable(order);
                }
            } finally {
                lock.writeLock().unlock();
            }
        } catch (Exception e) {
            log.error("Failed to schedule order {}", order.getId(), e);
        }
    }

    /**
     * Handle orders that cannot be assigned: mark as UNASSIGNABLE and schedule retry (GAP 7).
     */
    private void handleUnassignable(Order order) {
        int attempts = retryCount.merge(order.getId(), 1, Integer::sum);
        int maxAttempts = properties.getRetry().getMaxAttempts();
        long retryDelayMs = properties.getRetry().getDelayMs();

        if (attempts <= maxAttempts) {
            order.setStatus(OrderStatus.UNASSIGNABLE);
            orderRepository.save(order);
            log.warn("No feasible insertion for order {} (attempt {}/{}). Retrying in {}ms",
                    order.getId(), attempts, maxAttempts, retryDelayMs);

            retryExecutor.schedule(() -> retryOrder(order.getId()),
                    retryDelayMs, TimeUnit.MILLISECONDS);
        } else {
            order.setStatus(OrderStatus.UNASSIGNABLE);
            orderRepository.save(order);
            metrics.recordUnassignable();
            retryCount.remove(order.getId());
            log.error("Order {} unassignable after {} attempts — requires manual intervention",
                    order.getId(), maxAttempts);
        }
    }

    /**
     * Retry assignment for a previously unassignable order (GAP 7).
     */
    private void retryOrder(Long orderId) {
        try {
            Order order = orderRepository.findById(orderId).orElse(null);
            if (order == null || order.getStatus() == OrderStatus.CANCELLED) {
                retryCount.remove(orderId);
                return;
            }

            log.info("Retrying assignment for order {}", orderId);
            eventPublisher.publish(new OrderPlacedEvent(order));
        } catch (Exception e) {
            log.error("Retry failed for order {}", orderId, e);
        }
    }

    /**
     * Called when an order is cancelled. Removes the order from the rider's schedule.
     */
    @Async("schedulerExecutor")
    @EventListener
    public void onOrderCancelled(OrderCancelledEvent event) {
        Order order = event.order();
        try {
            Long zoneId = resolveZoneId(order);
            ReentrantReadWriteLock lock = zoneLocks.get(zoneId);
            if (lock == null) return;

            lock.writeLock().lock();
            try {
                ConcurrentHashMap<Long, RiderSchedule> schedules = zoneSchedules.get(zoneId);
                if (schedules == null) return;

                for (Map.Entry<Long, RiderSchedule> entry : schedules.entrySet()) {
                    RiderSchedule schedule = entry.getValue();
                    Set<Long> orderIds = schedule.getUncompletedOrderIds();
                    if (orderIds.contains(order.getId())) {
                        schedule.removeOrderStops(order.getId());
                        constraintEngine.propagateArrivalTimes(schedule);
                        persistAllSchedules(Map.of(entry.getKey(), schedule));
                        eventPublisher.publish(new TripUpdatedEvent(zoneId));
                        metrics.recordCancellation();
                        log.info("Order {} cancelled and removed from rider {}'s schedule",
                                order.getId(), entry.getKey());
                        break;
                    }
                }
            } finally {
                lock.writeLock().unlock();
            }
        } catch (Exception e) {
            log.error("Failed to process cancellation for order {}", order.getId(), e);
        }
    }

    /**
     * Periodic re-optimization. Runs on schedule per zone.
     */
    @Scheduled(fixedDelayString = "${scheduler.optimization.interval-ms:30000}")
    public void periodicOptimization() {
        for (Map.Entry<Long, ConcurrentHashMap<Long, RiderSchedule>> entry : zoneSchedules.entrySet()) {
            Long zoneId = entry.getKey();
            ReentrantReadWriteLock lock = zoneLocks.get(zoneId);
            if (lock == null) continue;

            if (!lock.writeLock().tryLock()) continue; // skip if zone is busy
            try {
                Map<Long, RiderSchedule> schedules = entry.getValue();
                if (schedules.isEmpty()) continue;

                long startTime = System.currentTimeMillis();
                boolean improved = localSearchOptimizer.optimize(schedules);
                long elapsed = System.currentTimeMillis() - startTime;

                metrics.recordOptimization(elapsed);

                if (improved) {
                    persistAllSchedules(schedules);
                    eventPublisher.publish(new TripUpdatedEvent(zoneId));
                    log.debug("Zone {} re-optimized in {}ms", zoneId, elapsed);
                }
            } finally {
                lock.writeLock().unlock();
            }
        }
    }

    /**
     * Gather candidate rider schedules from nearby riders.
     */
    private List<RiderSchedule> gatherCandidateSchedules(
            GeoLocation center, ConcurrentHashMap<Long, RiderSchedule> zoneSchedules) {

        List<RiderWithDistance> nearbyRiders = geofencingService.findNearbyRiders(center);
        List<RiderSchedule> candidates = new ArrayList<>();

        for (RiderWithDistance rwd : nearbyRiders) {
            Long riderId = rwd.riderId();

            // Check rider status
            String status = riderLocationCache.getRiderStatus(riderId);
            if (status == null || RiderStatus.OFFLINE.name().equals(status)) continue;

            // Get or create schedule for this rider
            RiderSchedule schedule = zoneSchedules.computeIfAbsent(riderId, id -> {
                GeoLocation loc = rwd.location();
                return new RiderSchedule(id, loc);
            });

            // Update current location
            schedule.setCurrentLocation(rwd.location());
            candidates.add(schedule);
        }

        return candidates;
    }

    @Transactional
    protected void applyInsertion(InsertionCandidate candidate,
                                   ConcurrentHashMap<Long, RiderSchedule> schedules,
                                   Order order) {
        // Update in-memory schedule
        schedules.put(candidate.getRiderId(), candidate.getResultingSchedule());

        // Persist to database
        Rider rider = riderRepository.findById(candidate.getRiderId()).orElse(null);
        if (rider == null) return;

        // Find or create trip for this rider
        Trip trip = tripRepository.findByRiderIdAndStatusIn(
                        candidate.getRiderId(), List.of(TripStatus.PLANNED, TripStatus.ACTIVE))
                .orElseGet(() -> tripRepository.save(new Trip(rider)));

        // Rebuild trip stops from the schedule
        trip.getStops().clear();
        List<ScheduledStop> scheduledStops = candidate.getResultingSchedule().getStops();
        for (int i = 0; i < scheduledStops.size(); i++) {
            ScheduledStop ss = scheduledStops.get(i);
            TripStop tripStop = new TripStop(
                    i, ss.getType(), ss.getLocation(),
                    ss.getType() == StopType.PICKUP ? order.getRestaurant().getId() : ss.getOrderId(),
                    ss.getTimeWindow(), ss.getServiceTimeSeconds());
            tripStop.setEstimatedArrival(ss.getEstimatedArrival());
            if (ss.isCompleted()) tripStop.markCompleted();
            trip.addStop(tripStop);
        }
        tripRepository.save(trip);

        // Update order
        order.setStatus(OrderStatus.ASSIGNED);
        order.setTrip(trip);
        orderRepository.save(order);

        // Update rider status
        rider.setStatus(RiderStatus.ON_TRIP);
        riderRepository.save(rider);

        eventPublisher.publish(new OrderAssignedEvent(order, candidate.getRiderId()));
    }

    @Transactional
    protected void persistAllSchedules(Map<Long, RiderSchedule> schedules) {
        for (Map.Entry<Long, RiderSchedule> entry : schedules.entrySet()) {
            Long riderId = entry.getKey();
            RiderSchedule schedule = entry.getValue();

            tripRepository.findByRiderIdAndStatusIn(riderId,
                            List.of(TripStatus.PLANNED, TripStatus.ACTIVE))
                    .ifPresent(trip -> {
                        trip.getStops().clear();
                        List<ScheduledStop> stops = schedule.getStops();
                        for (int i = 0; i < stops.size(); i++) {
                            ScheduledStop ss = stops.get(i);
                            TripStop tripStop = new TripStop(
                                    i, ss.getType(), ss.getLocation(),
                                    ss.getOrderId(), ss.getTimeWindow(), ss.getServiceTimeSeconds());
                            tripStop.setEstimatedArrival(ss.getEstimatedArrival());
                            if (ss.isCompleted()) tripStop.markCompleted();
                            trip.addStop(tripStop);
                        }
                        tripRepository.save(trip);
                    });
        }
    }

    /**
     * Rebuild a RiderSchedule from a persisted Trip entity (GAP 8).
     * Used during startup recovery to restore in-memory state.
     */
    private RiderSchedule rebuildScheduleFromTrip(Trip trip) {
        GeoLocation riderLocation = riderLocationCache.getRiderLocation(trip.getRider().getId());
        if (riderLocation == null) {
            // Fallback: use location of first uncompleted stop, or first stop
            riderLocation = trip.getStops().stream()
                    .filter(s -> !s.isCompleted())
                    .findFirst()
                    .map(TripStop::getLocation)
                    .orElse(trip.getStops().get(0).getLocation());
        }

        RiderSchedule schedule = new RiderSchedule(trip.getRider().getId(), riderLocation);
        for (TripStop ts : trip.getStops()) {
            ScheduledStop ss = new ScheduledStop(
                    ts.getReferenceId(), ts.getStopType(), ts.getLocation(),
                    ts.getTimeWindow(), ts.getServiceTimeSeconds());
            ss.setEstimatedArrival(ts.getEstimatedArrival());
            if (ts.isCompleted()) ss.setCompleted(true);
            schedule.getStops().add(ss);
        }

        // Recalculate current stop index
        for (int i = 0; i < schedule.getStops().size(); i++) {
            if (!schedule.getStops().get(i).isCompleted()) {
                schedule.setCurrentStopIndex(i);
                break;
            }
        }

        return schedule;
    }

    private Long resolveZoneId(Order order) {
        return geofencingService.findZoneForLocation(order.getRestaurant().getLocation())
                .map(Zone::getId)
                .orElse(0L); // default zone if not found
    }

    // Exposed for simulation
    public ConcurrentHashMap<Long, ConcurrentHashMap<Long, RiderSchedule>> getZoneSchedules() {
        return zoneSchedules;
    }
}
