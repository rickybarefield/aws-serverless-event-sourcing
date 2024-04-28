package com.appagility.powercircles.commandhandlers.infrastructure.aws;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.appagility.powercircles.commandhandlers.repository.PersonEventRepository;
import com.appagility.powercircles.domain.events.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class DynamoDbPersonEventRepository implements PersonEventRepository, PersonEventVisitor {


    public static final String TABLE_NAME = System.getenv("PERSON_TABLE_NAME");
    public static final String TYPE_KEY = "_type";
    public static final String PERSON_ID_KEY = "personId";
    public static final String SEQUENCE_NUMBER_KEY = "sequenceNumber";
    public static final String DETAIL_KEY = "detail";
    final DynamoDB dynamo = new DynamoDB(AmazonDynamoDBClientBuilder.defaultClient());

    @Override
    public Stream<PersonEvent<? extends PersonEventDetail>> load(String personId) {

        var querySpec = new QuerySpec()
                .withKeyConditionExpression("id = :id")
                .withValueMap(new ValueMap().withString(":id", personId));

        var items = dynamo.getTable(TABLE_NAME).query(querySpec);

        var itemStream = StreamSupport.stream(items.spliterator(), false);

        return itemStream.map(this::convert);
    }

    private PersonEvent<? extends PersonEventDetail> convert(Item item) {

        var type = item.getString(TYPE_KEY);

        Optional<PersonEvent> possibleEvent = switch (type) {

            case PersonCreatedEvent.TYPE -> Optional.of(convertPersonCreatedEvent(item));
            default -> Optional.empty();
        };

        return possibleEvent
                .orElseThrow(() -> new RuntimeException("Event returned of unknown type: " + type));
    }

    private PersonEvent convertPersonCreatedEvent(Item item) {

        var objectMapper = new ObjectMapper();

        try {

            var detail = objectMapper.reader().readValue(item.getString(DETAIL_KEY), PersonCreatedEventDetail.class);

            return new PersonCreatedEvent(
                    item.getString(PERSON_ID_KEY),
                    item.getInt(SEQUENCE_NUMBER_KEY),
                    detail);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public void save(List<PersonEvent<? extends PersonEventDetail>> personEvents) {

        personEvents.forEach(event -> event.accept(this));
    }

    @Override
    public void visit(PersonCreatedEvent personCreatedEvent) {

        var objectMapper = new ObjectMapper();
        try {

            var detail = objectMapper.writer().writeValueAsString(personCreatedEvent.getDetail());

            var item = new Item()
                    .withString(TYPE_KEY, PersonCreatedEvent.TYPE)
                    .withString(PERSON_ID_KEY, personCreatedEvent.getPersonId())
                    .withInt(SEQUENCE_NUMBER_KEY, personCreatedEvent.getSequenceNumber())
                    .withJSON(DETAIL_KEY, detail);

            dynamo.getTable(TABLE_NAME).putItem(item);

        } catch (JsonProcessingException e) {

            throw new RuntimeException(e);
        }

    }
}
