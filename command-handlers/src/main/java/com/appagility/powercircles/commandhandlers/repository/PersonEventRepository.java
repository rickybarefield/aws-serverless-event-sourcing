package com.appagility.powercircles.commandhandlers.repository;


import com.appagility.powercircles.domain.events.PersonCreatedEventDetail;
import com.appagility.powercircles.domain.events.PersonEvent;
import com.appagility.powercircles.domain.events.PersonEventDetail;

import java.util.List;
import java.util.stream.Stream;

public interface PersonEventRepository {

    Stream<PersonEvent<? extends PersonEventDetail>> load(String personId);

    void save(List<PersonEvent<? extends PersonEventDetail>> personEvents);
}
