package com.appagility.powercircles.summaryprojection;

import com.appagility.powercircles.domain.events.PersonCreatedEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RoundCircleTest {


    @Test
    public void testHandleEventThenQuery() {

        var event = new PersonCreatedEvent("test-id", 1, true, "test-name");

        new EventHandler().handle(event);

        var retrieved = new QueryHandler().getAll();

        Assertions.assertEquals(1, retrieved.size());
    }

}
