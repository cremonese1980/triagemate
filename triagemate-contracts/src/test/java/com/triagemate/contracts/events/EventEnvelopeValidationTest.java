package com.triagemate.contracts.events;

import com.triagemate.contracts.events.v1.InputReceivedV1;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EventEnvelopeValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void rejectsNullEventId() {
        EventEnvelope<InputReceivedV1> envelope =
                validEnvelope(null, "type-1");

        Set<ConstraintViolation<EventEnvelope<InputReceivedV1>>> violations =
                validator.validate(envelope);

        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("eventId"));
    }

    @Test
    void rejectsBlankEventId() {
        EventEnvelope<InputReceivedV1> envelope =
                validEnvelope("", "type-1");

        Set<ConstraintViolation<EventEnvelope<InputReceivedV1>>> violations =
                validator.validate(envelope);

        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("eventId"));
    }

    @Test
    void rejectsBlankEventType() {
        EventEnvelope<InputReceivedV1> envelope =
                validEnvelope("event-1", "");

        Set<ConstraintViolation<EventEnvelope<InputReceivedV1>>> violations =
                validator.validate(envelope);

        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("eventType"));
    }

    @Test
    void rejectsNullOccurredAt() {

        InputReceivedV1 payload = validPayload();

        EventEnvelope<InputReceivedV1> envelope =
                new EventEnvelope<>(
                        "event-1",
                        "type-1",
                        1,
                        null,
                        new EventEnvelope.Producer("service", "instance"),
                        new EventEnvelope.Trace("req", "corr", null),
                        payload,
                        Map.of()
                );

        Set<ConstraintViolation<EventEnvelope<InputReceivedV1>>> violations =
                validator.validate(envelope);

        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("occurredAt"));
    }

    @Test
    void rejectsNullPayload() {

        EventEnvelope<InputReceivedV1> envelope =
                new EventEnvelope<>(
                        "event-1",
                        "type-1",
                        1,
                        Instant.now(),
                        new EventEnvelope.Producer("service", "instance"),
                        new EventEnvelope.Trace("req", "corr", null),
                        null,
                        Map.of()
                );

        Set<ConstraintViolation<EventEnvelope<InputReceivedV1>>> violations =
                validator.validate(envelope);

        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("payload"));
    }

    @Test
    void acceptsValidEnvelope() {

        EventEnvelope<InputReceivedV1> envelope =
                validEnvelope("event-1", "type-1");

        Set<ConstraintViolation<EventEnvelope<InputReceivedV1>>> violations =
                validator.validate(envelope);

        assertThat(violations).isEmpty();
    }

    // --------------------

    private EventEnvelope<InputReceivedV1> validEnvelope(
            String eventId,
            String eventType
    ) {
        return new EventEnvelope<>(
                eventId,
                eventType,
                1,
                Instant.now(),
                new EventEnvelope.Producer("service", "instance"),
                new EventEnvelope.Trace("req", "corr", null),
                validPayload(),
                Map.of()
        );
    }

    private InputReceivedV1 validPayload() {
        return new InputReceivedV1(
                "input-1",
                "email",
                "subject",
                "body",
                "from@test",
                System.currentTimeMillis()
        );
    }
}
