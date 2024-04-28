package com.appagility.powercircles.domain.events;

public interface PersonEventVisitor {

    void visit(PersonCreatedEvent personCreatedEvent);
}
