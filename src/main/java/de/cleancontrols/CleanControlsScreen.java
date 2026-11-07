package de.cleancontrols;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class CleanControlsScreen extends Screen {
    private static final String[] TAB_NAMES = {"Wichtig", "Weniger wichtig", "Am unwichtigsten"};

    private static final Set<String> IMPORTANT = Set.of(
            "key.forward", "key.back", "key.left", "key.right", "key.jump", "key.sneak", "key.sprint",
            "key.attack", "key.use", "key.pickItem", "key.inventory", "key.drop", "key.chat");
    private static final Set<String> LESS = Set.of(
            "key.swapOffhand", "key.playerlist", "key.screenshot", "key.togglePerspective", "key.smoothCamera",
            "key.fullscreen", "key.command", "key.advancements", "key.socialInteractions",
            "key.saveToolbarActivator", "key.loadToolbarActivator", "key.spectatorOutlines");

    private record Entry(KeyBinding key, String name, String hay, int cat) {}
    private record Row(Entry entry, String header) {}

    private final Screen parent;
    private final GameOptions options;
    private final List<Entry> all = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final ButtonWidget[] tabButtons = new ButtonWidget[3];
    private TextFieldWidget search;
    private int tab = 0;
    private double scroll = 0;
    private KeyBinding listening = null;
    private String query = "";

    public CleanControlsScreen(Screen parent, GameOptions options) {
        super(Text.literal("Steuerung"));
        this.parent = parent;
        this.options = options;
    }

    private static int classify(KeyBinding k) {
        String id = k.getTranslationKey();
        if (IMPORTANT.contains(id)) return 0;
        if (LESS.contains(id) || id.startsWith("key.hotbar.")) return 1;
        return 2; // alles andere (Mods etc.)
    }

    @Override
    protected void init() {
        all.clear();
        for (KeyBinding k : options.allKeys) {
            String name = Text.translatable(k.getTranslationKey()).getString();
            String cat = "";
            try { cat = k.getCategory().getLabel().getString(); } catch (Throwable ignored) {}
            String hay = (name + " " + k.getTranslationKey() + " " + cat).toLowerCase(Locale.ROOT);
            all.add(new Entry(k, name, hay, classify(k)));
        }
        all.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));

        int cx = width / 2;
        search = new TextFieldWidget(textRenderer, cx - 170, 22, 340, 20, Text.literal("Suche"));
        search.setPlaceholder(Text.literal("Suchen... (z.B. fps, sprint, zoom)"));
        search.setMaxLength(64);
        search.setText(query);
        search.setChangedListener(s -> { query = s; scroll = 0; rebuild(); });
        addDrawableChild(search);

        for (int i = 0; i < 3; i++) {
            final int idx = i;
            tabButtons[i] = ButtonWidget.builder(Text.literal(TAB_NAMES[i]), b -> { tab = idx; scroll = 0; rebuild(); })
                    .dimensions(cx - 170 + i * 114, 46, 112, 20).build();
            addDrawableChild(tabButtons[i]);
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Alle zuruecksetzen"), b -> {
            for (KeyBinding k : options.allKeys) k.setBoundKey(k.getDefaultKey());
            save();
        }).dimensions(cx - 170, height - 28, 166, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Fertig"), b -> close())
                .dimensions(cx + 4, height - 28, 166, 20).build());

        setInitialFocus(search);
        rebuild();
    }

    private void rebuild() {
        rows.clear();
        String q = query.trim().toLowerCase(Locale.ROOT);
        boolean searching = !q.isEmpty();
        String[] tokens = q.split("\\s+");
        int[] counts = new int[3];
        for (Entry e : all) counts[e.cat()]++;

        if (searching) {
            for (int c = 0; c < 3; c++) {
                List<Row> part = new ArrayList<>();
                for (Entry e : all) {
                    if (e.cat() != c) continue;
                    boolean ok = true;
                    for (String t : tokens) if (!e.hay().contains(t)) { ok = false; break; }
                    if (ok) part.add(new Row(e, null));
                }
                if (!part.isEmpty()) {
                    rows.add(new Row(null, TAB_NAMES[c]));
                    rows.addAll(part);
                }
            }
        } else {
            for (Entry e : all) if (e.cat() == tab) rows.add(new Row(e, null));
        }

        for (int i = 0; i < 3; i++) {
            String mark = (!searching && i == tab) ? "> " : "";
            tabButtons[i].setMessage(Text.literal(mark + TAB_NAMES[i] + " (" + counts[i] + ")"));
        }
    }

    private int rowHeight(Row r) { return r.header() != null ? 18 : 24; }

    private int listTop() { return 74; }
    private int listBottom() { return height - 36; }

    private int contentHeight() {
        int h = 8;
        for (Row r : rows) h += rowHeight(r);
        return h;
    }

    private boolean hasConflict(KeyBinding k) {
        if (k.isUnbound()) return false;
        for (KeyBinding o : options.allKeys) if (o != k && k.equals(o)) return true;
        return false;
    }

    private void save() {
        KeyBinding.updateKeysByCode();
        options.write();
        listening = null;
        rebuild();
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFFFFF);

        int left = width / 2 - 170, right = width / 2 + 170, top = listTop(), bottom = listBottom();
        int maxScroll = Math.max(0, contentHeight() - (bottom - top));
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        ctx.fill(left - 4, top, right + 4, bottom, 0x90000000);
        ctx.enableScissor(left - 4, top, right + 4, bottom);
        int y = top + 4 - (int) scroll;
        for (Row r : rows) {
            int h = rowHeight(r);
            if (y + h >= top && y <= bottom) {
                if (r.header() != null) {
                    ctx.drawTextWithShadow(textRenderer, Text.literal("-- " + r.header() + " --"), left + 4, y + 5, 0xFFFFD27F);
                } else {
                    drawRow(ctx, r.entry(), left, right, y, mx, my);
                }
            }
            y += h;
        }
        if (rows.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Nichts gefunden"), width / 2, top + 20, 0xFFAAAAAA);
        }
        ctx.disableScissor();
    }

    private void drawRow(DrawContext ctx, Entry e, int left, int right, int y, int mx, int my) {
        KeyBinding k = e.key();
        boolean conflict = hasConflict(k);
        int nameColor = conflict ? 0xFFFF5555 : 0xFFFFFFFF;
        String name = textRenderer.trimToWidth(e.name(), 160);
        ctx.drawTextWithShadow(textRenderer, Text.literal(name), left + 6, y + 8, nameColor);

        int kx1 = right - 160, kx2 = right - 50, rx1 = right - 46, rx2 = right - 4;
        boolean inList = my >= listTop() && my < listBottom();
        boolean hoverKey = inList && mx >= kx1 && mx < kx2 && my >= y + 2 && my < y + 22;
        boolean hoverReset = inList && mx >= rx1 && mx < rx2 && my >= y + 2 && my < y + 22;
        boolean isListening = listening == k;

        ctx.fill(kx1, y + 2, kx2, y + 22, isListening ? 0xFF806000 : (hoverKey ? 0xFF505050 : 0xFF2A2A2A));
        String label = isListening ? "> Taste druecken <" : textRenderer.trimToWidth(k.getBoundKeyLocalizedText().getString(), 104);
        int lc = isListening ? 0xFFFFFF55 : (conflict ? 0xFFFF5555 : 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(label), (kx1 + kx2) / 2, y + 8, lc);

        boolean changed = !k.isDefault();
        ctx.fill(rx1, y + 2, rx2, y + 22, hoverReset && changed ? 0xFF505050 : 0xFF2A2A2A);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Reset"), (rx1 + rx2) / 2, y + 8, changed ? 0xFFFFFFFF : 0xFF666666);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (listening != null) {
            listening.setBoundKey(InputUtil.Type.MOUSE.createFromCode(click.button()));
            save();
            return true;
        }
        double mx = click.x(), my = click.y();
        int left = width / 2 - 170, right = width / 2 + 170;
        if (click.button() == 0 && my >= listTop() && my < listBottom()) {
            int y = listTop() + 4 - (int) scroll;
            for (Row r : rows) {
                int h = rowHeight(r);
                if (r.entry() != null && my >= y + 2 && my < y + 22) {
                    if (mx >= right - 160 && mx < right - 50) {
                        listening = r.entry().key();
                        return true;
                    }
                    if (mx >= right - 46 && mx < right - 4) {
                        r.entry().key().setBoundKey(r.entry().key().getDefaultKey());
                        save();
                        return true;
                    }
                }
                y += h;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (listening != null) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                listening.setBoundKey(InputUtil.UNKNOWN_KEY);
            } else {
                listening.setBoundKey(InputUtil.fromKeyCode(input));
            }
            save();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        scroll -= vertical * 24;
        return true;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
