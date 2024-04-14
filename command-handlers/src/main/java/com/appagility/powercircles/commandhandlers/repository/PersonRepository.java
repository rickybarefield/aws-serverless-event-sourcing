package com.appagility.powercircles.commandhandlers.repository;

import com.appagility.powercircles.commandhandlers.domain.Person;
import com.appagility.powercircles.commandhandlers.infrastructure.aws.DynamoDbPersonEventRepository;

public final class PersonRepository {

    //TODO Implement IoC here to avoid dependency from domain to infrastructure
    private PersonEventRepository personEventRepository = new DynamoDbPersonEventRepository();

    public Person load(String id) {

        var events = personEventRepository.load(id);

        var person = new Person(events.toList());

        return person;
    }

    public void save(Person person) {

        var newEvents = person.getEvents().stream().filter(e -> !e.isPersisted());
        personEventRepository.save(newEvents);
    }
}
