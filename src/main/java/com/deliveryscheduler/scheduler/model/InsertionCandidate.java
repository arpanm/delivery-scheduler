package com.deliveryscheduler.scheduler.model;

/**
 * Result of evaluating the insertion of a new order into a rider's schedule.
 */
public class InsertionCandidate {

    private final Long riderId;
    private final int pickupInsertionIndex;
    private final int deliveryInsertionIndex;
    private final double insertionCost;
    private final boolean feasible;
    private final RiderSchedule resultingSchedule;

    public InsertionCandidate(Long riderId, int pickupInsertionIndex, int deliveryInsertionIndex,
                              double insertionCost, boolean feasible, RiderSchedule resultingSchedule) {
        this.riderId = riderId;
        this.pickupInsertionIndex = pickupInsertionIndex;
        this.deliveryInsertionIndex = deliveryInsertionIndex;
        this.insertionCost = insertionCost;
        this.feasible = feasible;
        this.resultingSchedule = resultingSchedule;
    }

    public Long getRiderId() { return riderId; }
    public int getPickupInsertionIndex() { return pickupInsertionIndex; }
    public int getDeliveryInsertionIndex() { return deliveryInsertionIndex; }
    public double getInsertionCost() { return insertionCost; }
    public boolean isFeasible() { return feasible; }
    public RiderSchedule getResultingSchedule() { return resultingSchedule; }
}
