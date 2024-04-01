package com.appagility.powercircles.commandhandlers;

import com.amazonaws.services.lambda.runtime.Context;

public class PersonHandler {

    public void handleRequest(Person person, Context context) {

        System.out.println(person.name());
    }
}
