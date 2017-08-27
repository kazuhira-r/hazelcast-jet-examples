package org.littlewings.hazelcast.jet;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import com.hazelcast.jet.AbstractProcessor;
import com.hazelcast.jet.Processor;
import com.hazelcast.jet.function.DistributedSupplier;

public class RandomNumberGenerator extends AbstractProcessor {
    Random random = new Random();
    long continuousTime;
    TimeUnit continuousUnit;
    int limit;
    long startTime;

    public RandomNumberGenerator(long countinuousTime, TimeUnit continuousUnit, int limit, long startTime) {
        this.continuousTime = countinuousTime;
        this.continuousUnit = continuousUnit;
        this.limit = limit;
        this.startTime = startTime;
    }

    public static DistributedSupplier<Processor> source(long continuousTime, TimeUnit continuousUnit, int limit) {
        return () -> new RandomNumberGenerator(continuousTime, continuousUnit, limit, System.nanoTime());
    }

    @Override
    protected void init(Context context) throws Exception {
    }

    @Override
    public boolean complete() {
        System.out.printf("[%s] ===== complete =====%n", Thread.currentThread().getName());

        long sleepMillis = 1000L;
        int loopCount = (int) (60 * 1000 / sleepMillis);

        int n = random.nextInt(limit);

        IntStream
                .rangeClosed(1, loopCount)
                .forEach(i -> {
                    System.out.printf("[%s] genrated = %d%n", Thread.currentThread().getName(), n);

                    emit(new Num(n, LocalDateTime.now().atZone(ZoneOffset.systemDefault()).toInstant().toEpochMilli()));

                    try {
                        TimeUnit.MILLISECONDS.sleep(sleepMillis);
                    } catch (InterruptedException e) {
                        // ignoe
                    }
                });


        long elapsedTime = System.nanoTime() - startTime;

        System.out.printf("[%s] start: %d%n", Thread.currentThread().getName(), startTime);
        System.out.printf("[%s] rap: %s%n",
                Thread.currentThread().getName(),
                continuousUnit.convert(elapsedTime, TimeUnit.NANOSECONDS));

        return continuousUnit.convert(elapsedTime, TimeUnit.NANOSECONDS) >= continuousTime;
    }

    @Override
    public boolean isCooperative() {
        return false;
    }
}
