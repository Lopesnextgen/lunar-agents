package me.onils.damageindicator;

import java.lang.instrument.Instrumentation;

public class Agent {
    public static void premain(String args, Instrumentation inst){
        inst.addTransformer(new Transformer());
    }
}
