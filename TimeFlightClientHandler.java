package com.custom.nodashbhop.wheel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Enables smooth, slow 3D free flight whenever the player holds Space
 * during JCraft Time Stop or King Crimson's Ultimate (Time Erase).
 */
public class TimeFlightClientHandler {
    public static volatile boolean canTimeFlight = false;
    public static volatile boolean activelyFlying = false;

    private static final double FLY_SPEED = 0.28;
    private static final double HOVER_RISE_SPEED = 0.16;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null || !canTimeFlight || player.getAbilities().flying || player.isSpectator()) {
            activelyFlying = false;
            return;
        }

        if (mc.screen != null || !mc.options.keyJump.isDown()) {
            activelyFlying = false;
            return;
        }

        activelyFlying = true;

        float forward = 0.0F;
        float strafe = 0.0F;
        if (mc.options.keyUp.isDown()) forward += 1.0F;
        if (mc.options.keyDown.isDown()) forward -= 1.0F;
        if (mc.options.keyLeft.isDown()) strafe += 1.0F;
        if (mc.options.keyRight.isDown()) strafe -= 1.0F;

        Vec3 look = player.getLookAngle().normalize();
        Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
        if (right.lengthSqr() > 1.0E-4) {
            right = right.normalize();
        } else {
            right = Vec3.ZERO;
        }

        Vec3 targetVel;
        if (forward != 0.0F || strafe != 0.0F) {
            // Free 3D slow flight following camera look + strafe
            Vec3 moveDir = look.scale(forward).add(right.scale(-strafe));
            if (moveDir.lengthSqr() > 1.0E-4) {
                moveDir = moveDir.normalize();
            }
            double vy = moveDir.y * FLY_SPEED;
            if (mc.options.keyShift.isDown()) {
                vy -= 0.14;
            }
            targetVel = new Vec3(moveDir.x * FLY_SPEED, vy, moveDir.z * FLY_SPEED);
        } else {
            // Holding Space without directional keys -> slow upward levitation
            double vy = mc.options.keyShift.isDown() ? 0.0 : HOVER_RISE_SPEED;
            Vec3 cur = player.getDeltaMovement();
            targetVel = new Vec3(cur.x * 0.72, vy, cur.z * 0.72);
        }

        // Smooth interpolation for weighty, cinematic slow anime flight
        Vec3 cur = player.getDeltaMovement();
        Vec3 blended = new Vec3(
                cur.x + (targetVel.x - cur.x) * 0.35,
                cur.y + (targetVel.y - cur.y) * 0.40,
                cur.z + (targetVel.z - cur.z) * 0.35
        );

        player.setDeltaMovement(blended);
        player.fallDistance = 0.0F;
    }
}
