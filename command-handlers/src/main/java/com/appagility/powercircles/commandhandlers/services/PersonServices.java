package com.appagility.powercircles.commandhandlers.services;

import com.appagility.powercircles.commandhandlers.domain.Person;
import com.appagility.powercircles.commandhandlers.domain.commands.PersonCreateCommand;
import com.appagility.powercircles.commandhandlers.domain.commands.PersonCreateResponse;
import com.appagility.powercircles.commandhandlers.repository.PersonRepository;

public class PersonServices {

    private PersonRepository personRepository = new PersonRepository();

    public PersonCreateResponse handle(PersonCreateCommand createCommand) {

        var person = Person.handle(createCommand);

        personRepository.save(person);

        return new PersonCreateResponse(person.getId(), person.getName());
    }
}
