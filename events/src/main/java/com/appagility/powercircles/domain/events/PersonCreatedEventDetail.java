package com.appagility.powercircles.domain.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

public class PersonCreatedEventDetail implements PersonEventDetail {

    @Getter
    private final String name;

    @JsonCreator
    public PersonCreatedEventDetail(@JsonProperty("name") String name) {

        this.name = name;
    }
}
