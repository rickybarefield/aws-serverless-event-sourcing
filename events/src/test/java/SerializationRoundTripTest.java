import com.appagility.powercircles.domain.events.PersonCreatedEvent;
import com.appagility.powercircles.domain.events.PersonEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class SerializationRoundTripTest {


    @Test
    public void serializeAndDeserialize() throws IOException {

        var personCreatedEvent = PersonCreatedEvent.forNewAggregate("test-name");

        var objectMapperWriter = new ObjectMapper();

        var serialized = objectMapperWriter.writer().writeValueAsString(personCreatedEvent);

        var objectMapperReader = new ObjectMapper();

        var personCreatedEventDeserialized = objectMapperReader.reader().readValue(serialized, PersonEvent.class);

        Assertions.assertInstanceOf(PersonCreatedEvent.class, personCreatedEventDeserialized);
    }
}
