package com.appagility.powercircles.domain.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;

import java.util.UUID;

public class PersonCreatedEvent extends PersonEvent<PersonCreatedEventDetail> {

    public final static String TYPE = "PersonCreatedEvent";

    @JsonCreator
    public PersonCreatedEvent(@JsonProperty("personId") String personId, @JsonProperty("sequenceNumber") int sequenceNumber, @JsonProperty("detail") PersonCreatedEventDetail personCreatedEventDetail) {
        super(personId, sequenceNumber, personCreatedEventDetail);
    }

    @Override
    public void accept(PersonEventVisitor eventVisitor) {

        eventVisitor.visit(this);
    }

    public static PersonCreatedEvent forNewAggregate(String name) {

        return new PersonCreatedEvent(UUID.randomUUID().toString(),
                1,
                new PersonCreatedEventDetail(name));
    }
}
