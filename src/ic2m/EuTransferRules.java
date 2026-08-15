package ic2m;

/** Pure EU transfer math shared by cables, transformers, and the test harness. */
public final class EuTransferRules {
    private EuTransferRules() {
    }

    /** Returns how much source energy is consumed for one transfer step. */
    public static float sourceAmount(float sourceEnergy, float targetSpace, float transferLimit, float efficiency) {
        if (sourceEnergy <= 0f || targetSpace <= 0f || transferLimit <= 0f || efficiency <= 0f) return 0f;
        return Math.min(sourceEnergy, Math.min(transferLimit, targetSpace / efficiency));
    }

    public static float targetAmount(float sourceAmount, float efficiency) {
        return sourceAmount * efficiency;
    }
}
