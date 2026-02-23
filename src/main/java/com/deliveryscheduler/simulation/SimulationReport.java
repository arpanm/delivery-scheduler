package com.deliveryscheduler.simulation;

/**
 * Summary of a simulation run's KPIs.
 */
public record SimulationReport(
        int totalOrdersPlaced,
        int totalOrdersAssigned,
        int totalOrdersDelivered,
        double slaAdherencePercent,
        double avgDeliveryTimeMinutes,
        double p50DeliveryTimeMinutes,
        double p95DeliveryTimeMinutes,
        double p50InsertionLatencyMs,
        double p95InsertionLatencyMs,
        double p99InsertionLatencyMs,
        int unassignableOrders,
        int totalPickupsCompleted,
        int totalDeliveriesCompleted) {

    public String toFormattedString() {
        return String.format("""
                ===== SIMULATION REPORT =====
                Orders placed:        %d
                Orders assigned:      %d
                Orders delivered:     %d
                Unassignable:         %d

                SLA adherence:        %.1f%%
                Avg delivery time:    %.1f min
                P50 delivery time:    %.1f min
                P95 delivery time:    %.1f min

                Insertion latency:
                  P50: %.1f ms
                  P95: %.1f ms
                  P99: %.1f ms

                Pickups completed:    %d
                Deliveries completed: %d
                =============================
                """,
                totalOrdersPlaced, totalOrdersAssigned, totalOrdersDelivered,
                unassignableOrders,
                slaAdherencePercent,
                avgDeliveryTimeMinutes, p50DeliveryTimeMinutes, p95DeliveryTimeMinutes,
                p50InsertionLatencyMs, p95InsertionLatencyMs, p99InsertionLatencyMs,
                totalPickupsCompleted, totalDeliveriesCompleted);
    }
}
