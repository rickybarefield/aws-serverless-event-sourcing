package com.appagility.powercircles.summaryprojection;

import com.appagility.powercircles.domain.events.PersonCreatedEvent;
import jakarta.persistence.EntityManager;

public class EventHandler {

    public void handle(PersonCreatedEvent personCreatedEvent) {

        try(EntityManager entityManager =  JpaInfrastructure.createEntityManager()) {

            var summary = PersonSummary.builder()
                    .id(personCreatedEvent.getPersonId())
                    .name(personCreatedEvent.getName())
                    .build();

            entityManager.persist(summary);
        }

    }
}
