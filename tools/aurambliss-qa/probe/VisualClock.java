package dev.auram.bliss.qa;

/** Holds the shader clock while matching captures from the same scene. */
public final class VisualClock {
    private static float frozenTime = Float.NaN;

    private VisualClock() {}

    public static void freeze(String argument) {
        frozenTime = argument.equals("off") ? Float.NaN : Float.parseFloat(argument);
    }

    public static boolean frozen() {
        return Float.isFinite(frozenTime);
    }

    public static float time() {
        return frozenTime;
    }
}
