package ic2m;

import java.io.File;
import java.util.List;
import java.util.zip.ZipFile;

/** Verifies that the shipped JAR contains the runtime content and assets. */
public final class EuJarContentHarness {
    private EuJarContentHarness() {
    }

    public static void main(String[] args) throws Exception {
        File jar = new File("build/libs/ic2m.jar");
        if (!jar.isFile()) throw new AssertionError("missing build/libs/ic2m.jar");

        List<String> required = List.of(
            "mod.hjson",
            "content/blocks/ic2-macerator.hjson",
            "content/blocks/ic2-alloy-furnace.hjson",
            "content/blocks/ic2-lv-cable.hjson",
            "content/blocks/ic2-hv-cable.hjson",
            "content/blocks/ic2-transformer.hjson",
            "content/blocks/ic2-insulated-lv-cable.hjson",
            "content/blocks/ic2-reinforced-hv-cable.hjson",
            "content/blocks/ic2-low-loss-hv-cable.hjson",
            "content/blocks/ic2-superconductor-cable.hjson",
            "content/items/copper-dust.hjson",
            "content/items/copper-ingot.hjson",
            "content/items/lead-ingot.hjson",
            "content/items/titanium-ingot.hjson",
            "sprites/blocks/ic2-macerator.png",
            "sprites/blocks/ic2-alloy-furnace.png",
            "sprites/blocks/ic2-battery.png",
            "sprites/blocks/ic2-solar-panel.png"
        );

        try (ZipFile zip = new ZipFile(jar)) {
            for (String path : required) {
                if (zip.getEntry(path) == null) throw new AssertionError("missing JAR entry: " + path);
            }
        }
        System.out.println("JAR content harness passed " + required.size() + " checks.");
    }
}
