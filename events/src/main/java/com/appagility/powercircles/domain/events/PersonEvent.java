package com.appagility.powercircles.domain.events;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "_type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PersonCreatedEvent.class, name = PersonCreatedEvent.TYPE),
})
@AllArgsConstructor
public abstract class PersonEvent<TDetail extends PersonEventDetail> {

    @Getter
    private final String personId;
    @Getter
    private final int sequenceNumber;
    @Getter
    private final TDetail detail;

    public abstract void accept(PersonEventVisitor eventVisitor);
}