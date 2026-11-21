package com.custom.nodashbhop.wheel;

import com.custom.nodashbhop.CombinedNetwork;
import com.custom.nodashbhop.JCraftStandCheck;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Fixes two Mandom quirks in JCraft 0.18.2:
 * 1. Allows normal bare-hand / weapon left-click attacks while Mandom is summoned (bypassing JCraft's MinecraftMixin.startAttack block when player has no Spec).
 * 2. Lets pressing JCraft's Barrage key trigger Mandom's Rewind directly when Mandom is active in Stand mode.
 */
public class MandomAttackFixHandler {
    private final WheelController controller;
    private boolean barrageWasDown = false;
    private KeyMapping cachedBarrageKey = null;
    private boolean barrageKeyScanned = false;

    public MandomAttackFixHandler(WheelController controller) {
        this.controller = controller;
    }

    @SubscribeEvent
    public void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT || event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null || mc.screen != null) return;
        if (!StandThemeRegistry.isMandom(JCraftStandCheck.getStandId(mc.player))) return;
        if (JCraftStandCheck.getStandEntity(mc.player) == null) return;
        // If player has a Spec, let JCraft route M1 to Spec normally
        if (JCraftStandCheck.hasSpec(mc.player)) return;

        // Perform normal vanilla left-click swing & attack while Mandom is summoned!
        HitResult hit = mc.hitResult;
        if (hit instanceof EntityHitResult entityHit) {
            mc.gameMode.attack(mc.player, entityHit.getEntity());
            mc.player.swing(InteractionHand.MAIN_HAND);
        } else if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            mc.gameMode.startDestroyBlock(blockHit.getBlockPos(), blockHit.getDirection());
            mc.player.swing(InteractionHand.MAIN_HAND);
        } else {
            mc.player.resetAttackStrengthTicker();
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            barrageWasDown = false;
            return;
        }
        if (!controller.isCurrentMandom()) {
            barrageWasDown = false;
            return;
        }

        if (!barrageKeyScanned && mc.options != null) {
            for (KeyMapping km : mc.options.keyMappings) {
                if (km.getName().equalsIgnoreCase("key.jcraft.barrage")) {
                    cachedBarrageKey = km;
                    break;
                }
            }
            barrageKeyScanned = true;
        }

        if (cachedBarrageKey != null) {
            boolean down = cachedBarrageKey.isDown();
            if (down && !barrageWasDown) {
                // Pressing Barrage while Mandom is active triggers Mandom's Rewind (ULTIMATE)
                CombinedNetwork.sendWheelCast(WheelSlot.ULTIMATE.index, false, false, false);
            }
            barrageWasDown = down;
        }
    }
}
