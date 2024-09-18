package com.appagility.powercircles.commandhandlers.infrastructure.aws;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.appagility.powercircles.commandhandlers.domain.commands.PersonCreateCommand;
import com.appagility.powercircles.commandhandlers.services.PersonServices;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class PersonHandler implements RequestStreamHandler {

    private ObjectMapper objectMapper = new ObjectMapper();

    private PersonServices personServices = new PersonServices();

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {

        var personCreateCommand = objectMapper.readValue(inputStream, PersonCreateCommand.class);
        personServices.handle(personCreateCommand);
    }
}
