package com.appagility.powercircles.domain.events;


import lombok.Getter;

public abstract class PersonEvent {

    @Getter
    private final String personId;
    @Getter
    private final int sequenceNumber;

    @Getter
    private final boolean persisted;

    public PersonEvent(String personId, int sequenceNumber, boolean persisted) {

        this.personId = personId;
        this.sequenceNumber = sequenceNumber;
        this.persisted = persisted;
    }

    public abstract void accept(PersonEventVisitor personEventVisitor);
}
