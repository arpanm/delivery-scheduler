package com.deliveryscheduler.domain.model;

import jakarta.persistence.Embeddable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Embeddable
public class TimeWindow {

    private Instant earliest;
    private Instant latest;

    protected TimeWindow() {}

    public TimeWindow(Instant earliest, Instant latest) {
        this.earliest = earliest;
        this.latest = latest;
    }

    public boolean contains(Instant time) {
        return !time.isBefore(earliest) && !time.isAfter(latest);
    }

    public boolean isViolatedBy(Instant time) {
        return time.isAfter(latest);
    }

    public long slackSeconds(Instant time) {
        return Duration.between(time, latest).getSeconds();
    }

    public Instant getEarliest() {
        return earliest;
    }

    public Instant getLatest() {
        return latest;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TimeWindow that)) return false;
        return Objects.equals(earliest, that.earliest) && Objects.equals(latest, that.latest);
    }

    @Override
    public int hashCode() {
        return Objects.hash(earliest, latest);
    }

    @Override
    public String toString() {
        return "TimeWindow[" + earliest + " -> " + latest + "]";
    }
}
