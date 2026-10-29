package dev.opecko.sonocraft.compat.oneconfig;

import dev.opecko.sonocraft.Sonocraft;
import dev.opecko.sonocraft.SonocraftCore;
import dev.opecko.sonocraft.config.HudConfig;
import dev.opecko.sonocraft.hud.HudLayout;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.polyfrost.oneconfig.api.config.v1.Property;
import org.polyfrost.oneconfig.api.hud.v1.HudManager;
import org.polyfrost.oneconfig.api.hud.v1.HudResize;
import org.polyfrost.oneconfig.api.hud.v1.OneConfigHudWrapper;

/**
 * Shows the Now Playing panel in OneConfig's HUD editor. OneConfig draws its own frame and moves it; we keep
 * drawing the panel and keep its position in {@code config/sonocraft.json} ({@code ownsPlacement}), so our own HUD
 * editor and OneConfig's always agree. Loaded only when OneConfig is installed (see {@link OneConfigIntegration}).
 */
final class OneConfigHud implements OneConfigHudWrapper {
    private String id = Sonocraft.MOD_ID + ":now_playing";
    private String name = Sonocraft.NAME + " – Now Playing";

    static boolean isEditorOpen() {
        try {
            return HudManager.INSTANCE.isEditorOpen();
        } catch (LinkageError e) {
            return false;
        }
    }

    private static HudConfig config() {
        return SonocraftCore.get().config.get().hud;
    }

    private static HudLayout layout() {
        return SonocraftCore.get().hud.layout();
    }

    private static int screenWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    private static int screenHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    /** Places the panel's top-left corner, picking the nearest anchor like our own editor does. */
    private static void moveTo(float x, float y) {
        HudLayout l = layout();
        HudLayout.storePosition(config(), x, y, l.width * l.scale, l.height * l.scale, screenWidth(), screenHeight());
        l.place(config(), screenWidth(), screenHeight());
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getModId() {
        return Sonocraft.MOD_ID;
    }

    @Override
    public float getX() {
        return layout().screenX;
    }

    @Override
    public void setX(float x) {
        moveTo(x, layout().screenY);
    }

    @Override
    public float getY() {
        return layout().screenY;
    }

    @Override
    public void setY(float y) {
        moveTo(layout().screenX, y);
    }

    @Override
    public float getScale() {
        return config().scale;
    }

    @Override
    public void setScale(float scale) {
        config().scale = Math.max(0.5f, Math.min(3f, scale));
        layout().place(config(), screenWidth(), screenHeight());
    }

    @Override
    public float getScaledWidth() {
        HudLayout l = layout();
        return l.width * config().scale;
    }

    @Override
    public void setScaledWidth(float width) {
        // the panel sizes itself from its contents (resizeAxes = None)
    }

    @Override
    public float getScaledHeight() {
        HudLayout l = layout();
        return l.height * config().scale;
    }

    @Override
    public void setScaledHeight(float height) {
    }

    @Override
    public boolean getHidden() {
        return !config().enabled;
    }

    @Override
    public void setHidden(boolean hidden) {
        config().enabled = !hidden;
        SonocraftCore.get().config.save();
    }

    @Override
    public boolean getOwnsPlacement() {
        return true;
    }

    @Override
    public HudResize getResizeAxes() {
        return HudResize.None;
    }

    /** The HUD settings, shown in the side panel of OneConfig's HUD editor. */
    @Override
    public List<Property<?>> linkedProperties() {
        return HudProperties.create();
    }

    @Override
    public void onDragEnd() {
        SonocraftCore.get().config.save();
    }

    @Override
    public void save() {
        SonocraftCore.get().config.save();
    }
}
