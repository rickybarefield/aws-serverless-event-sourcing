package com.appagility.powercircles.commandhandlers.infrastructure.aws;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.appagility.powercircles.commandhandlers.repository.PersonEventRepository;
import com.appagility.powercircles.domain.events.PersonCreatedEvent;
import com.appagility.powercircles.domain.events.PersonEvent;
import com.appagility.powercircles.domain.events.PersonEventVisitor;

import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class DynamoDbPersonEventRepository implements PersonEventRepository, PersonEventVisitor {


    public static final String TABLE_NAME = System.getenv("PERSON_TABLE_NAME");
    public static final String TYPE_KEY = "_type";
    public static final String PERSON_CREATED_EVENT_TYPE = "PersonCreatedEvent";
    public static final String ID_KEY = "id";
    public static final String SEQUENCE_NUMBER_KEY = "sequenceNumber";
    public static final String NAME_KEY = "name";
    final DynamoDB dynamo = new DynamoDB(AmazonDynamoDBClientBuilder.defaultClient());

    @Override
    public Stream<PersonEvent> load(String personId) {

        var querySpec = new QuerySpec()
                .withKeyConditionExpression("id = :id")
                .withValueMap(new ValueMap().withString(":id", personId));

        var items = dynamo.getTable(TABLE_NAME).query(querySpec);

        var itemStream = StreamSupport.stream(items.spliterator(), false);

        return itemStream.map(this::convert);
    }

    private PersonEvent convert(Item item) {

        var type = item.getString(TYPE_KEY);

        Optional<PersonEvent> possibleEvent = switch (type) {

            case PERSON_CREATED_EVENT_TYPE -> Optional.of(convertPersonCreatedEvent(item));
            default -> Optional.empty();
        };

        return possibleEvent
                .orElseThrow(() -> new RuntimeException("Event returned of unknown type: " + type));
    }

    private PersonEvent convertPersonCreatedEvent(Item item) {


        return new PersonCreatedEvent(
                item.getString(ID_KEY),
                item.getInt(SEQUENCE_NUMBER_KEY),
                true,
                item.getString(NAME_KEY));
    }

    @Override
    public void save(Stream<PersonEvent> personEvents) {

        personEvents.forEach(event -> event.accept(this));
    }

    @Override
    public void visit(PersonCreatedEvent personCreatedEvent) {

        var item = new Item()
                .withString(TYPE_KEY, PERSON_CREATED_EVENT_TYPE)
                .withString(ID_KEY, personCreatedEvent.getPersonId())
                .withInt(SEQUENCE_NUMBER_KEY, personCreatedEvent.getSequenceNumber())
                .withString(NAME_KEY, personCreatedEvent.getName());

        dynamo.getTable(TABLE_NAME).putItem(item);
    }
}
