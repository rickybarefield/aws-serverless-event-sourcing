package com.appagility.powercircles.summaryprojection;

import com.appagility.powercircles.connectionfactories.H2ConnectionFactory;
import com.appagility.powercircles.domain.events.PersonCreatedEvent;
import com.appagility.powercircles.domain.events.PersonCreatedEventDetail;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

public class RoundCircleTest {

    @BeforeAll
    public static void createDatabase() {

        try (var schemaResource = SummaryProjection.class.getClassLoader().getResourceAsStream("schema.sql")) {

            var schema = new String(schemaResource.readAllBytes(), StandardCharsets.UTF_8);

            try(var connection = new H2ConnectionFactory().create()) {

                var statement = connection.createStatement();
                statement.execute(schema);
            }

        } catch (IOException | SQLException e) {

            throw new RuntimeException(e);
        }
    }

    @Test
    public void testHandleEventThenQuery() {

        var personName = "test-name";
        var personId = "test-id";

        var event = new PersonCreatedEvent(personId, 1, new PersonCreatedEventDetail(personName));

        new PersonSummaryEventHandler(new PersonSummaryDao(new H2ConnectionFactory())).visit(event);

        var retrieved = new QueryHandler(new PersonSummaryDao(new H2ConnectionFactory())).getAll();

        Assertions.assertEquals(1, retrieved.size());

        var retrievedPersonSummary = retrieved.getFirst();

        Assertions.assertEquals(personName, retrievedPersonSummary.getName());
        Assertions.assertEquals(personId, retrievedPersonSummary.getPersonId());
    }

}
