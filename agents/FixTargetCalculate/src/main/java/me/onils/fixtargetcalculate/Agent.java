package me.onils.fixtargetcalculate;

import java.lang.instrument.Instrumentation;
import java.util.Locale;

public class Agent {
    public static void premain(String args, Instrumentation inst){
        boolean debug = args != null && args.toLowerCase(Locale.ROOT).contains("debug");
        Hooks.DEBUG = debug;
        System.out.println("[FixTargetCalculate] Agent loaded" + (debug ? " (debug=ON)" : ""));
        inst.addTransformer(new Transformer());
        inst.addTransformer(new WorldTransformer());
    }
}
