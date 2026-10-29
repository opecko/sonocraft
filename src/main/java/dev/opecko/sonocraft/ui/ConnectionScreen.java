package dev.opecko.sonocraft.ui;

import dev.opecko.sonocraft.SonocraftCore;
import dev.opecko.sonocraft.Texts;
import dev.opecko.sonocraft.api.Scope;
import dev.opecko.sonocraft.api.session.SessionState;
import dev.opecko.sonocraft.api.session.SessionStatus;
import dev.opecko.sonocraft.api.session.SonolithSession;
import dev.opecko.sonocraft.compat.Gfx;
import dev.opecko.sonocraft.compat.Mc;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Live view of the Sonolith connection: state with countdowns, granted permissions, and the user actions the spec
 * requires (Connect only on a click, a visible "Disconnect from Sonolith", "Uses Sonolith" credit; SPEC §6, §9).
 */
public final class ConnectionScreen extends Screen {
    public static final URI SONOLITH = URI.create("https://github.com/opecko/sonolith");
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFAAAAAA;
    private static final int GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555;

    private final Screen parent;
    private Button connect;
    private Button cancel;
    private Button disconnect;
    private Button allow;
    private volatile boolean disconnecting;

    public ConnectionScreen(Screen parent) {
        super(Component.translatable("sonocraft.connection.title"));
        this.parent = parent;
    }

    private static SonolithSession session() {
        return SonocraftCore.get().session;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int y = height - 52;
        connect = addRenderableWidget(Button.builder(Component.translatable("sonocraft.connection.connect"),
                b -> session().connect()).bounds(cx - 154, y, 100, 20).build());
        cancel = addRenderableWidget(Button.builder(Component.translatable("sonocraft.connection.cancel"),
                b -> session().cancelRegistration()).bounds(cx - 154, y, 100, 20).build());
        disconnect = addRenderableWidget(Button.builder(Component.translatable("sonocraft.connection.disconnect"),
                b -> confirmDisconnect()).bounds(cx - 50, y, 100, 20).build());
        allow = addRenderableWidget(Button.builder(Component.translatable("sonocraft.connection.allow"),
                b -> session().requestScopes(missingScopes(session().status()))).bounds(cx + 54, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("sonocraft.connection.uses_sonolith"),
                ConfirmLinkScreen.confirmLink(this, SONOLITH)).bounds(cx - 154, y + 24, 150, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(cx + 4, y + 24, 150, 20).build());
        refresh();
    }

    private void confirmDisconnect() {
        Mc.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                disconnecting = true;
                session().disconnect().whenComplete((r, e) -> disconnecting = false);
            }
            Mc.setScreen(this);
        }, Component.translatable("sonocraft.connection.disconnect.title"),
                Component.translatable("sonocraft.connection.disconnect.message")));
    }

    /** Optional scopes the user can still allow. */
    private static List<Scope> missingScopes(SessionStatus s) {
        List<Scope> out = new ArrayList<>();
        for (Scope scope : session().registration().scopes().keySet()) {
            if (!s.has(scope)) {
                out.add(scope);
            }
        }
        return out;
    }

    @Override
    public void tick() {
        refresh();
    }

    private void refresh() {
        SessionStatus s = session().status();
        SessionState st = s.state();
        long remaining = s.remainingMs(System.currentTimeMillis());
        boolean pending = st == SessionState.REGISTERING || st == SessionState.IDENTITY_PENDING;
        cancel.visible = pending;
        connect.visible = !pending;
        boolean onHold = st == SessionState.COOLDOWN && remaining > 0;
        connect.active = st != SessionState.CONNECTED && st != SessionState.CONNECTING && !onHold;
        connect.setMessage(onHold
                ? Component.translatable("sonocraft.connection.connect.wait", Texts.duration(remaining))
                : Component.translatable(st == SessionState.UNAVAILABLE
                        ? "sonocraft.connection.retry" : "sonocraft.connection.connect"));
        // a token may exist in every state except these; disconnect must also work while Sonolith is down
        disconnect.active = !disconnecting && !pending && st != SessionState.NOT_CONNECTED
                && st != SessionState.BLOCKED && st != SessionState.COOLDOWN;
        allow.visible = st == SessionState.CONNECTED && !missingScopes(s).isEmpty();
    }

    //? if >=26.1 {
    @Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        draw(new Gfx(g));
    }
    //?} else {
    /*@Override
    public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        draw(new Gfx(g));
    }
    *///?}

    private void draw(Gfx g) {
        int cx = width / 2;
        int left = cx - 154;
        int textWidth = 308;
        g.centeredText(title, cx, 15, WHITE);
        SessionStatus s = session().status();
        int y = 34;
        y += g.wrappedText(Texts.status(s), left, y, textWidth, s.state() == SessionState.CONNECTED ? GREEN : WHITE);
        y += 4;
        if (s.state() == SessionState.CONNECTED) {
            if (s.appVersion() != null) {
                g.text(Component.translatable("sonocraft.connection.version", s.appVersion()), left, y, GRAY, false);
                y += 10;
            }
            if (s.signedIn() != null) {
                g.text(Component.translatable(s.signedIn() ? "sonocraft.connection.signed_in"
                        : "sonocraft.connection.signed_out"), left, y, GRAY, false);
                y += 10;
            }
            y += 6;
            g.text(Component.translatable("sonocraft.connection.permissions"), left, y, WHITE, false);
            y += 12;
            List<Scope> scopes = new ArrayList<>(session().registration().scopes().keySet());
            int col = textWidth / 2;
            for (int i = 0; i < scopes.size(); i++) {
                Scope scope = scopes.get(i);
                boolean granted = s.has(scope);
                Component label = Component.literal(granted ? "✔ " : "✘ ").append(
                        Component.translatable("sonocraft.scope." + scope.name().toLowerCase(Locale.ROOT)));
                g.text(label, left + (i % 2) * col, y + (i / 2) * 11, granted ? GREEN : RED, false);
            }
            y += (scopes.size() + 1) / 2 * 11 + 6;
        }
        if (s.identity() != null && s.identity().key() != null
                && (s.state() == SessionState.REGISTERING || s.state() == SessionState.IDENTITY_PENDING
                || s.state() == SessionState.CONNECTED)) {
            g.wrappedText(Component.translatable("sonocraft.connection.identity", s.identity().key()), left, y,
                    textWidth, GRAY);
        }
    }

    @Override
    public void onClose() {
        Mc.setScreen(parent);
    }
}
