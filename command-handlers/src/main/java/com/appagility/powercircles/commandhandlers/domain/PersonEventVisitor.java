package com.appagility.powercircles.commandhandlers.domain;

import com.appagility.powercircles.commandhandlers.domain.events.PersonCreatedEvent;

public interface PersonEventVisitor {

    void visit(PersonCreatedEvent personCreatedEvent);
}
