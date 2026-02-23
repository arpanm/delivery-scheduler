package com.deliveryscheduler.infrastructure.messaging;

/**
 * Interface for publishing domain events. Implementations may use
 * Spring ApplicationEvents, Kafka, or other messaging systems.
 */
public interface EventPublisher {

    void publish(Object event);
}
