package com.manu.frosted.modules.combat;

import com.manu.frosted.FrostedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/**
 * One-shot pearl throw. Toggle it from a keybind: it selects the pearl, throws,
 * and returns to your configured slot without leaving a screen open.
 */
public class KeyPearl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> switchBack = sgGeneral.add(new IntSetting.Builder()
        .name("switch-back-slot")
        .description("Hotbar slot to select after the throw.")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 9)
        .build()
    );

    private final Setting<Integer> cooldownWait = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown-wait")
        .description("Ticks to wait for the pearl cooldown before giving up.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 40)
        .build()
    );

    private int stage;
    private int waited;

    public KeyPearl() {
        super(FrostedAddon.COMBAT, "key-pearl", "Throws an ender pearl and swaps back.");
    }

    @Override
    public void onActivate() {
        stage = 0;
        waited = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.interactionManager == null || mc.currentScreen != null) return;

        switch (stage) {
            case 0 -> {
                int slot = findPearl();
                if (slot == -1) {
                    toggle();
                    return;
                }
                if (mc.player.getInventory().getSelectedSlot() != slot) {
                    mc.player.getInventory().setSelectedSlot(slot);
                }
                stage = 1;
            }
            case 1 -> {
                ItemStack held = mc.player.getMainHandStack();
                if (!held.isOf(Items.ENDER_PEARL)) {
                    stage = 2;
                    return;
                }
                if (mc.player.getItemCooldownManager().isCoolingDown(held)) {
                    if (++waited > cooldownWait.get()) stage = 2;
                    return;
                }
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                stage = 2;
            }
            default -> {
                int slot = switchBack.get() - 1;
                if (mc.player.getInventory().getSelectedSlot() != slot) {
                    mc.player.getInventory().setSelectedSlot(slot);
                }
                toggle();
            }
        }
    }

    private int findPearl() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.ENDER_PEARL)) return i;
        }
        return -1;
    }
}
