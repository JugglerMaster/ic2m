package ic2m;

import arc.Core;
import arc.input.KeyBind;
import arc.input.KeyCode;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.Dialog;
import arc.scene.ui.ImageButton;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextField;
import arc.scene.ui.Tooltip;
import arc.scene.ui.layout.Table;
import mindustry.Vars;
import mindustry.world.Block;
import mindustry.ui.Styles;
import mindustry.world.meta.BuildVisibility;

/** A custom IC2 build menu, opened with a rebindable hotkey (Settings -> Controls -> "ic2").
 *  Lists every placeable IC2 block; clicking one enters vanilla placement mode for it. */
public class Ic2BuildMenu {
    private static KeyBind openKey;
    private static Dialog dialog;
    private static boolean built = false;
    private static boolean shown = false;

    public static void init() {
        // Registered into KeyBind.all, so it shows up (and is rebindable) in the
        // vanilla Controls settings under the "ic2" category.
        openKey = KeyBind.add("ic2_build_menu", KeyCode.g, "ic2");
        openKey.load();

        // Core.scene does not exist yet during mod init, and is always null on a
        // headless server, so defer the listener until the client UI is ready.
        Core.app.post(Ic2BuildMenu::registerListener);
    }

    private static void registerListener() {
        if (Core.scene == null) {
            if (!Core.app.isHeadless()) Core.app.post(Ic2BuildMenu::registerListener);
            return;
        }
        Core.scene.addListener(new InputListener() {
            public boolean keyDown(InputEvent event, KeyCode keycode) {
                if (Core.scene.getKeyboardFocus() instanceof TextField) return false;
                if (openKey.value != null && openKey.value.key == keycode) {
                    toggle();
                    return true;
                }
                return false;
            }
        });
    }

    private static void toggle() {
        if (dialog == null) build();
        if (shown) {
            dialog.hide();
            shown = false;
        } else {
            dialog.show();
            shown = true;
        }
    }

    private static void build() {
        dialog = new Dialog("IC2 Build Menu");
        dialog.setResizable(false);

        Table grid = new Table();
        int col = 0;
        for (Block b : Vars.content.blocks()) {
            if (b.name == null || !b.name.startsWith("ic2")) continue;
            if (b.buildVisibility == BuildVisibility.hidden) continue;
            if (b.uiIcon == null) continue;

            ImageButton btn = new ImageButton(new TextureRegionDrawable(b.uiIcon), Styles.clearNoneTogglei);
            btn.resizeImage(32f);
            btn.addListener(new Tooltip(t -> t.add(b.localizedName)));
            btn.clicked(() -> {
                Vars.control.input.block = b;
                dialog.hide();
                shown = false;
            });
            grid.add(btn).size(46f).pad(2f);
            if (++col % 6 == 0) grid.row();
        }

        ScrollPane pane = new ScrollPane(grid);
        pane.setScrollingDisabled(false, false);
        dialog.cont.add(pane).size(360f, 420f);
        dialog.addCloseButton();
        built = true;
    }
}
