package dev.nandi0813.zppbots.platform;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NativeBotBindingsPrimeTest {

    public static final class Probe {
        static int runs;
        public int value;

        public Probe(String s) {
            runs++;
        }

        public void hit(int x) {
            runs++;
        }

        public static void hitStatic(String s) {
            runs++;
        }
    }

    @Test
    void primingRunsNothing() throws ReflectiveOperationException {
        Probe.runs = 0;
        NativeBotBindings.prime(Probe.class.getMethod("hit", int.class));
        NativeBotBindings.prime(Probe.class.getMethod("hitStatic", String.class));
        NativeBotBindings.prime(Probe.class.getConstructor(String.class));
        NativeBotBindings.prime(Probe.class.getField("value"));
        NativeBotBindings.prime("text");
        assertEquals(0, Probe.runs);
    }

    @Test
    void primedMembersStillWork() throws ReflectiveOperationException {
        Probe.runs = 0;
        Method hit = Probe.class.getMethod("hit", int.class);
        Constructor<Probe> constructor = Probe.class.getConstructor(String.class);
        NativeBotBindings.prime(hit);
        NativeBotBindings.prime(constructor);
        Probe probe = (Probe) NativeBotBindings.construct(constructor, "a");
        NativeBotBindings.call(hit, probe, 1);
        assertEquals(2, Probe.runs);
    }
}
