package com.deliveryscheduler.api.rest;

import com.deliveryscheduler.domain.model.OrderStatus;
import com.deliveryscheduler.domain.model.RiderStatus;
import com.deliveryscheduler.domain.repository.OrderRepository;
import com.deliveryscheduler.domain.repository.RiderRepository;
import com.deliveryscheduler.scheduler.SchedulerOrchestrator;
import com.deliveryscheduler.scheduler.model.RiderSchedule;
import com.deliveryscheduler.simulation.SimulationConfig;
import com.deliveryscheduler.simulation.SimulationReport;
import com.deliveryscheduler.simulation.SimulationRunner;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final OrderRepository orderRepository;
    private final RiderRepository riderRepository;
    private final SchedulerOrchestrator schedulerOrchestrator;
    private final SimulationRunner simulationRunner;

    public AdminController(OrderRepository orderRepository,
                           RiderRepository riderRepository,
                           SchedulerOrchestrator schedulerOrchestrator,
                           SimulationRunner simulationRunner) {
        this.orderRepository = orderRepository;
        this.riderRepository = riderRepository;
        this.schedulerOrchestrator = schedulerOrchestrator;
        this.simulationRunner = simulationRunner;
    }

    public record SchedulerMetrics(
            int activeZones,
            int totalActiveSchedules,
            int pendingOrders,
            int assignedOrders,
            int availableRiders,
            int onTripRiders) {}

    @GetMapping("/scheduler/metrics")
    public ResponseEntity<SchedulerMetrics> getSchedulerMetrics() {
        var zoneSchedules = schedulerOrchestrator.getZoneSchedules();

        int activeZones = zoneSchedules.size();
        int totalSchedules = zoneSchedules.values().stream()
                .mapToInt(ConcurrentHashMap::size).sum();
        int pendingOrders = orderRepository.findByStatus(OrderStatus.PLACED).size();
        int assignedOrders = orderRepository.findByStatus(OrderStatus.ASSIGNED).size();
        int availableRiders = riderRepository.findByStatusIn(List.of(RiderStatus.AVAILABLE)).size();
        int onTripRiders = riderRepository.findByStatusIn(List.of(RiderStatus.ON_TRIP)).size();

        return ResponseEntity.ok(new SchedulerMetrics(
                activeZones, totalSchedules, pendingOrders,
                assignedOrders, availableRiders, onTripRiders));
    }

    @PostMapping("/simulation/run")
    public ResponseEntity<SimulationReport> runSimulation(
            @RequestBody(required = false) SimulationConfig config) {
        if (config == null) config = new SimulationConfig();
        SimulationReport report = simulationRunner.run(config);
        return ResponseEntity.ok(report);
    }
}
