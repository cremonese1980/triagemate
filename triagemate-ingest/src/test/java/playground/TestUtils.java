package playground;

import java.util.Collection;
import java.util.Map;

public final class TestUtils {

    private TestUtils(){

    }

    public static <E> void  printCollection  (Collection<E> collection){

        collection.forEach(System.out::println);
        System.out.println();

    }

    public static <K,V> void printMap(Map<K,V> map) {

        map.keySet().forEach(key ->{

            System.out.println("\"" + key + "\": " + map.get(key) );

        });

        System.out.println();

    }
}
