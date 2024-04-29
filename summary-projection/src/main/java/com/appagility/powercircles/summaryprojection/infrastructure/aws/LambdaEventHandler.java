package com.appagility.powercircles.summaryprojection.infrastructure.aws;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.appagility.powercircles.domain.events.PersonEvent;
import com.appagility.powercircles.summaryprojection.PersonSummaryDao;
import com.appagility.powercircles.summaryprojection.PersonSummaryEventHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class LambdaEventHandler implements RequestStreamHandler {

    private PersonSummaryEventHandler eventHandler =
            new PersonSummaryEventHandler(new PersonSummaryDao(new RdsConnectionFactory()));

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {

        ObjectMapper objectMapper = new ObjectMapper();

        PersonEvent<?> personEvent = objectMapper.reader().readValue(inputStream, PersonEvent.class);

        personEvent.accept(eventHandler);
    }
}
