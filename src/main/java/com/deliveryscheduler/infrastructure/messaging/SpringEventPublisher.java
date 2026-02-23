package com.deliveryscheduler.infrastructure.messaging;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Spring ApplicationEvent-based implementation of EventPublisher.
 * Publishes events to in-process listeners via @EventListener.
 */
@Component
public class SpringEventPublisher implements EventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public SpringEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(Object event) {
        applicationEventPublisher.publishEvent(event);
    }
}
