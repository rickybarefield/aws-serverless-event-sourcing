package com.appagility.powercircles.commandhandlers.infrastructure.aws;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.appagility.powercircles.commandhandlers.domain.commands.PersonCreateCommand;
import com.appagility.powercircles.commandhandlers.domain.commands.PersonCreateResponse;
import com.appagility.powercircles.commandhandlers.services.PersonServices;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class PersonHandler implements RequestHandler<PersonCreateCommand, PersonCreateResponse> {

    private PersonServices personServices = new PersonServices();

    @Override
    public PersonCreateResponse handleRequest(PersonCreateCommand personCreateCommand, Context context) {

        return personServices.handle(personCreateCommand);
    }
}
