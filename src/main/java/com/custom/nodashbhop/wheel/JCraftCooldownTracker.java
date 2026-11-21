package com.custom.nodashbhop.wheel;

import com.custom.nodashbhop.JCraftStandCheck;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Tracks live JCraft ability cooldowns for all wheel slots (Stand & Spec modes).
 * Automatically reflects JCraft Cooldown Cancel (RA) resets immediately onto the Wheel!
 */
public class JCraftCooldownTracker {
    private final int[] remainingTicks = new int[8];
    private final int[] maxTicks = new int[8];
    private final int[] localFallbackTicks = new int[8];
    private final int[] localFallbackMax = new int[8];

    private Class<?> cachedEnumCls = null;
    private boolean enumChecked = false;
    private KeyMapping cooldownCancelKey = null;
    private boolean cancelKeyScanned = false;
    private boolean cancelWasDown = false;

    public void tick(Player player, boolean specMode) {
        if (player == null) {
            resetAll();
            return;
        }

        // Detect JCraft Cooldown Cancel (RA) keypress to instantly clear wheel cooldown overlays
        Minecraft mc = Minecraft.getInstance();
        if (!cancelKeyScanned && mc.options != null) {
            cooldownCancelKey = findCooldownCancelKey(mc);
            cancelKeyScanned = true;
        }
        if (cooldownCancelKey != null) {
            boolean down = cooldownCancelKey.isDown();
            if (down && !cancelWasDown) {
                clearNonUniversalFallback();
            }
            cancelWasDown = down;
        }

        if (!enumChecked) {
            cachedEnumCls = JCraftStandCheck.findCooldownTypeEnum();
            enumChecked = true;
        }

        Object cdComp = JCraftStandCheck.getCooldownComponent(player);
        Object[] enumConstants = (cachedEnumCls != null && cachedEnumCls.isEnum())
                ? cachedEnumCls.getEnumConstants()
                : null;

        for (WheelSlot slot : WheelSlot.values()) {
            int i = slot.index;
            if (localFallbackTicks[i] > 0) {
                localFallbackTicks[i]--;
            }

            int liveRem = 0;
            int liveMax = 0;
            boolean hasLiveSource = false;

            if (cdComp != null && enumConstants != null) {
                Object matched = matchCooldownEnum(enumConstants, slot, specMode);
                if (matched != null) {
                    hasLiveSource = true;
                    liveRem = invokeIntGetter(cdComp, cachedEnumCls, matched,
                            "getCooldown", "get", "getRemaining");
                    liveMax = invokeIntGetter(cdComp, cachedEnumCls, matched,
                            "getInitialDuration", "getMaxCooldown", "getDuration");
                }
            }

            if (hasLiveSource) {
                if (liveRem > 0) {
                    remainingTicks[i] = liveRem;
                    if (liveMax >= liveRem) {
                        maxTicks[i] = liveMax;
                    } else if (liveRem > maxTicks[i]) {
                        maxTicks[i] = liveRem;
                    }
                    if (maxTicks[i] <= 0) {
                        maxTicks[i] = liveRem;
                    }
                    localFallbackTicks[i] = 0;
                } else {
                    // Live component reports 0 (including right after Cooldown Cancel / RA!) -> clear immediately!
                    remainingTicks[i] = 0;
                    maxTicks[i] = 0;
                    localFallbackTicks[i] = 0;
                    localFallbackMax[i] = 0;
                }
            } else if (localFallbackTicks[i] > 0) {
                remainingTicks[i] = localFallbackTicks[i];
                maxTicks[i] = Math.max(1, localFallbackMax[i]);
            } else {
                remainingTicks[i] = 0;
                maxTicks[i] = 0;
            }
        }
    }

    private KeyMapping findCooldownCancelKey(Minecraft mc) {
        for (KeyMapping km : mc.options.keyMappings) {
            String raw = km.getName().toLowerCase(Locale.ROOT).replace("_", "").replace(".", "");
            if (raw.contains("jcraft") && (raw.contains("cooldowncancel") || raw.contains("cdcancel"))) {
                return km;
            }
        }
        return null;
    }

    public void clearNonUniversalFallback() {
        for (int i = 0; i < 8; i++) {
            localFallbackTicks[i] = 0;
            localFallbackMax[i] = 0;
        }
    }

    public int getRemainingTicks(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= 8) return 0;
        return remainingTicks[slotIndex];
    }

    public float getRemainingSeconds(int slotIndex) {
        return getRemainingTicks(slotIndex) / 20.0F;
    }

    public float getCooldownRatio(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= 8) return 0.0F;
        int rem = remainingTicks[slotIndex];
        int max = maxTicks[slotIndex];
        if (rem <= 0 || max <= 0) return 0.0F;
        return Math.min(1.0F, Math.max(0.0F, (float) rem / (float) max));
    }

    public String formatCooldown(int slotIndex) {
        float sec = getRemainingSeconds(slotIndex);
        if (sec <= 0.05F) return "";
        if (sec >= 10.0F) {
            return String.format(Locale.ROOT, "%.0fs", sec);
        }
        return String.format(Locale.ROOT, "%.1fs", sec);
    }

    public void resetAll() {
        for (int i = 0; i < 8; i++) {
            remainingTicks[i] = 0;
            maxTicks[i] = 0;
            localFallbackTicks[i] = 0;
            localFallbackMax[i] = 0;
        }
    }

    private Object matchCooldownEnum(Object[] constants, WheelSlot slot, boolean specMode) {
        String prefix = specMode ? "SPEC_" : "STAND_";
        for (String tok : slot.cooldownTokens) {
            String upper = tok.toUpperCase(Locale.ROOT);
            if (!upper.startsWith(prefix)) continue;
            for (Object c : constants) {
                if (c.toString().equalsIgnoreCase(upper)) return c;
            }
        }
        for (String tok : slot.cooldownTokens) {
            String upper = tok.toUpperCase(Locale.ROOT);
            if (specMode && upper.startsWith("STAND_")) continue;
            if (!specMode && upper.startsWith("SPEC_")) continue;
            for (Object c : constants) {
                String cname = c.toString().toUpperCase(Locale.ROOT);
                if (cname.equals(upper) || cname.contains(upper)) {
                    return c;
                }
            }
        }
        return null;
    }

    private int invokeIntGetter(Object target, Class<?> enumCls, Object enumVal, String... names) {
        for (String name : names) {
            try {
                Method m = target.getClass().getMethod(name, enumCls);
                Object res = m.invoke(target, enumVal);
                if (res instanceof Number n) return n.intValue();
            } catch (Exception ignored) {
            }
        }
        return 0;
    }
}
