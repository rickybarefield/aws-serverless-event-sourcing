package com.appagility.powercircles.summaryprojection.infrastructure.aws;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.appagility.powercircles.connectionfactories.RdsPostgresIamAuthConnectionFactory;
import com.appagility.powercircles.domain.events.PersonEvent;
import com.appagility.powercircles.summaryprojection.PersonSummaryDao;
import com.appagility.powercircles.summaryprojection.PersonSummaryEventHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

public class LambdaEventHandler implements RequestStreamHandler {

    private PersonSummaryEventHandler eventHandler =
            new PersonSummaryEventHandler(new PersonSummaryDao(new RdsPostgresIamAuthConnectionFactory()));

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {

        var gson = new Gson();

        var sqsMessage = gson.fromJson(new InputStreamReader(inputStream), JsonObject.class);

        System.out.println(sqsMessage);

        var records = sqsMessage.get("Records").getAsJsonArray();

        for(JsonElement record : records) {

            var recordBody = record.getAsJsonObject().get("body").getAsString();

            var snsMessage = gson.fromJson(recordBody, JsonObject.class);

            var message = snsMessage.get("Message").getAsString();

            ObjectMapper objectMapper = new ObjectMapper();

            PersonEvent<?> personEvent = objectMapper.reader().readValue(message, PersonEvent.class);

            personEvent.accept(eventHandler);
        }

    }
}
