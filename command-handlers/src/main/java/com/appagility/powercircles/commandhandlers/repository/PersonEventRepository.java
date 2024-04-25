package com.appagility.powercircles.commandhandlers.repository;



import com.appagility.powercircles.domain.events.PersonEvent;

import java.util.stream.Stream;

public interface PersonEventRepository {

    Stream<PersonEvent> load(String personId);

    void save(Stream<PersonEvent> personEvents);
}
