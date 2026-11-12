package com.manu.frosted.modules.visual;

import com.manu.frosted.FrostedAddon;
import com.manu.frosted.util.FrostedChunkRenderer;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.ItemPhysics;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

/**
 * Item Physics & Frost Glint: synchronizes with Meteor's flat ItemPhysics and
 * renders rotating 3D Frost Rings & Crystal Beacons around dropped items by rarity.
 */
public class FrostedItems extends Module {
    public enum Tier {
        MYTHIC(255, 215, 95),
        VALUABLE(215, 115, 255),
        RARE(95, 225, 255),
        COMMON(185, 230, 255);

        final int r, g, b;

        Tier(int r, int g, int b) {
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> flatPhysics = sgGeneral.add(new BoolSetting.Builder()
        .name("flat-item-physics")
        .description("Automatically enables Meteor's flat ItemPhysics so items lie flat on the ground.")
        .defaultValue(true)
        .onChanged(v -> syncItemPhysics())
        .build()
    );

    private final Setting<Boolean> randomRotation = sgGeneral.add(new BoolSetting.Builder()
        .name("random-rotation")
        .description("Applies random rotation to flat items on the ground.")
        .defaultValue(true)
        .visible(flatPhysics::get)
        .onChanged(v -> syncItemPhysics())
        .build()
    );

    private final Setting<Boolean> showCommon = sgGeneral.add(new BoolSetting.Builder()
        .name("show-common-items")
        .description("Draw a subtle frost ring under regular/common dropped items too.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> valuableBeacon = sgGeneral.add(new BoolSetting.Builder()
        .name("valuable-crystal-beacon")
        .description("Draw a 3D floating frost crystal and light beam on Mythic and Valuable drops (Totems, Netherite, Shulkers).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> maxDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-distance")
        .description("Maximum distance in blocks to render Frost Glint rings on dropped items.")
        .defaultValue(48.0)
        .min(8.0)
        .sliderRange(8.0, 128.0)
        .build()
    );

    private final Color cRing = new Color();
    private final Color cInner = new Color();
    private final Color cFade = new Color();
    private final Color cBeacon = new Color();

    public FrostedItems() {
        super(FrostedAddon.VISUAL, "item-physics-plus", "Flat item physics with 3D rotating Frost Glint rings and crystal beacons for valuable drops.");
    }

    @Override
    public void onActivate() {
        syncItemPhysics();
    }

    @Override
    public void onDeactivate() {
        ItemPhysics ip = Modules.get().get(ItemPhysics.class);
        if (ip != null && ip.isActive()) {
            ip.disable();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!flatPhysics.get()) return;
        ItemPhysics ip = Modules.get().get(ItemPhysics.class);
        if (ip != null && !ip.isActive()) {
            syncItemPhysics();
            ip.enable();
        }
    }

    @SuppressWarnings("unchecked")
    private void syncItemPhysics() {
        ItemPhysics ip = Modules.get().get(ItemPhysics.class);
        if (ip == null) return;
        Setting<?> s = ip.settings.get("random-rotation");
        if (s != null) {
            try {
                ((Setting<Boolean>) s).set(randomRotation.get());
            } catch (Throwable ignored) {}
        }
        if (flatPhysics.get() && !ip.isActive()) {
            ip.enable();
        } else if (!flatPhysics.get() && ip.isActive()) {
            ip.disable();
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        double maxSq = maxDistance.get() * maxDistance.get();
        double time = System.currentTimeMillis() / 1000.0;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ItemEntity itemEntity)) continue;
            if (mc.player.squaredDistanceTo(itemEntity) > maxSq) continue;

            ItemStack stack = itemEntity.getStack();
            if (stack.isEmpty()) continue;

            Tier tier = classify(stack);
            if (tier == Tier.COMMON && !showCommon.get()) continue;

            double ix = itemEntity.lastRenderX + (itemEntity.getX() - itemEntity.lastRenderX) * event.tickDelta;
            double iy = itemEntity.lastRenderY + (itemEntity.getY() - itemEntity.lastRenderY) * event.tickDelta;
            double iz = itemEntity.lastRenderZ + (itemEntity.getZ() - itemEntity.lastRenderZ) * event.tickDelta;

            drawFrostAura(event, ix, iy + 0.03, iz, tier, time, itemEntity.getId());
        }
    }

    private void drawFrostAura(Render3DEvent event, double x, double y, double z, Tier tier, double time, int seed) {
        double radius = switch (tier) {
            case MYTHIC -> 0.58;
            case VALUABLE -> 0.50;
            case RARE -> 0.42;
            case COMMON -> 0.30;
        };

        double spin = time * (tier == Tier.COMMON ? 1.4 : 2.3) + seed * 0.7;
        double pulse = 0.80 + 0.20 * Math.sin(time * 3.5 + seed);

        int alpha = (int) Math.round((tier == Tier.COMMON ? 135 : 225) * pulse);
        cRing.set(tier.r, tier.g, tier.b, alpha);
        cInner.set(Math.min(255, tier.r + 40), Math.min(255, tier.g + 30), 255, (int) (alpha * 0.45));
        cFade.set(tier.r, tier.g, tier.b, 0);

        int points = tier == Tier.COMMON ? 6 : 8;
        double innerR = radius * 0.65;

        for (int i = 0; i < points; i++) {
            double a1 = spin + i * (2.0 * Math.PI / points);
            double a2 = spin + (i + 1) * (2.0 * Math.PI / points);

            double ox1 = x + Math.cos(a1) * radius;
            double oz1 = z + Math.sin(a1) * radius;
            double ox2 = x + Math.cos(a2) * radius;
            double oz2 = z + Math.sin(a2) * radius;

            double ix1 = x + Math.cos(a1) * innerR;
            double iz1 = z + Math.sin(a1) * innerR;
            double ix2 = x + Math.cos(a2) * innerR;
            double iz2 = z + Math.sin(a2) * innerR;

            event.renderer.line(ox1, y, oz1, ox2, y, oz2, cRing);
            event.renderer.quad(ox1, y, oz1, ox2, y, oz2, ix2, y, iz2, ix1, y, iz1,
                cInner, cInner, cFade, cFade);

            if (tier != Tier.COMMON && i % 2 == 0) {
                double spikeR = radius * 1.25;
                double sx = x + Math.cos(a1) * spikeR;
                double sz = z + Math.sin(a1) * spikeR;
                event.renderer.line(ox1, y, oz1, sx, y + 0.04, sz, cRing);
            }
        }

        if (valuableBeacon.get() && (tier == Tier.MYTHIC || tier == Tier.VALUABLE)) {
            cBeacon.set(tier.r, tier.g, tier.b, 230);
            double beamH = tier == Tier.MYTHIC ? 3.4 : 2.2;
            FrostedChunkRenderer.drawBeacon(event.renderer, x, y, z, cBeacon, time, beamH);
        } else if (tier == Tier.RARE) {
            event.renderer.line(x, y, z, x, y + 1.15, z, cRing, cFade);
        }
    }

    private static Tier classify(ItemStack stack) {
        Item item = stack.getItem();

        if (item == Items.TOTEM_OF_UNDYING
            || item == Items.ENCHANTED_GOLDEN_APPLE
            || item == Items.ELYTRA
            || item == Items.MACE
            || item == Items.HEAVY_CORE
            || item == Items.NETHER_STAR
            || item == Items.DRAGON_EGG
            || item == Items.TRIDENT) {
            return Tier.MYTHIC;
        }

        if (item instanceof BlockItem bi) {
            Block b = bi.getBlock();
            if (b instanceof ShulkerBoxBlock) return Tier.VALUABLE;
        }

        if (item == Items.ENDER_CHEST
            || item == Items.NETHERITE_INGOT
            || item == Items.NETHERITE_BLOCK
            || item == Items.ANCIENT_DEBRIS
            || item == Items.NETHERITE_SCRAP
            || item == Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE) {
            return Tier.VALUABLE;
        }

        String id = Registries.ITEM.getId(item).getPath();
        if (id.startsWith("netherite_")) {
            return Tier.VALUABLE;
        }

        if (id.startsWith("diamond_")
            || item == Items.DIAMOND
            || item == Items.DIAMOND_BLOCK
            || item == Items.GOLDEN_APPLE
            || item == Items.ENDER_PEARL
            || item == Items.END_CRYSTAL
            || item == Items.WIND_CHARGE
            || item == Items.EXPERIENCE_BOTTLE
            || item == Items.RESPAWN_ANCHOR
            || item == Items.OBSIDIAN) {
            return Tier.RARE;
        }

        return Tier.COMMON;
    }
}
