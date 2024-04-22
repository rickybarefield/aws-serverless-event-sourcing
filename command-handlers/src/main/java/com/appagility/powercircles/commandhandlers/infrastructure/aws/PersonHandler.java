package com.appagility.powercircles.commandhandlers.infrastructure.aws;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.appagility.powercircles.commandhandlers.domain.commands.PersonCreateCommand;
import com.appagility.powercircles.commandhandlers.domain.Person;
import com.appagility.powercircles.commandhandlers.services.PersonServices;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.security.auth.login.CredentialException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

public class PersonHandler implements RequestStreamHandler {

    private ObjectMapper objectMapper = new ObjectMapper();

    private PersonServices personServices = new PersonServices();

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {

        var personCreateCommand = objectMapper.readValue(inputStream, PersonCreateCommand.class);
        personServices.handle(personCreateCommand);
    }
}
