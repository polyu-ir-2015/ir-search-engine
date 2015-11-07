package hk.edu.polyu.ir.groupc;

import scala.collection.Iterator;

import java.io.*;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Created by beenotung on 11/7/15.
 */
public class Utils {
    public static <E> void foreach(Iterator<E> iterator, Consumer<E> consumer) {
        while (iterator.hasNext())
            consumer.accept(iterator.next());
    }

    public static void processLines(File file, Consumer<String> consumer) throws FileNotFoundException {
        new BufferedReader(new FileReader(file)).lines().forEach(consumer);
    }

    public static <R> Stream<R> getLinesConverted(File file, Function<String, R> converter) throws FileNotFoundException {
        return new BufferedReader(new InputStreamReader(new FileInputStream(file))).lines().map(converter::apply);
    }

    public static String[] getLines(File file) throws IOException {
        return new BufferedReader(new FileReader(file)).lines().toArray(value -> new String[value]);
    }

    @Deprecated
    public static <T> ArrayList<T> streamToArray(Stream<T> stream) {
        ArrayList<T> ts=new ArrayList<>();
        stream.forEach(t -> ts.add(t));
        return ts;
    }

    public static int countLines(File file) throws IOException {
        InputStream is = new BufferedInputStream(new FileInputStream(file));
        try {
            byte[] c = new byte[1024];
            int count = 0;
            int readChars = 0;
            boolean empty = true;
            while ((readChars = is.read(c)) != -1) {
                empty = false;
                for (int i = 0; i < readChars; ++i) {
                    if (c[i] == '\n') {
                        ++count;
                    }
                }
            }
            return (count == 0 && !empty) ? 1 : count;
        } finally {
            is.close();
        }
    }
}
