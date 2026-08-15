package ic2m;

/** Lightweight regression harness for EU transfer rules; run with ./gradlew euTest. */
public final class EuTransferHarness {
    private static int checks;

    private EuTransferHarness() {
    }

    public static void main(String[] args) {
        checkClose("LV packet", EuTransferRules.sourceAmount(100f, 100f, 10f, 1f), 10f);
        checkClose("LV received", EuTransferRules.targetAmount(10f, 1f), 10f);

        checkClose("HV source packet", EuTransferRules.sourceAmount(100f, 100f, 10f, .98f), 10f);
        checkClose("HV received", EuTransferRules.targetAmount(10f, .98f), 9.8f);

        // A nearly full destination must never make the source gain energy.
        checkClose("transformer capacity limit", EuTransferRules.sourceAmount(100f, 1f, 10f, .9f), 1f / .9f);
        checkClose("transformer fills capacity", EuTransferRules.targetAmount(1f / .9f, .9f), 1f);

        checkClose("source-limited transfer", EuTransferRules.sourceAmount(3f, 100f, 10f, .9f), 3f);
        checkClose("empty source", EuTransferRules.sourceAmount(0f, 100f, 10f, .9f), 0f);
        checkClose("full destination", EuTransferRules.sourceAmount(100f, 0f, 10f, .9f), 0f);
        checkClose("invalid efficiency", EuTransferRules.sourceAmount(100f, 100f, 10f, 0f), 0f);

        System.out.println("EU transfer harness passed " + checks + " checks.");
    }

    private static void checkClose(String name, float actual, float expected) {
        checks++;
        if (Math.abs(actual - expected) > 0.0001f) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
    }
}
