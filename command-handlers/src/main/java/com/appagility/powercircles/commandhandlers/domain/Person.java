package com.appagility.powercircles.commandhandlers.domain;

import com.appagility.powercircles.commandhandlers.domain.commands.PersonCreateCommand;
import com.appagility.powercircles.commandhandlers.domain.events.PersonCreatedEvent;
import com.appagility.powercircles.commandhandlers.domain.events.PersonEvent;

import java.util.ArrayList;
import java.util.List;

public class Person implements PersonEventVisitor {

    private String id;
    private String name;

    private final List<PersonEvent> events = new ArrayList<>();

    private Person() {}

    public Person(List<PersonEvent> events) {

        events.forEach(e -> e.accept(this));
    }

    public static Person handle(PersonCreateCommand createCommand) {

        var person = new Person();
        new PersonCreatedEvent(createCommand.name()).accept(person);
        return person;
    }

    public void visit(PersonCreatedEvent personCreatedEvent) {

        this.id = personCreatedEvent.getPersonId();
        this.name = personCreatedEvent.getName();
        this.events.add(personCreatedEvent);
    }

    public List<PersonEvent> getEvents() {

        return new ArrayList<>(events);
    }
}
