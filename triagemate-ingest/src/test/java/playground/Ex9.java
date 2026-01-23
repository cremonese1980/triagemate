package playground;

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static playground.TestUtils.*;

public class Ex9 {


    @Test
    public void ex9(){

        List<User> users = buildUsersDataset();
        printCollection(users);

        users = deduplicate(users);
        printCollection(users);

        users = sortUsers(users);
        printCollection(users);


        //Map<String, Long> usersPerCity = users.stream().collect(Collectors.groupingBy(user -> user.city().orElse("UNKNOWN"), Collectors.counting()));


        Map<String, Integer> usersPerCity = users.stream().collect(Collectors.groupingBy(user -> user.city().orElse("UNKNOWN")))
                .entrySet().stream().collect(Collectors.toMap(entry-> entry.getKey(), entry-> entry.getValue().size()));
        printMap(usersPerCity);

    }

    private List<User> sortUsers(List<User> users){

        Comparator<User> userComparator = Comparator.comparing((User user)-> user.city().isEmpty())
                .thenComparing((User user)-> user.city().orElse(""))
                .thenComparing((User user)-> user.age().orElse(-1), Comparator.reverseOrder())
                .thenComparing(User::id);
        return users.stream().sorted(userComparator).collect(Collectors.toList());
    }

    private List<User> deduplicate(List<User> users){

        return users.stream().collect(Collectors.groupingBy(User::id))
                .values().stream().map(usersById -> pickBest(usersById))
                .collect(Collectors.toList());

    }

    private User pickBest(List<User> users) {

        Comparator<User> userComparator = Comparator.comparing((User user)-> user.age.isPresent()).thenComparing((User user)-> user.age.orElse(0)).thenComparing(User::name, Comparator.reverseOrder());
        return users.stream().max(userComparator).orElseThrow();

    }


    private List<User> buildUsersDataset() {
        return List.of(
                new User("U01", "Alice", Optional.of(30), Optional.of("Madrid")),
                new User("U01", "Alice Dup", Optional.empty(), Optional.of("Madrid")),

                new User("U02", "Bob", Optional.of(25), Optional.empty()),
                new User("U02", "Bob Senior", Optional.of(40), Optional.empty()),

                new User("U03", "Charlie", Optional.empty(), Optional.of("Barcelona")),
                new User("U03", "Charlie Young", Optional.of(18), Optional.of("Barcelona")),

                new User("U04", "Diana", Optional.empty(), Optional.empty()),

                new User("U05", "Eve", Optional.of(35), Optional.of("Valencia")),
                new User("U05", "Eve Alt", Optional.of(35), Optional.of("Alicante")),

                new User("U06", "Frank", Optional.of(50), Optional.of("Madrid")),

                new User("U07", "Grace", Optional.empty(), Optional.of("Seville")),

                new User("U08", "Heidi", Optional.of(28), Optional.empty()),

                new User("U09", "Ivan", Optional.empty(), Optional.empty()),
                new User("U09", "Ivan X", Optional.empty(), Optional.of("Madrid")),

                new User("U10", "Judy", Optional.of(22), Optional.of("Madrid")),

                new User("U11", "Karl", Optional.of(22), Optional.of("Berlin")),

                new User("U12", "Laura", Optional.empty(), Optional.of("Rome")),

                new User("U13", "Mallory", Optional.of(60), Optional.empty()),

                new User("U14", "Niaj", Optional.of(19), Optional.of("Paris")),

                new User("U15", "Olivia", Optional.of(19), Optional.of("Paris"))
        );
    }


    //MODEL
    public record User(
            String id,
            String name,
            Optional<Integer> age,
            Optional<String> city
    ) {}
}
