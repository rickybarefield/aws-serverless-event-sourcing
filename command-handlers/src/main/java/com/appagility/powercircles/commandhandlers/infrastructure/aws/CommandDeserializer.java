package com.appagility.powercircles.commandhandlers.infrastructure.aws;

import com.appagility.powercircles.commandhandlers.domain.commands.PersonCreateCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.Converter;

public class CommandDeserializer implements Converter<String, PersonCreateCommand> {

    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public PersonCreateCommand convert(String input) {

        try {

            return objectMapper.readValue(input, PersonCreateCommand.class);

        } catch (JsonProcessingException e) {

            throw new RuntimeException(e);
        }
    }

    @Override
    public JavaType getInputType(TypeFactory typeFactory) {

        return typeFactory.constructSimpleType(String.class, null);
    }

    @Override
    public JavaType getOutputType(TypeFactory typeFactory) {

        return typeFactory.constructSimpleType(PersonCreateCommand.class, null);
    }
}
