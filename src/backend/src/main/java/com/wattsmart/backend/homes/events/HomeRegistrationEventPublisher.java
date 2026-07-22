package com.wattsmart.backend.homes.events;

public interface HomeRegistrationEventPublisher {

    void publish(HomeRegistrationEvent event);
}
