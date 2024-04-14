package com.appagility.powercircles.commandhandlers.domain.events;

import com.appagility.powercircles.commandhandlers.domain.PersonEventVisitor;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

public class PersonCreatedEvent extends PersonEvent {

    public final static String TYPE = "PersonCreatedEvent";

    @Getter
    private final String name;

    public PersonCreatedEvent(String name) {

        super(UUID.randomUUID().toString(), 1, false);
        this.name = name;
    }

    public PersonCreatedEvent(String personId, int sequenceNumber, boolean persisted, String name) {

        super(personId, sequenceNumber, persisted);
        this.name = name;
    }

    @Override
    public void accept(PersonEventVisitor personEventVisitor) {

        personEventVisitor.visit(this);
    }
}
