package com.example.cajadomod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public class DestructionStaffClient implements ClientModInitializer {

    private static final int BAR_WIDTH = 160;
    private static final int BAR_HEIGHT = 12;
    private static final int SEGMENTS = 20;

    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register((DrawContext ctx, float tickDelta) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            ClientPlayerEntity player = mc.player;
            if (player == null || mc.options.hudHidden) return;
            ItemStack active = player.getActiveItem();
            if (active.isEmpty() || !(active.getItem() instanceof DestructionStaffItem)) return;
            int used = player.getItemUseTime();
            if (used <= 0) return;
            float charge = Math.max(0.0f, Math.min(2.0f, used / 60.0f));
            drawChargeBar(ctx, mc, player, charge, active);
        });
    }

    private void drawChargeBar(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity player, float charge, ItemStack active) {
        TextRenderer tr = mc.textRenderer;
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        int x = (sw - BAR_WIDTH) / 2;
        int y = sh - 58;
        long time = System.currentTimeMillis();

        // Fundo + borda dupla
        ctx.fill(x - 4, y - 4, x + BAR_WIDTH + 4, y + BAR_HEIGHT + 4, 0xAA000000);
        int borderColor = charge >= 2.0f ? 0xFFE040FB : (charge >= 1.0f ? 0xFFFFEB3B : 0xFF5A5A72);
        ctx.fill(x - 4, y - 4, x + BAR_WIDTH + 4, y - 3, borderColor);
        ctx.fill(x - 4, y + BAR_HEIGHT + 3, x + BAR_WIDTH + 4, y + BAR_HEIGHT + 4, borderColor);
        ctx.fill(x - 4, y - 4, x - 3, y + BAR_HEIGHT + 4, borderColor);
        ctx.fill(x + BAR_WIDTH + 3, y - 4, x + BAR_WIDTH + 4, y + BAR_HEIGHT + 4, borderColor);
        ctx.fill(x - 2, y - 2, x + BAR_WIDTH + 2, y + BAR_HEIGHT + 2, 0xDD0A0A12);

        // Preenchimento em gradiente por coluna
        int fillWidth = (int)(charge / 2.0f * BAR_WIDTH);
        for (int i = 0; i < fillWidth; i++) {
            float t = i / (float) BAR_WIDTH;
            int color;
            if (t < 0.25f) {
                color = lerpColor(0xFFFFF176, 0xFFFFEB3B, t / 0.25f);
            } else if (t < 0.5f) {
                color = lerpColor(0xFFFFEB3B, 0xFFFF9800, (t - 0.25f) / 0.25f);
            } else if (t < 0.75f) {
                color = lerpColor(0xFFFF9800, 0xFFFF1744, (t - 0.5f) / 0.25f);
            } else {
                color = lerpColor(0xFFFF1744, 0xFFB71C1C, (t - 0.75f) / 0.25f);
            }
            ctx.fill(x + i, y, x + i + 1, y + BAR_HEIGHT, color);
            // brilho superior
            ctx.fill(x + i, y, x + i + 1, y + 2, blendWhite(color, 0.35f));
        }

        // Zona de OVERCHARGE (metade direita) pulsando em roxo
        if (charge > 1.0f) {
            int overWidth = (int)((charge - 1.0f) * (BAR_WIDTH / 2.0f));
            float pulse = (float)(0.65 + 0.35 * Math.sin(time / 100.0));
            int alpha = ((int)(pulse * 200) + 55) << 24;
            for (int i = 0; i < overWidth; i++) {
                float t = i / (float)(BAR_WIDTH / 2.0f);
                int base = lerpColor(0xFFE040FB, 0xFF651FFF, t) & 0x00FFFFFF;
                int px2 = x + BAR_WIDTH / 2 + i;
                ctx.fill(px2, y, px2 + 1, y + BAR_HEIGHT, base | alpha);
                ctx.fill(px2, y, px2 + 1, y + 2, 0x66FFFFFF);
            }
        }

        // Divisores de segmento
        for (int s = 1; s < SEGMENTS; s++) {
            int sx = x + s * (BAR_WIDTH / SEGMENTS);
            ctx.fill(sx, y, sx + 1, y + BAR_HEIGHT, 0x55000000);
        }
        // Marcador de 100%
        ctx.fill(x + BAR_WIDTH / 2, y - 2, x + BAR_WIDTH / 2 + 1, y + BAR_HEIGHT + 2, 0xFFFFFFFF);

        // Faíscas animadas quando OVERCHARGE completo
        if (charge >= 2.0f) {
            for (int s = 0; s < 8; s++) {
                float ph = (float)(((time / 500.0) + s / 8.0) % 1.0);
                int sparkX = x + (int)(ph * BAR_WIDTH);
                int rise = (int)((time / 40 + s * 11) % 10);
                int sparkY = y - 4 - rise;
                int a = Math.max(0, 220 - rise * 22) << 24;
                ctx.fill(sparkX, sparkY, sparkX + 2, sparkY + 2, a | 0x00FF80FF);
            }
            // brilho pulsante na barra inteira
            int glow = ((int)(120 + 100 * Math.sin(time / 90.0))) << 24;
            ctx.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, glow | 0x00FFFFFF);
        }

        // Texto de status
        String status;
        if (charge >= 2.0f) {
            float blink = (float)Math.sin(time / 150.0);
            status = blink > 0 ? "§d§l✦ OVERCHARGE — SOLTE PARA ULTIMATE! ✦" : "§5§l✦ OVERCHARGE — SOLTE PARA ULTIMATE! ✦";
        } else if (charge >= 1.0f) {
            status = "§a§lCARGA COMPLETA §7- §dsegure para OVERCHARGE";
        } else {
            status = "§6Carga §e" + (int)(charge * 100) + "%";
        }
        int tw = tr.getWidth(status);
        ctx.drawTextWithShadow(tr, Text.literal(status), x + BAR_WIDTH / 2 - tw / 2, y - 16, 0xFFFFFFFF);

        // Nome do modo
        int mode = active.getOrCreateNbt().getInt("Mode");
        String modeLabel = modeName(mode);
        ctx.drawTextWithShadow(tr, Text.literal(modeLabel), x - 4 - tr.getWidth(modeLabel) - 6, y + 1, 0xFFFFFFFF);
        String pctLabel = (int)(charge * 100) + "%";
        ctx.drawTextWithShadow(tr, Text.literal("§f" + pctLabel), x + BAR_WIDTH + 8, y + 1, 0xFFFFFFFF);
    }

    private String modeName(int mode) {
        switch (mode) {
            case DestructionStaffItem.MODE_FIRE:      return "§c🔥 Fogo";
            case DestructionStaffItem.MODE_LIGHTNING: return "§b⚡ Raio";
            case DestructionStaffItem.MODE_VOID:      return "§5🌀 Vazio";
            case DestructionStaffItem.MODE_TSUNAMI:   return "§9🌊 Tsunami";
            case DestructionStaffItem.MODE_METEOR:    return "§6☄️ Meteoro";
            case DestructionStaffItem.MODE_PLAGUE:    return "§2💀 Praga";
            case DestructionStaffItem.MODE_VORTEX:    return "§d🌪️ Vórtice";
            default:                                  return "§7?";
        }
    }

    private static int lerpColor(int a, int b, float t) {
        if (t < 0) t = 0; if (t > 1) t = 1;
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int)(ar + (br - ar) * t);
        int g = (int)(ag + (bg - ag) * t);
        int bl = (int)(ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int blendWhite(int color, float amount) {
        return lerpColor(color, 0xFFFFFFFF, amount);
    }
}
