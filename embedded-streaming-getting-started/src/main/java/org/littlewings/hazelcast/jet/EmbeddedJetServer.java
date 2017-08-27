package org.littlewings.hazelcast.jet;

import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;

public class EmbeddedJetServer {
    public static void main(String... args) {
        JetInstance jet = Jet.newJetInstance();

        System.console().readLine("> stop enter.");

        jet.shutdown();
        Jet.shutdownAll();
    }
}
