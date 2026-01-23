package playground;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.BiFunction;

import static playground.TestUtils.*;

public class Ex8 {

    private static final Logger log = LoggerFactory.getLogger(Ex8.class);
    private final OrderState FAKE = new Open(null, null, null);


    @Test
    public void ex8(){

        List<OrderEvent> incomingOrderEvents = buildIncomingOrderEvents();
        //printCollection(incomingOrderEvents);

        incomingOrderEvents = incomingOrderEvents.stream().sorted(Comparator.comparing(OrderEvent::at)).toList();
        //printCollection(incomingOrderEvents);

        Map<OrderKey, OrderState> finalStateByOrder = new HashMap<>();

        Map<OrderKey, List< OrderEvent>> orphans = new HashMap<>();

        incomingOrderEvents.forEach(orderEvent -> {

            if (!(orderEvent instanceof OrderPlaced) && !finalStateByOrder.containsKey(orderEvent.key())) {
                addOrphan(orderEvent, orphans);
                log.info("Orphan added ");
                return;
            }

            finalStateByOrder.merge(

                        orderEvent.key(),
                        createStateForOrder(orderEvent),
                        (currentState, ignored)-> (updateStatus(currentState, orderEvent))

                );

        });


        printMap(finalStateByOrder);

        printMap(orphans);

    }

    private void addOrphan(OrderEvent orderEvent, Map<OrderKey, List<OrderEvent>> orphans) {

        
        orphans.merge(
                orderEvent.key(),
                new ArrayList<>(List.of(orderEvent)),
                (actualEvents, ignored) -> {
                    actualEvents.add(orderEvent);
                    return actualEvents;
                });
    }

    private OrderState createStateForOrder(OrderEvent orderEvent) {

        if(orderEvent instanceof OrderPlaced orderPlaced){
            return new Open(orderPlaced, Collections.emptyList(), Optional.empty());
        }

        return FAKE;

    }

    private OrderState updateStatus(OrderState currentState, OrderEvent orderEvent) {

        return
                switch (orderEvent) {

                    case OrderPlaced orderPlaced -> (currentState);

                    case PaymentCaptured paymentCaptured -> (currentState);

                    case ShipmentSent shipmentSent -> (currentState);

                    case OrderCancelled orderCancelled -> (currentState);

                };
    }


    /*
    MODEL
     */

    public enum Currency { EUR, USD }

    public record Money(BigDecimal amount, Currency currency) {}

    public record OrderKey(String orderId) {}

    public sealed interface OrderEvent permits OrderPlaced, PaymentCaptured, ShipmentSent, OrderCancelled {
        OrderKey key();
        Instant at();
    }

    public record OrderPlaced(OrderKey key, Instant at, Money total, String customerId) implements OrderEvent {}
    public record PaymentCaptured(OrderKey key, Instant at, Money amount, String paymentId) implements OrderEvent {}
    public record ShipmentSent(OrderKey key, Instant at, String carrier, String trackingId) implements OrderEvent {}
    public record OrderCancelled(OrderKey key, Instant at, String reason) implements OrderEvent {}

    public sealed interface OrderState permits Open, Paid, Shipped, Cancelled {}

    public record Open(OrderPlaced placed, List<PaymentCaptured> payments, Optional<ShipmentSent> shipment) implements OrderState {}
    public record Paid(OrderPlaced placed, List<PaymentCaptured> payments) implements OrderState {}
    public record Shipped(OrderPlaced placed, List<PaymentCaptured> payments, ShipmentSent shipment) implements OrderState {}
    public record Cancelled(OrderPlaced placed, List<PaymentCaptured> payments, Optional<ShipmentSent> shipment, OrderCancelled cancelled) implements OrderState {}


    /*
    INITIAL DATASET
     */
    private List<OrderEvent> buildIncomingOrderEvents() {
        ZoneId zone = ZoneId.of("Europe/Madrid");

        // Helper to build Instants with readable local times
        BiFunction<LocalDate, String, Instant> at =
                (date, hhmm) -> LocalDateTime
                        .of(date, LocalTime.parse(hhmm))
                        .atZone(zone)
                        .toInstant();

        LocalDate d1 = LocalDate.of(2025, 12, 20);
        LocalDate d2 = LocalDate.of(2025, 12, 21);

        // Order IDs
        OrderKey o100 = new OrderKey("ORD-100");
        OrderKey o200 = new OrderKey("ORD-200");
        OrderKey o300 = new OrderKey("ORD-300");
        OrderKey o400 = new OrderKey("ORD-400");
        OrderKey o500 = new OrderKey("ORD-500");
        OrderKey o600 = new OrderKey("ORD-600");
        OrderKey o700 = new OrderKey("ORD-700");

        // Payment IDs (used to force duplicates + diagnostics)
        String p100a = "PAY-100-A";
        String p200a = "PAY-200-A";
        String p200dup = "PAY-200-DUP";
        String p300a = "PAY-300-A";
        String p300b = "PAY-300-B";
        String p400a = "PAY-400-A";
        String p500a = "PAY-500-A";
        String p600a = "PAY-600-A";
        String p700a = "PAY-700-A";

        return List.of(
                // =========================
                // ORD-100 (EUR) — Happy path: placed -> paid -> shipped
                // =========================
                new OrderPlaced(
                        o100,
                        at.apply(d1, "09:00"),
                        new Money(new BigDecimal("100.00"), Currency.EUR),
                        "CUST-1"
                ),
                new PaymentCaptured(
                        o100,
                        at.apply(d1, "09:05"),
                        new Money(new BigDecimal("100.00"), Currency.EUR),
                        p100a
                ),
                new ShipmentSent(
                        o100,
                        at.apply(d1, "12:00"),
                        "DHL",
                        "TRK-100"
                ),

                // =========================
                // ORD-200 (EUR) — Shipped before fully paid (risk +50), then later fully paid
                // Also: duplicate paymentId appears twice (risk +1)
                // =========================
                new OrderPlaced(
                        o200,
                        at.apply(d1, "09:10"),
                        new Money(new BigDecimal("200.00"), Currency.EUR),
                        "CUST-2"
                ),
                new PaymentCaptured(
                        o200,
                        at.apply(d1, "09:12"),
                        new Money(new BigDecimal("50.00"), Currency.EUR),
                        p200a
                ),
                new ShipmentSent(
                        o200,
                        at.apply(d1, "10:00"),
                        "UPS",
                        "TRK-200"
                ),
                // duplicate payment id (same paymentId twice for same order)
                new PaymentCaptured(
                        o200,
                        at.apply(d1, "10:05"),
                        new Money(new BigDecimal("10.00"), Currency.EUR),
                        p200dup
                ),
                new PaymentCaptured(
                        o200,
                        at.apply(d1, "10:06"),
                        new Money(new BigDecimal("10.00"), Currency.EUR),
                        p200dup
                ),
                // later payment completes total
                new PaymentCaptured(
                        o200,
                        at.apply(d1, "11:00"),
                        new Money(new BigDecimal("140.00"), Currency.EUR),
                        "PAY-200-B"
                ),

                // =========================
                // ORD-300 (EUR) — Orphan payment before placed (diagnostic + orphan count)
                // then placed, then partial pay, then cancelled before shipping
                // =========================
                new PaymentCaptured(
                        o300,
                        at.apply(d1, "08:30"),
                        new Money(new BigDecimal("20.00"), Currency.EUR),
                        p300a
                ),
                new OrderPlaced(
                        o300,
                        at.apply(d1, "09:20"),
                        new Money(new BigDecimal("60.00"), Currency.EUR),
                        "CUST-3"
                ),
                new PaymentCaptured(
                        o300,
                        at.apply(d1, "09:25"),
                        new Money(new BigDecimal("30.00"), Currency.EUR),
                        p300b
                ),
                new OrderCancelled(
                        o300,
                        at.apply(d1, "09:40"),
                        "Customer changed mind"
                ),

                // =========================
                // ORD-400 (EUR) — Currency mismatch payment (USD on EUR order) must be ignored + diagnostics
                // plus a valid EUR payment (still underpaid) and shipped -> risk +50, +5 mismatch
                // =========================
                new OrderPlaced(
                        o400,
                        at.apply(d1, "09:30"),
                        new Money(new BigDecimal("120.00"), Currency.EUR),
                        "CUST-4"
                ),
                new PaymentCaptured(
                        o400,
                        at.apply(d1, "09:32"),
                        new Money(new BigDecimal("50.00"), Currency.USD),
                        "PAY-400-USD"
                ),
                new PaymentCaptured(
                        o400,
                        at.apply(d1, "09:33"),
                        new Money(new BigDecimal("40.00"), Currency.EUR),
                        p400a
                ),
                new ShipmentSent(
                        o400,
                        at.apply(d1, "09:50"),
                        "GLS",
                        "TRK-400"
                ),

                // =========================
                // ORD-500 (EUR) — Cancelled after shipping (risk +30), also not fully paid at ship time (+50)
                // =========================
                new OrderPlaced(
                        o500,
                        at.apply(d1, "10:10"),
                        new Money(new BigDecimal("90.00"), Currency.EUR),
                        "CUST-5"
                ),
                new PaymentCaptured(
                        o500,
                        at.apply(d1, "10:15"),
                        new Money(new BigDecimal("30.00"), Currency.EUR),
                        p500a
                ),
                new ShipmentSent(
                        o500,
                        at.apply(d1, "10:30"),
                        "DHL",
                        "TRK-500"
                ),
                new OrderCancelled(
                        o500,
                        at.apply(d1, "11:30"),
                        "Address not reachable"
                ),

                // =========================
                // ORD-600 (USD) — Valid USD order/pay/shipping
                // Tests revenue grouping per currency and date (different day)
                // =========================
                new OrderPlaced(
                        o600,
                        at.apply(d2, "09:00"),
                        new Money(new BigDecimal("75.00"), Currency.USD),
                        "CUST-6"
                ),
                new PaymentCaptured(
                        o600,
                        at.apply(d2, "09:01"),
                        new Money(new BigDecimal("75.00"), Currency.USD),
                        p600a
                ),
                new ShipmentSent(
                        o600,
                        at.apply(d2, "10:00"),
                        "FedEx",
                        "TRK-600"
                ),

                // =========================
                // ORD-700 (EUR) — Multiple orphan payments before placed (orphan count > 1),
                // then placed, then paid fully (shows orphan risk component)
                // =========================
                new PaymentCaptured(
                        o700,
                        at.apply(d1, "08:00"),
                        new Money(new BigDecimal("10.00"), Currency.EUR),
                        p700a
                ),
                new PaymentCaptured(
                        o700,
                        at.apply(d1, "08:10"),
                        new Money(new BigDecimal("15.00"), Currency.EUR),
                        "PAY-700-B"
                ),
                new OrderPlaced(
                        o700,
                        at.apply(d1, "10:40"),
                        new Money(new BigDecimal("25.00"), Currency.EUR),
                        "CUST-7"
                ),
                new PaymentCaptured(
                        o700,
                        at.apply(d1, "10:45"),
                        new Money(new BigDecimal("25.00"), Currency.EUR),
                        "PAY-700-C"
                )
        );
    }



}
