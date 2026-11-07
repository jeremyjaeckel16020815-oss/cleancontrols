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
        String id = k.getId();
        if (IMPORTANT.contains(id)) return 0;
        if (LESS.contains(id) || id.startsWith("key.hotbar.")) return 1;
        return 2; // alles andere (Mods etc.)
    }

    @Override
    protected void init() {
        all.clear();
        for (KeyBinding k : options.allKeys) {
            String name = Text.translatable(k.getId()).getString();
            String cat = "";
            try { cat = k.getCategory().getLabel().getString(); } catch (Throwable ignored) {}
            String hay = (name + " " + k.getId() + " " + cat).toLowerCase(Locale.ROOT);
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
        scroll = Math.max(0,
                          
