package org.littlewings.hazelcast.jet;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.hazelcast.jet.*;
import com.hazelcast.jet.function.DistributedSupplier;
import com.hazelcast.jet.processor.Processors;
import com.hazelcast.jet.processor.Sinks;

public class StreamingRunner {
    public static void main(String... args) throws ExecutionException, InterruptedException {
        JetInstance jet = Jet.newJetInstance();

        try {
            WindowDefinition windowDef =
                    WindowDefinition.slidingWindowDef(30_000L, 10_000L);
                    // WindowDefinition.tumblingWindowDef(30_000L);

            DAG dag = new DAG();
            Vertex source =
                    dag.newVertex("source",
                            RandomNumberGenerator.source(3L, TimeUnit.MINUTES, 100));
            Vertex insertWatermarks =
                    dag.newVertex("insert-watermarks", Processors.insertWatermarks(Num::getTime, WatermarkPolicies.withFixedLag(1000), WatermarkEmissionPolicy.emitByFrame(windowDef)));
            Vertex summing1 =
                    dag.newVertex("summing1", Processors.accumulateByFrame(Num::getN, Num::getTime, TimestampKind.EVENT, windowDef, AggregateOperations.summingLong(Num::getN)));
            Vertex summing2 =
                    dag.newVertex("summing2", Processors.combineToSlidingWindow(windowDef, AggregateOperations.summingLong(Num::getN)));
            Vertex format =
                    dag.newVertex("format", formatOutput());
            Vertex sink =
                    dag.newVertex("sink", Sinks.writeFile("target/jet"));


            dag
                    .edge(Edge.between(source, insertWatermarks))
                    .edge(Edge.between(insertWatermarks, summing1)
                            .partitioned(Num::getN, Partitioner.HASH_CODE))
                    .edge(Edge.between(summing1, summing2)
                            .partitioned(Map.Entry<Long, Long>::getKey, Partitioner.HASH_CODE)
                            .distributed())
                    .edge(Edge.between(summing2, format).isolated())
                    .edge(Edge.between(format, sink).isolated());


            Future<Void> future = jet.newJob(dag).execute();
            future.get();
        } finally {
            jet.shutdown();
            Jet.shutdownAll();
        }
    }

    static DistributedSupplier<Processor> formatOutput() {
        return () -> {
            DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
            return Processors.map((TimestampedEntry<Long, Long> f) -> {
                String record = String.format("%s %5s %4d",
                    timeFormat.format(Instant.ofEpochMilli(f.getTimestamp()).atZone(ZoneId.systemDefault())),
                    f.getKey(), f.getValue());
                System.out.printf("[%s] record = %s%n", Thread.currentThread().getName(), record);
                return record;
            }).get();
        };
    }

}
