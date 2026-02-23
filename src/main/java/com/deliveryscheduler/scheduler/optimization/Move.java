package com.deliveryscheduler.scheduler.optimization;

import java.util.Set;

/**
 * Represents a single move in the local search neighborhood.
 */
public class Move {

    public enum MoveType {
        RELOCATE,   // move order from one rider to another
        TWO_OPT,    // reverse a segment within a rider's route
        OR_OPT      // move a chain of stops within a rider's route
    }

    private final MoveType type;
    private final Long sourceRiderId;
    private final Long targetRiderId; // null for intra-route moves
    private final Long orderId;       // for RELOCATE
    private final int segmentStart;   // for TWO_OPT and OR_OPT
    private final int segmentEnd;     // for TWO_OPT and OR_OPT
    private final int targetIndex;    // for OR_OPT: where to insert the chain

    // RELOCATE constructor
    public Move(Long sourceRiderId, Long targetRiderId, Long orderId) {
        this.type = MoveType.RELOCATE;
        this.sourceRiderId = sourceRiderId;
        this.targetRiderId = targetRiderId;
        this.orderId = orderId;
        this.segmentStart = -1;
        this.segmentEnd = -1;
        this.targetIndex = -1;
    }

    // TWO_OPT constructor
    public Move(MoveType type, Long riderId, int segmentStart, int segmentEnd) {
        this.type = type;
        this.sourceRiderId = riderId;
        this.targetRiderId = null;
        this.orderId = null;
        this.segmentStart = segmentStart;
        this.segmentEnd = segmentEnd;
        this.targetIndex = -1;
    }

    // OR_OPT constructor
    public Move(Long riderId, int segmentStart, int segmentEnd, int targetIndex) {
        this.type = MoveType.OR_OPT;
        this.sourceRiderId = riderId;
        this.targetRiderId = null;
        this.orderId = null;
        this.segmentStart = segmentStart;
        this.segmentEnd = segmentEnd;
        this.targetIndex = targetIndex;
    }

    public Set<Long> getAffectedRiders() {
        if (targetRiderId != null) {
            return Set.of(sourceRiderId, targetRiderId);
        }
        return Set.of(sourceRiderId);
    }

    public MoveType getType() { return type; }
    public Long getSourceRiderId() { return sourceRiderId; }
    public Long getTargetRiderId() { return targetRiderId; }
    public Long getOrderId() { return orderId; }
    public int getSegmentStart() { return segmentStart; }
    public int getSegmentEnd() { return segmentEnd; }
    public int getTargetIndex() { return targetIndex; }
}
