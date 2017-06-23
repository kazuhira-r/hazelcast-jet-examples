package org.littlewings.hazelcast.jet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.function.DistributedComparator;
import com.hazelcast.jet.stream.DistributedCollectors;
import com.hazelcast.jet.stream.IStreamMap;

public class WordCountRunner {
    public static void main(String... args) {
        JetInstance jet = Jet.newJetInstance();

        IStreamMap<Integer, String> map = jet.getMap("source");

        try (InputStream is = WordCountRunner.class.getClassLoader().getResourceAsStream("jet-0_4-release.txt");
             InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr);
             Stream<String> lines = reader.lines()) {
            AtomicInteger count = new AtomicInteger();
            lines.forEach(line -> map.put(count.incrementAndGet(), line));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        IStreamMap<String, Integer> wordCount =
                map
                        .stream()
                        .map(e -> e.getValue().replaceAll("[=()\"',.:;”-]", " ").trim())
                        .filter(line -> !line.isEmpty())
                        .flatMap(line -> Arrays.stream(line.split(" +")))
                        .map(word -> {
                            String lc = word.toLowerCase();
                            System.out.printf("[%s] %s%n", Thread.currentThread().getName(), lc);
                            return lc;
                        })
                        .collect(DistributedCollectors.toIMap(
                                "word-count-map",
                                word -> word,
                                word -> 1,
                                Integer::sum)
                        );

        List<Map.Entry<String, Integer>> top20 =
                wordCount
                        .stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .limit(20)
                        .collect(DistributedCollectors.toList());

        System.out.println("result:");
        top20.forEach(e -> System.out.println("  " + e));

        System.out.println();
        System.out.println("source map size = " + jet.getMap("source").size());
        System.out.println("word-count-map size = " + jet.getMap("word-count-map").size());

        jet.shutdown();
        Jet.shutdownAll();
    }
}
