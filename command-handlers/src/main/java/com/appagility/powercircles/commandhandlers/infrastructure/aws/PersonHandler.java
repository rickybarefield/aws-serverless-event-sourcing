package com.appagility.powercircles.commandhandlers.infrastructure.aws;

import com.amazonaws.services.lambda.runtime.Context;
import com.appagility.powercircles.commandhandlers.domain.commands.PersonCreateCommand;
import com.appagility.powercircles.commandhandlers.domain.Person;
import com.appagility.powercircles.commandhandlers.services.PersonServices;

public class PersonHandler {

    private PersonServices personServices = new PersonServices();

    public void handleRequest(PersonCreateCommand createCommand, Context context) {


        personServices.handle(createCommand);
    }
}
