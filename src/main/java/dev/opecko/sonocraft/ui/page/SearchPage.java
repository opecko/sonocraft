package dev.opecko.sonocraft.ui.page;

import com.mojang.blaze3d.platform.InputConstants;
import dev.opecko.sonocraft.api.SonolithApi.SearchType;
import dev.opecko.sonocraft.api.model.Item;
import dev.opecko.sonocraft.api.model.SearchSuggestions;
import dev.opecko.sonocraft.compat.Gfx;
import dev.opecko.sonocraft.ui.ItemActions;
import dev.opecko.sonocraft.ui.ItemRow;
import dev.opecko.sonocraft.ui.PagedLoader;
import dev.opecko.sonocraft.ui.UiColors;
import dev.opecko.sonocraft.ui.UiContext;
import dev.opecko.sonocraft.ui.widget.ScrollList;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Search (search.query) with suggestions while typing (search.suggestions) and a type filter. Typing waits
 * 400 ms before asking for suggestions, so the network rate limit (2/s) is never hit (PLAN §3.7).
 */
public final class SearchPage extends Page {
    private static final long DEBOUNCE_MS = 400;
    private static final int TOP = 44;

    private EditBox field;
    private SearchType type = SearchType.ALL;
    private String query = "";
    private Item topResult;
    private PagedLoader<Item> results;
    private final ScrollList<Item> list;

    // suggestions
    private long typedAt;
    private String suggestedFor = "";
    private SearchSuggestions suggestions;
    private boolean showSuggestions;

    private final List<int[]> chipBounds = new ArrayList<>();

    public SearchPage(UiContext ui) {
        super(ui);
        list = new ScrollList<>(this::items, ItemRow.HEIGHT, (g, item, i, x, y, w, h, hover, now) -> {
            ItemRow.render(g, ui, item, x, y, w, h, hover, false, 0);
            if (i == 0 && item == topResult) {
                Component top = Component.translatable("sonocraft.ui.search.top");
                g.text(top, x + w - g.textWidth(top) - 50, y + 3, ui.accent().accent(), false);
            }
        }, this::click);
    }

    private List<Item> items() {
        if (results == null) {
            return List.of();
        }
        List<Item> found = results.items();
        if (topResult == null) {
            return found;
        }
        List<Item> out = new ArrayList<>(found.size() + 1);
        out.add(topResult);
        for (Item i : found) {
            if (!(i.type() == topResult.type() && i.id().equals(topResult.id()))) {
                out.add(i);
            }
        }
        return out;
    }

    @Override
    public Component title() {
        return Component.translatable("sonocraft.ui.tab.search");
    }

    @Override
    public void layout(int x, int y, int width, int height) {
        super.layout(x, y, width, height);
        if (field != null) {
            field.setX(x + 4);
            field.setY(y + 4);
            field.setWidth(width - 8);
        }
        list.setBounds(x, y + TOP, width, height - TOP);
    }

    @Override
    public void onShow() {
        if (field == null) {
            field = new EditBox(Minecraft.getInstance().font, x + 4, y + 4, Math.max(40, width - 8), 16,
                    Component.translatable("sonocraft.ui.tab.search"));
            field.setMaxLength(200);
            field.setHint(Component.translatable("sonocraft.ui.search.hint"));
            field.setResponder(text -> {
                typedAt = System.currentTimeMillis();
                showSuggestions = !text.isBlank();
            });
        }
        ui.addField(field);
        ui.focus(field);
    }

    @Override
    public void onHide() {
        ui.removeField(field);
    }

    /** Ctrl+F from anywhere in the Library screen. */
    public void focusField() {
        if (field != null) {
            ui.focus(field);
        }
    }

    private void search(String text) {
        query = text.trim();
        showSuggestions = false;
        topResult = null;
        if (query.isEmpty()) {
            results = null;
            return;
        }
        String q = query;
        SearchType t = type;
        results = new PagedLoader<>(cursor -> ui.api().searchQuery(q, t, cursor, null).thenApply(r -> {
            if (cursor == null) {
                topResult = r.topResult();
            }
            return new dev.opecko.sonocraft.api.model.Page<>(r.items(), r.nextCursor());
        }), Component.translatable("sonocraft.ui.search.nothing"));
        list.resetScroll();
        results.ensure();
    }

    @Override
    public void render(Gfx g, int mouseX, int mouseY, long now) {
        // ask for suggestions once typing pauses
        String text = field != null ? field.getValue().trim() : "";
        if (showSuggestions && !text.isEmpty() && !text.equals(suggestedFor) && now - typedAt > DEBOUNCE_MS) {
            suggestedFor = text;
            String asked = text;
            ui.api().searchSuggestions(asked).whenCompleteAsync((s, e) -> {
                if (e == null && asked.equals(suggestedFor)) {
                    suggestions = s;
                }
            }, Minecraft.getInstance());
        }

        // type chips
        chipBounds.clear();
        int cx = x + 4;
        int cy = y + 25;
        for (SearchType t : SearchType.values()) {
            Component label = Component.translatable("sonocraft.ui.search.type." + t.name().toLowerCase(Locale.ROOT));
            int w = g.textWidth(label) + 10;
            if (cx + w > x + width) {
                break;
            }
            boolean selected = t == type;
            boolean hover = mouseX >= cx && mouseX < cx + w && mouseY >= cy && mouseY < cy + 14;
            g.fill(cx, cy, cx + w, cy + 14, selected ? ui.accent().accent() : hover ? 0x30FFFFFF : 0x18FFFFFF);
            g.text(label, cx + 5, cy + 3, selected ? ui.accent().onAccent() : UiColors.TEXT, false);
            chipBounds.add(new int[] {cx, cy, w, t.ordinal()});
            cx += w + 3;
        }

        if (results == null) {
            Component hint = Component.translatable("sonocraft.ui.search.start");
            g.text(hint, x + (width - g.textWidth(hint)) / 2, list.y + list.height / 2 - 4, UiColors.TEXT_FAINT, false);
        } else if (!results.renderStatus(g, list.x, list.y, list.width, list.height, mouseX, mouseY, now)) {
            list.render(g, mouseX, mouseY, now);
        }
        if (showSuggestions && suggestions != null && field != null && field.isFocused()) {
            renderSuggestions(g, mouseX, mouseY, now);
        }
    }

    private int suggestionRows() {
        return suggestions.queries().size() + suggestions.items().size();
    }

    private void renderSuggestions(Gfx g, int mouseX, int mouseY, long now) {
        int sx = x + 4;
        int sy = y + 21;
        int sw = width - 8;
        List<String> qs = suggestions.queries();
        List<Item> items = suggestions.items();
        int h = qs.size() * 12 + items.size() * ItemRow.HEIGHT;
        if (h == 0) {
            return;
        }
        g.fill(sx - 1, sy - 1, sx + sw + 1, sy + h + 1, UiColors.DIVIDER);
        g.fill(sx, sy, sx + sw, sy + h, UiColors.MENU);
        int ry = sy;
        for (String q : qs) {
            boolean hover = mouseX >= sx && mouseX < sx + sw && mouseY >= ry && mouseY < ry + 12;
            if (hover) {
                g.fill(sx, ry, sx + sw, ry + 12, UiColors.ROW_HOVER);
            }
            g.text(q, sx + 4, ry + 2, UiColors.TEXT, false);
            ry += 12;
        }
        for (Item item : items) {
            if (item != null) {
                boolean hover = mouseX >= sx && mouseX < sx + sw && mouseY >= ry && mouseY < ry + ItemRow.HEIGHT;
                ItemRow.render(g, ui, item, sx, ry, sw, ItemRow.HEIGHT, hover, false, 0);
            }
            ry += ItemRow.HEIGHT;
        }
    }

    private boolean clickSuggestion(double mx, double my, int button) {
        if (!showSuggestions || suggestions == null || field == null || !field.isFocused()) {
            return false;
        }
        int sx = x + 4;
        int sy = y + 21;
        int sw = width - 8;
        if (mx < sx || mx >= sx + sw || my < sy) {
            return false;
        }
        int ry = sy;
        for (String q : suggestions.queries()) {
            if (my < ry + 12) {
                field.setValue(q);
                search(q);
                return true;
            }
            ry += 12;
        }
        for (Item item : suggestions.items()) {
            if (my < ry + ItemRow.HEIGHT) {
                if (item != null) {
                    showSuggestions = false;
                    if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                        ui.openMenu(ItemActions.menu(ui, item, ItemActions.Origin.NONE, () -> { }), mx, my);
                    } else {
                        ItemActions.activate(ui, item, ItemActions.Origin.NONE);
                    }
                }
                return true;
            }
            ry += ItemRow.HEIGHT;
        }
        return false;
    }

    private void click(Item item, int index, int button, double mx, double my, boolean doubleClick) {
        if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
            ui.openMenu(ItemActions.menu(ui, item, ItemActions.Origin.NONE, () -> { }), mx, my);
        } else if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            ItemActions.activate(ui, item, ItemActions.Origin.NONE);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, boolean doubleClick) {
        if (clickSuggestion(mx, my, button)) {
            return true;
        }
        for (int[] b : chipBounds) {
            if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + 14) {
                type = SearchType.values()[b[3]];
                search(query);
                return true;
            }
        }
        if (results != null && results.mouseClicked(mx, my, button)) {
            return true;
        }
        showSuggestions = false;
        return list.mouseClicked(mx, my, button, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        return list.mouseScrolled(mx, my, amount);
    }

    @Override
    public boolean keyPressed(int key, boolean control) {
        if ((key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) && field != null
                && field.isFocused()) {
            search(field.getValue());
            return true;
        }
        if (field != null && field.isFocused()) {
            return false;
        }
        return list.keyPressed(key);
    }
}
