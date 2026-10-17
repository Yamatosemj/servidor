package br.com.auren.minecraft.client;

import br.com.auren.minecraft.entity.AurenDragon;
import br.com.auren.minecraft.AurenMinecraft;
import br.com.auren.minecraft.network.AurenMenuActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Vanilla/NeoForge Screen with its own painted widgets. No extra GUI runtime,
 * no fake clickable features, and no direct client-side world mutations.
 */
public final class AurenMenuScreen extends Screen {
    private static final int CYAN = 0xFF79DDF5;
    private static final int TEXT = 0xFFE6F3F9;
    private static final int MUTED = 0xFF94A9B9;
    private static final int PANEL = 0xF5121C2B;
    private static final int SURFACE = 0xFF202D3F;
    private static final int LINE = 0xFF33465A;
    private static final int GOLD = 0xFFF5C67C;
    private static final int GREEN = 0xFF8FE3B3;
    private int tab; // 0 Principal, 1 Combate, 2 Autonomia
    private int left, top, panelWidth, panelHeight;
    private float scale = 1.0F;
    private String notice = "";

    public AurenMenuScreen() {
        super(Component.literal("Auren - Painel de controle"));
    }

    @Override
    protected void init() {
        // Keep the approved proportions on narrow windows and high GUI scale.
        panelWidth = 418;
        panelHeight = 290;
        scale = Math.min(1.0F, Math.min((width - 18) / 418.0F,
                (height - 16) / 290.0F));
        scale = Math.max(0.20F, scale);
        left = Math.round((width / scale - panelWidth) / 2.0F);
        top = Math.round((height / scale - panelHeight) / 2.0F);
    }

    private AurenDragon dragon() {
        return AurenAttackOverlay.nearbyOwnedDragon(Minecraft.getInstance());
    }

    private boolean hasBond() {
        if (Minecraft.getInstance().player == null) return false;
        var inv = Minecraft.getInstance().player.getInventory();
        for (var stack : inv.items)
            if (stack.is(AurenMinecraft.BOND_ITEM.get())) return true;
        for (var stack : inv.offhand)
            if (stack.is(AurenMinecraft.BOND_ITEM.get())) return true;
        return false;
    }

    private static void box(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        g.fill(x + 3, y, x + w - 3, y + h, color);
        g.fill(x, y + 3, x + w, y + h - 3, color);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
    }

    private static void frame(GuiGraphics g, int x, int y, int w, int h,
                              int border, int fill) {
        box(g, x, y, w, h, border);
        box(g, x + 1, y + 1, w - 2, h - 2, fill);
    }

    private void label(GuiGraphics g, String message, int x, int y, int color) {
        g.drawString(font, message, x, y, color, false);
    }

    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean hover(int mx, int my, int x, int y, int w, int h) {
        return inside(mx, my, x, y, w, h);
    }

    private void tab(GuiGraphics g, int index, String name, int mx, int my) {
        int gap = 7;
        int tabWidth = (panelWidth - 36 - gap * 2) / 3;
        int x = left + 18 + index * (tabWidth + gap);
        int y = top + 82;
        boolean active = tab == index;
        frame(g, x, y, tabWidth, 25, active ? 0xFF3C8BA9 : LINE,
                active ? 0xFF214354 : hover(mx, my, x, y, tabWidth, 25)
                        ? 0xFF2C3B4E : SURFACE);
        label(g, name, x + (tabWidth - font.width(name)) / 2,
                y + 8, active ? CYAN : TEXT);
    }

    private void tile(GuiGraphics g, int col, int row, String title, String desc,
                      String glyph, int mx, int my, boolean enabled) {
        int gap = 9;
        int w = (panelWidth - 36 - gap) / 2;
        int yStart = top + 118;
        int h = Math.max(35, Math.min(63, (panelHeight - 149) / 2 - 4));
        int x = left + 18 + col * (w + gap);
        int y = yStart + row * (h + 9);
        boolean highlighted = enabled && hover(mx, my, x, y, w, h);
        frame(g, x, y, w, h, highlighted ? 0xFF4C9DB3 : LINE,
                highlighted ? 0xFF2A4554 : SURFACE);
        label(g, glyph, x + 10, y + 9, enabled ? CYAN : MUTED);
        label(g, title, x + 28, y + 9, enabled ? TEXT : MUTED);
        if (h >= 48) label(g, desc, x + 10, y + 29, MUTED);
    }

    private int tileHeight() {
        return Math.max(35, Math.min(63, (panelHeight - 149) / 2 - 4));
    }

    private void actionRow(GuiGraphics g, int row, String title, String desc,
                           int mx, int my, boolean enabled) {
        int x = left + 18, y = top + 117 + row * 37;
        int w = panelWidth - 36, h = 32;
        boolean highlighted = enabled && hover(mx, my, x, y, w, h);
        frame(g, x, y, w, h, highlighted ? 0xFF4C9DB3 : LINE,
                highlighted ? 0xFF2B4355 : SURFACE);
        label(g, title, x + 10, y + 6, enabled ? TEXT : MUTED);
        label(g, desc, x + 10, y + 18, MUTED);
        label(g, enabled ? ">" : "-", x + w - 17, y + 11, enabled ? CYAN : MUTED);
    }

    /**
     * This overlay paints its own dim backdrop. Vanilla's full-screen blur is
     * unnecessary and can blur the already drawn menu on some 1.21.1 render
     * paths / shader combinations. Never issue a post-process blur here.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY,
                                 float partialTick) {
        // Intentionally empty; the translucent backdrop is drawn in render().
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        // Render any parent widgets BEFORE our foreground; never blur or
        // redraw a background after the menu text was drawn.
        super.render(g, mx, my, partialTick);
        // Real Minecraft world remains visible beneath a dark, translucent HUD.
        g.fill(0, 0, width, height, 0x9C07101C);
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1.0F);
        mx = Math.round(mx / scale);
        my = Math.round(my / scale);
        frame(g, left, top, panelWidth, panelHeight, 0xFF3B6075, PANEL);
        g.fill(left + 3, top + 3, left + panelWidth - 3, top + 6, 0xFF3C9BBD);
        label(g, "AUREN", left + 18, top + 15, CYAN);
        label(g, "Seu companheiro dragao", left + 18, top + 29, MUTED);

        AurenDragon d = dragon();
        int hx = left + panelWidth - 133;
        if (d != null) {
            int hp = Math.max(0, (int) Math.ceil(d.getHealth()));
            int max = Math.max(1, (int) Math.ceil(d.getMaxHealth()));
            label(g, "Vida  " + hp + " / " + max, hx, top + 17, TEXT);
            box(g, hx, top + 32, 116, 6, 0xFF344254);
            box(g, hx, top + 32, Math.max(0, Math.min(116, (int) (116.0F * hp / max))),
                    6, 0xFFE77F91);
        } else {
            label(g, hasBond() ? "Na sua cabeca" : "Auren distante", hx, top + 19, GOLD);
        }
        String status = d == null ? (hasBond()
                ? "Auren acoplado - invulneravel, pronto para descer"
                : "Aproxime-se do seu Auren (ate 64 blocos)")
                : "Forma: " + (d.isSmallForm() ? "pequena" : "adulta")
                    + "   |   " + (d.getSyncedMarkMode() == 0 ? "Sem alvo"
                    : d.getSyncedMarkMode() == 1 ? "Criatura marcada" : "Area marcada");
        label(g, status, left + 18, top + 57, MUTED);

        tab(g, 0, "Principal", mx, my);
        tab(g, 1, "Combate", mx, my);
        tab(g, 2, "Autonomia", mx, my);
        if (tab == 0) {
            tile(g, 0, 0, hasBond() ? "Soltar Auren" : "Interagir",
                    hasBond() ? "Sair da sua cabeca" : "Subir na cabeca",
                    "*", mx, my, d != null || hasBond());
            tile(g, 1, 0, "Descansar", "Deitar e repousar", "Z", mx, my, d != null);
            tile(g, 0, 1, "Mudar forma", "Pequeno / adulto", "~", mx, my, d != null);
            tile(g, 1, 1, d != null && d.isOrderedToSit() ? "Seguir" : "Seguir / Ficar",
                    "Alternar acompanhamento", ">", mx, my, d != null);
        } else if (tab == 1) {
            actionRow(g, 0, "Marcar criatura", "Mirar no alvo e confirmar com botao direito", mx, my, d != null);
            actionRow(g, 1, "Marcar area", "Escolher local e raio de ataque", mx, my, d != null);
            actionRow(g, 2, "Cancelar ataque", "Retirar a ordem atual", mx, my, d != null);
            int radius = AurenAttackOverlay.selectedRadius();
            label(g, "Raio de area:", left + 20, top + 232, MUTED);
            for (int i = 0; i < 6; i++) {
                int r = (i + 1) * 5;
                int rw = Math.max(21, (panelWidth - 140) / 6);
                int rx = left + 108 + i * rw;
                boolean active = r == radius;
                frame(g, rx, top + 226, rw - 4, 23,
                        active ? 0xFF3B9FBB : LINE, active ? 0xFF214354 : SURFACE);
                label(g, String.valueOf(r), rx + (rw - 4 - font.width(String.valueOf(r))) / 2,
                        top + 233, active ? CYAN : TEXT);
            }
            String bomb = d == null ? "Bombardeio: --" :
                    "Bombardeio: " + (d.isSyncedAreaAttackEnabled() ? "LIGADO" : "DESLIGADO");
            label(g, bomb + "  (clique para alternar)", left + 19, top + 259,
                    d != null && d.isSyncedAreaAttackEnabled() ? GREEN : MUTED);
        } else {
            actionRow(g, 0, "Cacar animais", "Em desenvolvimento - opcao indisponivel", mx, my, false);
            actionRow(g, 1, "Voar espontaneamente", "Em desenvolvimento - opcao indisponivel", mx, my, false);
            actionRow(g, 2, "Explorar por vontade propria", "Em desenvolvimento - opcao indisponivel", mx, my, false);
            actionRow(g, 3, "Ver ambiente", "Mostrar observacoes atuais no jogo", mx, my, d != null);
        }
        String footer = notice.isEmpty() ? "V: fechar   |   ESC: voltar ao jogo" : notice;
        label(g, footer, left + 18, top + panelHeight - 15, notice.isEmpty() ? MUTED : GOLD);
        g.pose().popPose();
    }

    private void send(int action) {
        if (dragon() == null
                && !(action == AurenMenuActionPayload.SHOULDER && hasBond())) {
            notice = "Auren precisa estar perto de voce.";
            return;
        }
        PacketDistributor.sendToServer(new AurenMenuActionPayload(action, 0));
        notice = "Comando enviado. Aguarde a confirmacao do Auren.";
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        mx /= scale;
        my /= scale;
        int gap = 7;
        int tw = (panelWidth - 36 - gap * 2) / 3;
        for (int i = 0; i < 3; ++i) {
            int x = left + 18 + i * (tw + gap);
            if (inside(mx, my, x, top + 82, tw, 25)) {
                tab = i;
                notice = "";
                return true;
            }
        }
        if (tab == 0) {
            int cw = (panelWidth - 36 - 9) / 2;
            int th = tileHeight();
            for (int r = 0; r < 2; ++r) for (int c = 0; c < 2; ++c) {
                if (!inside(mx, my, left + 18 + c * (cw + 9),
                        top + 118 + r * (th + 9), cw, th)) continue;
                if (r == 0 && c == 0) send(AurenMenuActionPayload.SHOULDER);
                if (r == 0 && c == 1) send(AurenMenuActionPayload.REST);
                if (r == 1 && c == 0) send(AurenMenuActionPayload.FORM);
                if (r == 1 && c == 1) send(AurenMenuActionPayload.FOLLOW_OR_STAY);
                return true;
            }
        } else if (tab == 1) {
            if (inside(mx, my, left + 18, top + 117, panelWidth - 36, 32)) {
                beginSelection(false);
                return true;
            }
            if (inside(mx, my, left + 18, top + 154, panelWidth - 36, 32)) {
                beginSelection(true);
                return true;
            }
            if (inside(mx, my, left + 18, top + 191, panelWidth - 36, 32)) {
                send(AurenMenuActionPayload.STOP_ATTACK);
                return true;
            }
            int rw = Math.max(21, (panelWidth - 140) / 6);
            for (int i = 0; i < 6; ++i) {
                if (inside(mx, my, left + 108 + i * rw, top + 226, rw - 4, 23)) {
                    AurenAttackOverlay.setSelectedRadius((i + 1) * 5);
                    return true;
                }
            }
            if (inside(mx, my, left + 18, top + 254, panelWidth - 36, 18)) {
                send(AurenMenuActionPayload.TOGGLE_AREA);
                return true;
            }
        } else if (tab == 2 && inside(mx, my, left + 18, top + 228, panelWidth - 36, 32)) {
            if (dragon() != null) {
                AurenAttackOverlay.revealPerceptionForTicks(Minecraft.getInstance());
                Minecraft.getInstance().setScreen(null);
            } else {
                notice = "Auren precisa estar perto de voce.";
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void beginSelection(boolean area) {
        AurenDragon d = dragon();
        if (d == null || d.isSmallForm() || d.isVehicle() || d.isOnShoulder()) {
            notice = "Use o Auren adulto e desmontado, perto de voce.";
            return;
        }
        ClientEvents.beginSelection(area);
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (ClientEvents.matchesMenuKey(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
