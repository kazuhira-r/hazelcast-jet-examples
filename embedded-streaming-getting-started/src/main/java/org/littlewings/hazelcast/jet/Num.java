package org.littlewings.hazelcast.jet;

import java.io.Serializable;

public class Num implements Serializable {
    private static final long serialVersionUID = 1L;

    int n;
    long time;

    public Num(int n, long time) {
        this.n = n;
        this.time = time;
    }

    public int getN() {
        return n;
    }

    public long getTime() {
        return time;
    }
}
