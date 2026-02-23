package com.deliveryscheduler.scheduler;

import com.deliveryscheduler.config.SchedulerProperties;
import com.deliveryscheduler.domain.event.OrderAssignedEvent;
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
import com.deliveryscheduler.scheduler.constraint.ConstraintEngine;
import com.deliveryscheduler.scheduler.insertion.InsertionHeuristic;
import com.deliveryscheduler.scheduler.model.InsertionCandidate;
import com.deliveryscheduler.scheduler.model.RiderSchedule;
import com.deliveryscheduler.scheduler.model.ScheduledStop;
import com.deliveryscheduler.scheduler.optimization.LocalSearchOptimizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

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

    // In-memory state: current working schedules per zone
    private final ConcurrentHashMap<Long, ConcurrentHashMap<Long, RiderSchedule>> zoneSchedules = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ReentrantReadWriteLock> zoneLocks = new ConcurrentHashMap<>();

    public SchedulerOrchestrator(InsertionHeuristic insertionHeuristic,
                                  LocalSearchOptimizer localSearchOptimizer,
                                  ConstraintEngine constraintEngine,
                                  GeofencingService geofencingService,
                                  RiderLocationCache riderLocationCache,
                                  RiderRepository riderRepository,
                                  TripRepository tripRepository,
                                  OrderRepository orderRepository,
                                  EventPublisher eventPublisher,
                                  SchedulerProperties properties) {
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
                    log.info("Order {} assigned to rider {} in {}ms (cost: {:.2f})",
                            order.getId(), best.getRiderId(), elapsed, best.getInsertionCost());
                } else {
                    log.warn("No feasible insertion found for order {}", order.getId());
                }
            } finally {
                lock.writeLock().unlock();
            }
        } catch (Exception e) {
            log.error("Failed to schedule order {}", order.getId(), e);
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

                boolean improved = localSearchOptimizer.optimize(schedules);
                if (improved) {
                    persistAllSchedules(schedules);
                    eventPublisher.publish(new TripUpdatedEvent(zoneId));
                    log.debug("Zone {} re-optimized", zoneId);
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
