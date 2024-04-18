package com.appagility.powercircles.commandhandlers.infrastructure.aws;

import com.appagility.powercircles.commandhandlers.domain.commands.PersonCreateCommand;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Record(@JsonProperty("body") @JsonDeserialize(converter = CommandDeserializer.class) PersonCreateCommand personCreateCommand) {

}
