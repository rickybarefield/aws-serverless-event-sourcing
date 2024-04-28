package com.appagility.powercircles.summaryprojection;

import com.appagility.powercircles.domain.events.PersonCreatedEvent;
import com.appagility.powercircles.domain.events.PersonCreatedEventDetail;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.SQLException;

public class RoundCircleTest {

    @BeforeAll
    public static void createDatabase() {

        try (var schemaResource = SummaryProjection.class.getClassLoader().getResourceAsStream("schema.sql")) {

            var schema = new String(schemaResource.readAllBytes(), StandardCharsets.UTF_8);

            var connection = DriverManager.getConnection("jdbc:h2:mem:db1;DB_CLOSE_DELAY=-1", "sa", "");
            var statement = connection.createStatement();
            statement.execute(schema);

        } catch (IOException | SQLException e) {

            throw new RuntimeException(e);
        }
    }

    @Test
    public void testHandleEventThenQuery() {

        var personName = "test-name";
        var personId = "test-id";

        var event = new PersonCreatedEvent(personId, 1, new PersonCreatedEventDetail(personName));

        new EventHandler().handle(event);

        var retrieved = new QueryHandler().getAll();

        Assertions.assertEquals(1, retrieved.size());

        var retrievedPersonSummary = retrieved.getFirst();

        Assertions.assertEquals(personName, retrievedPersonSummary.getName());
        Assertions.assertEquals(personId, retrievedPersonSummary.getId());
    }

}
