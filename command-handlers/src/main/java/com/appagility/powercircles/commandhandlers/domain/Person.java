package com.appagility.powercircles.commandhandlers.domain;

import com.appagility.powercircles.commandhandlers.domain.commands.PersonCreateCommand;
import com.appagility.powercircles.domain.events.PersonCreatedEvent;
import com.appagility.powercircles.domain.events.PersonEvent;
import com.appagility.powercircles.domain.events.PersonEventDetail;
import com.appagility.powercircles.domain.events.PersonEventVisitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Person implements PersonEventVisitor {

    private String id;
    private String name;

    private final List<PersonEvent<? extends PersonEventDetail>> events;
    private final List<PersonEvent<? extends PersonEventDetail>> newEvents = new ArrayList<>();


    public Person(List<PersonEvent<? extends PersonEventDetail>> events) {

        this.events = new ArrayList<>(events);
        this.events.forEach(e -> e.accept(this));
    }

    private Person() {
        this(Collections.emptyList());
    }

    public static Person handle(PersonCreateCommand createCommand) {

        var person = new Person();
        var event = PersonCreatedEvent.forNewAggregate(createCommand.name());
        event.accept(person);
        person.newEvents.add(event);
        return person;
    }

    public void visit(PersonCreatedEvent personCreatedEvent) {

        this.id = personCreatedEvent.getId();
        this.name = personCreatedEvent.getDetail().getName();
    }

    public List<PersonEvent<? extends PersonEventDetail>> getNewEvents() {

        return new ArrayList<>(newEvents);
    }
}
