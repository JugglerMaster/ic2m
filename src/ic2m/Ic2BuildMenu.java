package ic2m;

import arc.Core;
import arc.input.KeyBind;
import arc.input.KeyCode;
import arc.scene.event.ClickListener;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.style.TextureRegionDrawable;
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
 *  Lists every placeable IC2 block; clicking one enters vanilla placement mode for it.
 *  The menu is a small non-modal panel (no full-screen dimming) — click outside it to close. */
public class Ic2BuildMenu {
    private static KeyBind openKey;
    private static Table catcher;
    private static Table panel;
    private static boolean built = false;
    private static boolean shown = false;

    public static void init() {
        openKey = KeyBind.add("ic2_build_menu", KeyCode.i, "ic2");
        openKey.load();
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
        if (catcher == null) build();
        if (shown) hide(); else show();
    }

    private static void show() {
        catcher.visible = true;
        catcher.touchable = Touchable.enabled;
        shown = true;
    }

    private static void hide() {
        catcher.visible = false;
        catcher.touchable = Touchable.disabled;
        shown = false;
    }

    private static void build() {
        // Transparent full-screen catcher. It dims nothing; it only exists so that clicks on
        // empty space (outside the panel) dismiss the menu.
        catcher = new Table();
        catcher.setFillParent(true);
        catcher.touchable = Touchable.disabled;
        catcher.visible = false;
        catcher.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (shown && panel.hit(event.stageX, event.stageY, true) == null) hide();
            }
        });

        // The actual visible menu panel.
        panel = new Table();
        panel.background(Styles.black6);

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
                hide();
            });
            grid.add(btn).size(46f).pad(2f);
            if (++col % 6 == 0) grid.row();
        }

        ScrollPane pane = new ScrollPane(grid);
        pane.setScrollingDisabled(false, false);
        panel.add(pane).size(360f, 420f);

        catcher.add(panel).expand().center();
        Core.scene.root.addChild(catcher);

        built = true;
    }
}
