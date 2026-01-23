package playground;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class StreamTest {

    @Test
    public void Test1(){


        var intList = Arrays.asList(new Integer[] { 1, 2, 3,4, 5, 6 });

        var result = intList.stream().filter(n-> n%2==0).map(n -> n*n).reduce(0, Integer::sum);

        assertEquals(56, result);
        

    }

    @Test
    void optional_map_and_orElse() {
        Optional<String> value = Optional.ofNullable(null);

        var upper = value
                .map(String::toUpperCase)
                .orElse("DEFAULT");

        assertEquals("DEFAULT", upper);
    }

    @Test
    void optional_orElseThrow() {
        Optional<String> empty = Optional.empty();

        assertThrows(IllegalStateException.class, () ->
                empty.orElseThrow(() -> new IllegalStateException("Missing value"))
        );
    }

    @Test
    void equals_and_hash_code (){

        User giorgip = new User("Giorgio", 3);
        User vilma = new User("Giorgio", 3);

        Set<User> users = new HashSet<>();
        users.add(giorgip);

        assertTrue(users.contains(vilma));

    }



    /*
    * xercise 4 — HashSet + dedup + Optional selection

Focus collection: HashSet

Domain
Create a Customer class with:

String email (unique identity)

String name

Optional<String> loyaltyCardId

Statement
Given a List<Customer> that may contain duplicates (same email, different name/loyaltyCardId):

Deduplicate customers into a Set<Customer> using correct equals/hashCode (identity = email, case-insensitive).

Build a Map<String, Customer> keyed by normalized email, but when duplicates exist, choose the “best” customer with this priority:

Prefer the one with loyaltyCardId.isPresent()

If both have loyaltyCardId (or both don’t), prefer the one with the longest name

Provide a method:

Optional<Customer> findBestByEmail(List<Customer> customers, String email)
returning the chosen customer using streams + Optional (no loops).

Constraints:

No null loyalty cards; must be Optional.

No loops.

Your logic must be deterministic.*/
    @Test
    void exercise4() {

        Customer customer0 = new Customer("ciao@ciao.it", "faraffanderoole", Optional.empty());
        Customer customer1 = new Customer("ciao@ciao.it", "giorgio", Optional.of("1234"));
        Customer customer2 = new Customer("ciao@ciao.it", "borjavalero", Optional.of("456"));

        Customer customer3 = new Customer("ostia@ciao.it", "adriano", Optional.empty());

        Customer customer4 = new Customer("puta@ciao.it", "sandro", Optional.of("1234"));

        Customer customer5 = new Customer("lamadonna@ciao.it", "giorgio", Optional.of("357"));
        Customer customer6 = new Customer("lamadonna@ciao.it", "ariannapanna", Optional.of("487"));

        Customer customer7 = new Customer("figaro@ciao.it", "bea", Optional.empty());
        Customer customer8 = new Customer("figaro@ciao.it", "beata", Optional.empty());

        List<Customer> customers = Arrays.asList(customer6, customer2, customer5, customer0, customer4, customer7, customer3, customer1, customer8);

        Set<Customer> customerSet =
                customers.stream().collect(Collectors.groupingBy(customer -> customer.email.toLowerCase(Locale.ROOT)))
                .values().stream()
                        .map(this::pickBest)
                        .collect(Collectors.toSet());

        customerSet.stream().sorted((c1, c2)-> c1.getName().compareTo(c2.name)).forEach(customer -> {System.out.println(customer);});

    }

    private Customer pickBest(Collection<Customer> customerList) {

        return customerList.stream().max((c1, c2) -> {

            if(c1.loyaltyCardId.isPresent() && c2.loyaltyCardId.isPresent() || c1.loyaltyCardId.isEmpty() && c2.loyaltyCardId.isEmpty()){
                return Integer.compare(c1.name.length(), c2.name.length());
            }else if(c1.loyaltyCardId.isPresent()){
                return 1;
            }else  if(c2.loyaltyCardId.isPresent())
                return -1;
            else return 0;

        }).orElseThrow();
    }


    private static class Customer{

        private String email;
        private String name;
        private Optional<String> loyaltyCardId;

        public Customer(String email, String name, Optional<String> loyaltyCardId) {
            this.email = email;
            this.name = name;
            this.loyaltyCardId = loyaltyCardId;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Customer customer)) return false;
            return Objects.equals(email.toLowerCase(), customer.email.toLowerCase());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(email.toLowerCase());
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Optional<String> getLoyaltyCardId() {
            return loyaltyCardId;
        }

        public void setLoyaltyCardId(Optional<String> loyaltyCardId) {
            this.loyaltyCardId = loyaltyCardId;
        }

        @Override
        public String toString() {
            return "Customer{" +
                    "email='" + email + '\'' +
                    ", name='" + name + '\'' +
                    ", loyaltyCardId=" + loyaltyCardId +
                    '}';
        }
    }


    private static class User {

        private String name;
        private int age;

        public User(String name, int age) {
            this.name = name;
            this.age = age;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof User user)) return false;
            return age == user.age && Objects.equals(name, user.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, age);
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }
    }


    /*

    Exercise 5 — Stream + Optional + equals/hashCode + Set semantics
Goal
Practice logical identity, Optional handling, and correct equals/hashCode usage with Set-based collections.

Domain
You have a list of Order objects.

Each Order has:
orderId (String)
customerEmail (String, case-insensitive identity)
Optional<Instant> shippedAt

Requirements
Two Order objects are considered equal if:
customerEmail is the same (case-insensitive)
orderId is the same
Implement equals and hashCode correctly.

From a List<Order>:
remove duplicates using a Set
then produce a List<Order> containing only shipped orders

The final list must:
contain no duplicates
contain only orders where shippedAt is present
be sorted by shippedAt ascending

Constraints
Use streams
Use Optional correctly (no get() without checks)
No loops

No mutable accumulators

**/
    @Test
    void exercise5(){

        ZoneId zone = ZoneId.of("Europe/Madrid");


        LocalDateTime dateTime = LocalDateTime.of(2025, 12, 4, 12, 51);
        Instant instant = dateTime.atZone(zone).toInstant();

        Order o1 = new Order(new OrderKey("ciao@puppa.it", "123456"), Optional.of(instant));
        Order o2 = new Order(new OrderKey("ciao@puppa.it", "123456"), Optional.of(instant));

         dateTime = LocalDateTime.of(2025, 12, 3, 12, 51);
        instant = dateTime.atZone(zone).toInstant();
        Order o3 = new Order(new OrderKey("ciaociao@puppa.it", "123456"), Optional.of(instant));
        Order o4 = new Order(new OrderKey("ciaociao@puppa.it", "123456"), Optional.of(instant));
        Order o5 = new Order(new OrderKey("ciaociao@puppa.it", "123456"), Optional.of(instant));


        dateTime = LocalDateTime.of(2025, 12, 2, 12, 51);
        instant = dateTime.atZone(zone).toInstant();
        Order o6 = new Order(new OrderKey("ciao3@puppa.it", "546"), Optional.of(instant));


        Order o7 = new Order(new OrderKey("ciao4@puppa.it", "978"), null);

        Order o8 = new Order(new OrderKey("ciao5@puppa.it", "654"), Optional.of(instant));

        List<Order> orders = Arrays.asList(o1, o2, o3, o4, o5, o6 , o7, o8);


        Set<Order> distinctShippedOrders = orders.stream().collect(Collectors.groupingBy(Order::getOrderKey))
                .values().stream().map(this::pickOne).filter(order -> order.getShippedAt().isPresent()).collect(Collectors.toSet());


        distinctShippedOrders.stream().sorted(Comparator.comparing(o -> o.getShippedAt().get()))
                .forEach(order -> {System.out.println(order);});


    }

    private Order pickOne(List<Order> orders) {
        return orders.getFirst();
    }

    private class Order{

        private OrderKey orderKey;
        private final Optional<Instant> shippedAt;

        public Order(OrderKey orderKey, Optional<Instant> shippedAt) {
            this.shippedAt = shippedAt == null ? Optional.empty() : shippedAt;
            this.orderKey = orderKey;
        }

        public OrderKey getOrderKey() {
            return orderKey;
        }

        public Optional<Instant> getShippedAt() {
            return shippedAt;
        }

        @Override
        public boolean equals(Object o) {
            if(this == o) return true;
            if (!(o instanceof Order order)) return false;
            return Objects.equals(orderKey, order.orderKey);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(orderKey);
        }

        @Override
        public String toString() {
            return "Order{" +
                    "orderKey=" + orderKey +
                    ", shippedAt=" + shippedAt +
                    '}';
        }
    }

    record OrderKey(String customerEmail, String orderId){
        @Override
        public boolean equals(Object o) {
            if(this==o) return true;
            if (!(o instanceof OrderKey orderKey)) return false;
            return Objects.equals(orderId, orderKey.orderId) && Objects.equals(customerEmail.toLowerCase(Locale.ROOT), orderKey.customerEmail.toLowerCase(Locale.ROOT));
        }

        @Override
        public int hashCode() {
            return Objects.hash(customerEmail.toLowerCase(Locale.ROOT), orderId);
        }
    }

    /**

Exercise 6 — Stream + Optional + TreeSet + complex selection
Goal

Combine Streams, Optional, custom ordering, and TreeSet semantics.

Domain
You have a list of Product objects.

Each Product has:
sku (String)
name (String)
Optional<BigDecimal> discountPrice
BigDecimal basePrice

Requirements
Define logical identity:
Products are equal if sku is equal

From a List<Product>:
insert products into a TreeSet with a custom comparator

Ordering rules (in this order):
Products with a discount come first

Then by effective price
(discountPrice if present, otherwise basePrice)

Then by name alphabetically (tie-breaker)

Extract the top 5 cheapest products into a List<Product>

Constraints

Use TreeSet (not sorted() on a stream)
Use Optional properly
Comparator must be consistent with equals
     */

    @Test
    void ex6(){

        List<Product> products = getProductList();

        TestUtils.printCollection(products);

        Comparator<Product> productComparator =
                Comparator.comparing((Product product) -> product.getDiscountPrice().isEmpty())
                        .thenComparing(product -> product.getDiscountPrice().isPresent() ? product.getDiscountPrice().get() : product.getBasePrice())
                        .thenComparing(Product::getName)
                        .thenComparing(Product::getSku)
                ;


        products =products.stream().collect(Collectors.groupingBy(Product::getSku)).values().stream().map(productsBySku -> pickOne(productsBySku, productComparator)).collect(Collectors.toList());

        System.out.println();
        TestUtils.printCollection(products);

        TreeSet<Product> productTreeSet = new TreeSet<>(productComparator);

        //productTreeSet.addAll(products);

        products.forEach(productTreeSet::add);

        System.out.println();
        TestUtils.printCollection(productTreeSet);



    }

    private Product  pickOne(List<Product> productsBySku, Comparator<Product> productComparator) {

        return productsBySku.stream().min(productComparator).orElseThrow();

    }

    static class Product{

        private final String sku;
        private final String name;
        private final Optional<BigDecimal> discountPrice;
        private final BigDecimal basePrice;

        public Product(String sku, String name, Optional<BigDecimal> discountPrice, BigDecimal basePrice) {
            this.sku = sku;
            this.name = name;
            this.discountPrice = discountPrice!=null ? discountPrice : Optional.empty();
            this.basePrice = basePrice;
        }

        @Override
        public boolean equals(Object o) {
            if(this == o){
                return  true;
            }
            if (!(o instanceof Product product)) return false;
            return Objects.equals(sku, product.sku);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(sku);
        }

        public String getSku() {
            return sku;
        }

        public String getName() {
            return name;
        }

        public Optional<BigDecimal> getDiscountPrice() {
            return discountPrice;
        }

        public BigDecimal getBasePrice() {
            return basePrice;
        }

        @Override
        public String toString() {
            return "Product{" +
                    "sku='" + sku + '\'' +
                    ", name='" + name + '\'' +
                    ", discountPrice=" + discountPrice +
                    ", basePrice=" + basePrice +
                    '}';
        }
    }


        private List<Product> getProductList() {

            // SKU-100: duplicate, discount should win over base-only
            Product p1  = new Product("SKU-100", "Milk",        Optional.empty(),                   new BigDecimal("1.00")); // eff 1.00
            Product p2  = new Product("SKU-100", "Milk Promo",  Optional.of(new BigDecimal("0.80")), new BigDecimal("1.10")); // eff 0.80 (WIN)

            // SKU-200: duplicate, both discounted, lower effective price wins
            Product p3  = new Product("SKU-200", "Coffee",      Optional.of(new BigDecimal("2.50")), new BigDecimal("3.00")); // eff 2.50
            Product p4  = new Product("SKU-200", "Coffee Deal", Optional.of(new BigDecimal("2.30")), new BigDecimal("3.10")); // eff 2.30 (WIN)

            // SKU-300: duplicate, same effective price, tie-break by name
            Product p5  = new Product("SKU-300", "Yogurt A",    Optional.of(new BigDecimal("0.90")), new BigDecimal("1.20")); // eff 0.90 (WIN vs name)
            Product p6  = new Product("SKU-300", "Yogurt B",    Optional.of(new BigDecimal("0.90")), new BigDecimal("1.10")); // eff 0.90

            // SKU-400: duplicate, no discounts, same effective price, tie-break by name
            Product p7  = new Product("SKU-400", "Bread A",     Optional.empty(),                   new BigDecimal("1.20")); // eff 1.20 (WIN vs name)
            Product p8  = new Product("SKU-400", "Bread B",     Optional.empty(),                   new BigDecimal("1.20")); // eff 1.20

            // Singles: ensure global ordering has ties and variety


            Product p11 = new Product("SKU-700", "Cheese",      Optional.of(new BigDecimal("1.20")), new BigDecimal("1.80")); // eff 1.20 (discounted)

            Product p9  = new Product("SKU-500", "Apple",   Optional.of(new BigDecimal("0.45")), new BigDecimal("3.60")); // disc 0.45
            Product p10 = new Product("SKU-600", "Banana",  Optional.empty(),                    new BigDecimal("0.10")); // noDisc 0.10 (should still come AFTER ALL discounted)
            Product p12 = new Product("SKU-800", "Water",   Optional.empty(),                    new BigDecimal("0.80")); // noDisc 0.80 (ties with Milk Promo eff)

            Product p13 = new Product("SKU-900", "Apricot", Optional.of(new BigDecimal("0.10")), new BigDecimal("0.50")); // disc 0.10 (wins vs Banana by rule #1)



            return List.of(p1,p2,p3,p4,p5,p6,p7,p8,p9,p10,p11,p12);
        }


    record Transaction(String user, BigDecimal amount) {}

    List<Transaction> transactions = List.of(
            new Transaction("alice", BigDecimal.valueOf(10.0)),
            new Transaction("bob", BigDecimal.valueOf(20)),
            new Transaction("alice", BigDecimal.valueOf(5)),
            new Transaction("bob", BigDecimal.valueOf(7)),
            new Transaction("charlie", BigDecimal.valueOf(30))
    );


    @Test
    public void exMerge(){

        Map<String, BigDecimal> totals = new HashMap<>();

        transactions.forEach(transaction -> {

            totals.merge(transaction.user(),
                    transaction.amount(),
                    BigDecimal::add);

        });

        TestUtils.printMap(totals);

    }


}
