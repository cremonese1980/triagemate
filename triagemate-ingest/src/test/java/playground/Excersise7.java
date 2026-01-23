package playground;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class Excersise7 {

    /***
     * Notes:
     *
     * DiscountUpdate.discountPrice can be Optional.empty() meaning “remove discount”.
     *
     * Stock updates are deltas (+5, -2).
     *
     * Requirements
     * Part A — Build the latest snapshot per SKU
     *
     * Input: List<ProductUpdate> updates
     *
     * Output: Map<ProductKey, ProductSnapshot>
     *
     * Rules:
     *
     * You start from an empty map.
     *
     * Apply updates in time order (at() ascending).
     *
     * Merge updates into a snapshot using Map.merge (not computeIfAbsent).
     *
     * Use pattern matching for switch on ProductUpdate to apply update logic.
     *
     * Optional rules:
     *
     * basePrice/discountPrice/category may be absent in the snapshot until set.
     *
     * DiscountUpdate with empty Optional must clear the discount.
     *
     * Stock:
     *
     * If stock is missing, treat as 0 before applying delta.
     *
     * Stock must never go below 0 (clamp to 0).
     *
     * Deliverable: Map<ProductKey, ProductSnapshot> latestBySku
     *
     * Part B — Rank products using a PriorityQueue (NOT TreeSet)
     *
     * Goal: “Top 5 best offers” with a realistic scoring.
     *
     * Define:
     *
     * Effective price = discountPrice if present else basePrice.
     *
     * A product is “eligible” only if:
     *
     * effective price present
     *
     * base price present
     *
     * currency must be "EUR"
     *
     * stock > 0
     *
     * Ranking rules (in order):
     *
     * Bigger discount percentage first
     * discountPct = (base - effective) / base
     * (If no discount → 0)
     *
     * Then lower effective price
     *
     * Then name alphabetically
     *
     * Then SKU as final tie-break
     *
     * Constraints:
     *
     * You must use a PriorityQueue<ProductSnapshot> to compute top 5.
     *
     * You may not sort the full collection with sorted() and limit(5).
     *
     * Use streams to filter/map, but selection must be via the queue.
     *
     * Output: List<ProductSnapshot> top5Offers
     *
     * Part C — Produce a category summary using Collectors
     *
     * Output:
     * Map<String, CategoryStats>
     *
     * Use a record:
     *
     * public record CategoryStats(
     *         long productsCount,
     *         long discountedCount,
     *         java.math.BigDecimal avgEffectivePriceEur
     * ) {}
     *
     *
     * Rules:
     *
     * Category key: snapshot.category or "UNCATEGORIZED".
     *
     * Only include products with effective price in EUR.
     *
     * avgEffectivePriceEur must be computed using BigDecimal correctly:
     *
     * sum / count with scale 2, RoundingMode.HALF_UP
     *
     * Use collectors (groupingBy + collectingAndThen) and avoid manual loops.
     *
     * Acceptance criteria (what I will check)
     *
     * Uses records, sealed interface, and switch pattern matching.
     *
     * Uses Optional properly (no .get() without checks).
     *
     * Uses Map.merge for snapshot accumulation.
     *
     * Uses PriorityQueue to get top 5 (not TreeSet, not sorted().limit()).
     *
     * Comparator is total and stable (ends with SKU tie-break).
     *
     * Correct handling of “discount removal” updates.
     *
     * Correct BigDecimal math for discount percent and average.
     *
     * Tiny test dataset idea (to catch mistakes)
     *
     * Include:
     *
     * A discount removal update after a discount was set.
     *
     * BasePrice set after catalog update.
     *
     * Two SKUs with same discountPct but different effective price.
     *
     * Stock deltas that would go negative (clamp).
     *
     * Mixed currencies (EUR + USD) to test filtering.
     */

    @Test
    public void excersise_7(){

        List<ProductUpdate> productUpdateList = initialList();
        TestUtils.printCollection(productUpdateList);
        System.out.println();

        productUpdateList = productUpdateList.stream().sorted(Comparator.comparing(ProductUpdate::at)).collect(Collectors.toList());
        TestUtils.printCollection(productUpdateList);
        System.out.println();

        Map<ProductKey, ProductSnapshot> state = new HashMap<>();

        productUpdateList.forEach(productUpdate->{

            state.merge(
                    productUpdate.key(),
                    snapshotFromUpdate(productUpdate),
                    (oldSnapshot, ignored) -> applyUpdate(oldSnapshot, productUpdate)

            );

        });



        TestUtils.printMap(state);
        System.out.println();

    }

    private ProductSnapshot applyUpdate(ProductSnapshot old, ProductUpdate update) {
        return switch (update) {
            case CatalogUpdate u -> new ProductSnapshot(
                    old.key(),
                    u.name(),
                    old.basePrice(),
                    old.discountPrice(),
                    u.category(),        // replaces or clears
                    old.stock()
            );

            case PriceUpdate u -> new ProductSnapshot(
                    old.key(),
                    old.name(),
                    Optional.of(u.basePrice()),
                    old.discountPrice(),
                    old.category(),
                    old.stock()
            );

            case DiscountUpdate u -> new ProductSnapshot(
                    old.key(),
                    old.name(),
                    old.basePrice(),
                    u.discountPrice(),   // Optional.empty() means clear
                    old.category(),
                    old.stock()
            );

            case StockUpdate u -> {
                int current = old.stock().orElse(0);
                int next = Math.max(0, current + u.delta());
                yield new ProductSnapshot(
                        old.key(),
                        old.name(),
                        old.basePrice(),
                        old.discountPrice(),
                        old.category(),
                        Optional.of(next)
                );
            }
        };
    }

    private ProductSnapshot snapshotFromUpdate(ProductUpdate u) {
        if (u instanceof CatalogUpdate cu) {
            return new ProductSnapshot(
                    cu.key(),
                    cu.name(),
                    Optional.empty(),
                    Optional.empty(),
                    cu.category(),
                    Optional.of(0)
            );
        }
        return new ProductSnapshot(
                u.key(),
                "UNKNOWN",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(0)
        );
    }



    private List<ProductUpdate> initialList() {
        ProductKey A = new ProductKey("SKU-100");
        ProductKey B = new ProductKey("SKU-200");
        ProductKey C = new ProductKey("SKU-300");
        ProductKey D = new ProductKey("SKU-400");
        ProductKey E = new ProductKey("SKU-500");

        return List.of(
                // --- A: base after catalog, discount set, discount removed, base updated, stock clamp, category cleared


                // --- B: mixed currency discount (USD), then fixed to EUR; also tie-breaking later
                new CatalogUpdate(B, Instant.parse("2025-12-22T09:02:00Z"), "Protein Bar", Optional.of("SNACKS")),
                new PriceUpdate(B,   Instant.parse("2025-12-22T09:02:10Z"), new Money(new BigDecimal("2.00"), "EUR")),
                new DiscountUpdate(B,Instant.parse("2025-12-22T09:02:20Z"), Optional.of(new Money(new BigDecimal("1.00"), "USD"))), // currency mismatch
                new StockUpdate(B,   Instant.parse("2025-12-22T09:02:30Z"), +10),
                new DiscountUpdate(B,Instant.parse("2025-12-22T09:02:40Z"), Optional.of(new Money(new BigDecimal("1.00"), "EUR"))), // fixed to EUR (50% off)

                // --- C: tie on discountPct with B (50%), but stock=0 makes it ineligible; later stock positive makes eligible
                new CatalogUpdate(C, Instant.parse("2025-12-22T09:03:00Z"), "Greek Yogurt", Optional.of("DAIRY")),
                new PriceUpdate(C,   Instant.parse("2025-12-22T09:03:10Z"), new Money(new BigDecimal("1.00"), "EUR")),
                new DiscountUpdate(C,Instant.parse("2025-12-22T09:03:20Z"), Optional.of(new Money(new BigDecimal("0.50"), "EUR"))), // 50% off
                new StockUpdate(C,   Instant.parse("2025-12-22T09:03:30Z"), 0), // ineligible
                new StockUpdate(C,   Instant.parse("2025-12-22T09:03:40Z"), +3), // eligible again

                // --- D: tie on discountPct (50%) but different effective price; plus name tie and SKU tie setup
                new CatalogUpdate(D, Instant.parse("2025-12-22T09:04:00Z"), "Sparkling Water", Optional.of("BEVERAGES")),
                new PriceUpdate(D,   Instant.parse("2025-12-22T09:04:10Z"), new Money(new BigDecimal("0.80"), "EUR")),
                new DiscountUpdate(D,Instant.parse("2025-12-22T09:04:20Z"), Optional.of(new Money(new BigDecimal("0.40"), "EUR"))), // 50% off, eff=0.40
                new StockUpdate(D,   Instant.parse("2025-12-22T09:04:30Z"), +7),

                // --- E: discount-only first (ineligible), then base arrives; discount higher than base (negative discountPct)
                new CatalogUpdate(E, Instant.parse("2025-12-22T09:05:00Z"), "Mystery Item", Optional.empty()),
                new DiscountUpdate(E,Instant.parse("2025-12-22T09:05:10Z"), Optional.of(new Money(new BigDecimal("5.00"), "EUR"))), // discount-only -> no base => ineligible
                new PriceUpdate(E,   Instant.parse("2025-12-22T09:05:20Z"), new Money(new BigDecimal("4.00"), "EUR")), // now eligible, but discount > base => negative discountPct
                new StockUpdate(E,   Instant.parse("2025-12-22T09:05:30Z"), +1),

                new CatalogUpdate(A, Instant.parse("2025-12-22T09:00:00Z"), "Coffee Beans", Optional.of("BEVERAGES")),
                new PriceUpdate(A,   Instant.parse("2025-12-22T09:00:10Z"), new Money(new BigDecimal("10.00"), "EUR")),
                new DiscountUpdate(A,Instant.parse("2025-12-22T09:00:20Z"), Optional.of(new Money(new BigDecimal("8.00"), "EUR"))),
                new StockUpdate(A,   Instant.parse("2025-12-22T09:00:30Z"), +5),
                new DiscountUpdate(A,Instant.parse("2025-12-22T09:00:40Z"), Optional.empty()), // remove discount
                new PriceUpdate(A,   Instant.parse("2025-12-22T09:00:50Z"), new Money(new BigDecimal("12.00"), "EUR")), // later wins
                new StockUpdate(A,   Instant.parse("2025-12-22T09:01:00Z"), -10), // clamp to 0
                new CatalogUpdate(A, Instant.parse("2025-12-22T09:01:10Z"), "Coffee Beans Premium", Optional.empty()) // clears category -> UNCATEGORIZED
        );
    }


    public record ProductKey(String sku) {}

    public record Money(BigDecimal amount, String currency) {}

    public record ProductSnapshot(
            ProductKey key,
            String name,
            Optional<Money> basePrice,
            Optional<Money> discountPrice,
            Optional<String> category,
            Optional<Integer> stock
    ) {}


    public sealed interface ProductUpdate permits PriceUpdate, DiscountUpdate, StockUpdate, CatalogUpdate {
        ProductKey key();
        Instant at();
    }

    public record PriceUpdate(ProductKey key, Instant at, Money basePrice) implements ProductUpdate {}
    public record DiscountUpdate(ProductKey key, Instant at, Optional<Money> discountPrice) implements ProductUpdate {}
    public record StockUpdate(ProductKey key, Instant at, int delta) implements ProductUpdate {}
    public record CatalogUpdate(ProductKey key, Instant at, String name, Optional<String> category) implements ProductUpdate {}

}
