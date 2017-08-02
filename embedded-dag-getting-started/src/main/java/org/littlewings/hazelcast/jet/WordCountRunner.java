package org.littlewings.hazelcast.jet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.hazelcast.jet.AggregateOperations;
import com.hazelcast.jet.DAG;
import com.hazelcast.jet.Edge;
import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.Partitioner;
import com.hazelcast.jet.Traversers;
import com.hazelcast.jet.Vertex;
import com.hazelcast.jet.function.DistributedFunctions;
import com.hazelcast.jet.processor.Processors;
import com.hazelcast.jet.processor.Sinks;
import com.hazelcast.jet.processor.Sources;
import com.hazelcast.jet.stream.DistributedCollectors;
import com.hazelcast.jet.stream.IStreamMap;

public class WordCountRunner {
    public static void main(String... args) throws ExecutionException, InterruptedException {
        JetInstance jet = Jet.newJetInstance();

        IStreamMap<Integer, String> map = jet.getMap("source");

        load(map, "jet-0_4-release.txt");

        DAG dag = new DAG();

        Vertex source = dag.newVertex("source", Sources.readMap("source"));

        Vertex tokenize = dag.newVertex(
                "tokenize",
                Processors
                        .flatMap((Map.Entry<Integer, String> e) -> {
                            System.out.printf("[%s] tokenize: %s%n", Thread.currentThread().getName(), e);
                            return Traversers.traverseArray(
                                    e.getValue().replaceAll("[=()\"',.:;”-]", " ").trim().split(" +"));
                        }));
        Vertex filter = dag.newVertex("filter", Processors.filter((String s) -> {
            System.out.printf("[%s] filter: %s%n", Thread.currentThread().getName(), s);
            return !s.isEmpty();
        }));
        Vertex lower = dag.newVertex("lower", Processors.map((String s) -> {
            System.out.printf("[%s] lower: %s%n", Thread.currentThread().getName(), s);
            return s.toLowerCase();
        }));
        Vertex accumulate = dag.newVertex(
                "accumulate",
                Processors.accumulateByKey(DistributedFunctions.wholeItem(), AggregateOperations.counting())
        );
        Vertex combine = dag.newVertex("combine",
                Processors.combineByKey(AggregateOperations.counting())
        );

        Vertex sink = dag.newVertex("sink", Sinks.writeMap("word-count-map"));

        dag.edge(Edge.between(source, tokenize))
                .edge(Edge.between(tokenize, filter))
                .edge(Edge.between(filter, lower))
                .edge(Edge.between(lower, accumulate)
                        .partitioned(DistributedFunctions.wholeItem(), Partitioner.HASH_CODE))
                .edge(Edge.between(accumulate, combine)
                        .distributed()
                        .partitioned(DistributedFunctions.entryKey()))
                .edge(Edge.between(combine, sink));

        jet.newJob(dag).execute().get();

        IStreamMap<String, Long> wordCount = jet.getMap("word-count-map");

        List<Map.Entry<String, Long>> top20 =
                wordCount
                        .stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
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

    static void load(IStreamMap<Integer, String> map, String fileName) {
        try (InputStream is = WordCountRunner.class.getClassLoader().getResourceAsStream(fileName);
             InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr);
             Stream<String> lines = reader.lines()) {
            AtomicInteger count = new AtomicInteger();
            lines.forEach(line -> map.put(count.incrementAndGet(), line));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
