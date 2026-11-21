package com.custom.nodashbhop.wheel;

import com.custom.nodashbhop.CS2BhopConfig;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;

/**
 * Server-synced Third-Person Camera Controller (/forcecam replace | lock | off):
 * - Mode REPLACE (default): Replaces vanilla THIRD_PERSON_BACK with Shoulder Surfing Reloaded
 *   over-the-shoulder aiming view (decoupled_camera = false, crosshair ALWAYS visible & dynamic
 *   just like holding a TaCZ gun). Players can freely press F5 to switch between 1st-person and
 *   this over-the-shoulder 3rd-person view.
 * - Mode LOCK (/forcecam lock or /forcecam on): Also locks F5 and keeps players in Shoulder Surfing
 *   at all times (still compatible with TaCZ 1.2.0-hotfix ADS / sniper scopes).
 * - Mode OFF (/forcecam off): Disables all camera overrides.
 */
public class ForceThirdPersonHandler {
    private int configCheckTicker = 0;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onKeyInput(InputEvent.Key event) {
        // Only lock F5 / Shoulder Surfing toggle keys in LOCK mode
        if (CS2BhopConfig.forceCamMode != CS2BhopConfig.CAM_LOCK) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options == null || mc.screen != null) return;

        KeyMapping togglePersp = mc.options.keyTogglePerspective;
        if (togglePersp != null && togglePersp.getKey().getValue() == event.getKey()) {
            togglePersp.setDown(false);
            while (togglePersp.consumeClick()) {}
        }

        for (KeyMapping km : mc.options.keyMappings) {
            String name = km.getName().toLowerCase(Locale.ROOT);
            if ((name.contains("shouldersurfing") || name.contains("shoulder_surfing"))
                    && (name.contains("toggle") || name.contains("perspective") || name.contains("decoupled"))) {
                if (km.getKey().getValue() == event.getKey()) {
                    km.setDown(false);
                    while (km.consumeClick()) {}
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        int mode = CS2BhopConfig.forceCamMode;
        if (mode == CS2BhopConfig.CAM_OFF) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options == null || player.isSpectator()) return;

        // Periodically apply Shoulder Surfing Reloaded 4.x / 5.x settings:
        // - replace_default_perspective = true (removes vanilla 3rd-person back)
        // - decoupled_camera = false (character rotates with crosshair like gun aiming)
        // - crosshair_type = DYNAMIC + shoulder_surfing visibility = ALWAYS
        if (configCheckTicker <= 0) {
            enforceShoulderSurfingGunStyleConfig();
            configCheckTicker = 20;
        } else {
            configCheckTicker--;
        }

        // Never interfere while aiming down sights (ADS / Scope) with TaCZ 1.2.0-hotfix guns
        if (isTaCZAiming(player)) {
            return;
        }

        if (mode == CS2BhopConfig.CAM_LOCK) {
            // Consume F5 clicks while locked
            KeyMapping togglePersp = mc.options.keyTogglePerspective;
            if (togglePersp != null) {
                togglePersp.setDown(false);
                while (togglePersp.consumeClick()) {}
            }

            if (!enforceShoulderSurfingPerspective()) {
                if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
                    mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                }
            }
        } else if (mode == CS2BhopConfig.CAM_REPLACE) {
            // Allow F5 freely between 1st-person & Shoulder Surfing,
            // but intercept vanilla THIRD_PERSON_BACK whenever Shoulder Surfing isn't active.
            if (mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK && !isCurrentlyShoulderSurfing()) {
                enforceShoulderSurfingPerspective();
            }
        }
    }

    /**
     * Checks if the local player is currently aiming down sights (ADS) with a TaCZ 1.2.0 gun.
     */
    private boolean isTaCZAiming(LocalPlayer player) {
        try {
            Class<?> clientOpCls = Class.forName("com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator");
            Method fromLocal = clientOpCls.getMethod("fromLocalPlayer", LocalPlayer.class);
            Object op = fromLocal.invoke(null, player);
            if (op != null) {
                try {
                    Method isAim = op.getClass().getMethod("isAim");
                    if (Boolean.TRUE.equals(isAim.invoke(op))) return true;
                } catch (Exception ignored) {
                }
                try {
                    Method prog = op.getClass().getMethod("getClientAimingProgress", float.class);
                    Object val = prog.invoke(op, 1.0F);
                    if (val instanceof Number n && n.floatValue() > 0.05F) return true;
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }

        try {
            Class<?> gunOpCls = Class.forName("com.tacz.guns.api.entity.IGunOperator");
            Method fromLiving = gunOpCls.getMethod("fromLivingEntity", LivingEntity.class);
            Object op = fromLiving.invoke(null, player);
            if (op != null) {
                try {
                    Method synAim = op.getClass().getMethod("getSynIsAiming");
                    if (Boolean.TRUE.equals(synAim.invoke(op))) return true;
                } catch (Exception ignored) {
                }
                try {
                    Method synProg = op.getClass().getMethod("getSynAimingProgress");
                    Object val = synProg.invoke(op);
                    if (val instanceof Number n && n.floatValue() > 0.05F) return true;
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private Object getShoulderSurfingInstance() {
        String[] ssClasses = {
                "com.github.exopandora.shouldersurfing.api.client.ShoulderSurfing",
                "com.github.exopandora.shouldersurfing.client.ShoulderSurfingImpl"
        };
        for (String clsName : ssClasses) {
            try {
                Class<?> ssCls = Class.forName(clsName);
                Method getInst = ssCls.getMethod("getInstance");
                Object inst = getInst.invoke(null);
                if (inst != null) return inst;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private boolean isCurrentlyShoulderSurfing() {
        Object inst = getShoulderSurfingInstance();
        if (inst == null) return false;
        try {
            Method isSS = inst.getClass().getMethod("isShoulderSurfing");
            return Boolean.TRUE.equals(isSS.invoke(inst));
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Switches to Shoulder Surfing Reloaded 4.x / 5.x SHOULDER_SURFING perspective via reflection.
     */
    private boolean enforceShoulderSurfingPerspective() {
        Object inst = getShoulderSurfingInstance();
        if (inst == null) return false;

        try {
            boolean alreadySS = false;
            try {
                Method isSS = inst.getClass().getMethod("isShoulderSurfing");
                alreadySS = Boolean.TRUE.equals(isSS.invoke(inst));
            } catch (Exception ignored) {
            }

            if (!alreadySS) {
                for (Method m : inst.getClass().getMethods()) {
                    if (m.getName().equals("changePerspective") && m.getParameterCount() == 1 && m.getParameterTypes()[0].isEnum()) {
                        Object[] constants = m.getParameterTypes()[0].getEnumConstants();
                        if (constants != null) {
                            for (Object c : constants) {
                                if (c.toString().equalsIgnoreCase("SHOULDER_SURFING")) {
                                    m.invoke(inst, c);
                                    return true;
                                }
                            }
                        }
                    }
                }
                try {
                    Method toggle = inst.getClass().getMethod("toggleShoulderSurfing");
                    toggle.invoke(inst);
                    return true;
                } catch (Exception ignored) {
                }
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Configures Shoulder Surfing Reloaded 4.x / 5.x live in memory so 3rd-person feels like
     * holding a gun with crosshair at all times:
     * - replace_default_perspective = true (replaces vanilla 3rd-person back)
     * - decoupled_camera = false (player turns with crosshair)
     * - crosshair_type = DYNAMIC (aim-tracked crosshair like gun TPS)
     * - crosshair visibility for SHOULDER_SURFING = ALWAYS
     */
    private void enforceShoulderSurfingGunStyleConfig() {
        String[] configClasses = {
                "com.github.exopandora.shouldersurfing.config.Config",
                "com.github.exopandora.shouldersurfing.client.config.ClientConfig"
        };
        for (String clsName : configClasses) {
            try {
                Class<?> cfgCls = Class.forName(clsName);
                Object clientCfg = null;
                try {
                    Field clientField = cfgCls.getField("CLIENT");
                    clientCfg = clientField.get(null);
                } catch (Exception ignored) {
                }
                if (clientCfg == null) continue;

                applyConfigOnObject(clientCfg, 0);
            } catch (Exception ignored) {
            }
        }
    }

    private void applyConfigOnObject(Object obj, int depth) {
        if (obj == null || depth > 2) return;
        for (Field f : obj.getClass().getDeclaredFields()) {
            String fn = f.getName().toLowerCase(Locale.ROOT);
            try {
                f.setAccessible(true);
                Object valObj = f.get(obj);
                if (valObj == null) continue;

                // 1. decoupled_camera = false
                if (fn.contains("decoupled")) {
                    setBooleanConfigValue(valObj, false);
                }
                // 2. replace_default_perspective = true
                else if (fn.contains("replacedefaultperspective") || fn.contains("replace_default_perspective")) {
                    setBooleanConfigValue(valObj, true);
                }
                // 3. crosshair_type = DYNAMIC
                else if (fn.equals("crosshairtype") || fn.equals("crosshair_type")) {
                    setEnumConfigValue(valObj, "DYNAMIC");
                }
                // 4. crosshairVisibility map (Perspective -> ConfigValue<CrosshairVisibility>)
                else if (fn.contains("crosshairvisibility") || fn.contains("crosshair_visibility")) {
                    if (valObj instanceof Map<?, ?> map) {
                        for (Map.Entry<?, ?> entry : map.entrySet()) {
                            if (entry.getKey() != null && entry.getKey().toString().equalsIgnoreCase("SHOULDER_SURFING")) {
                                setEnumConfigValue(entry.getValue(), "ALWAYS");
                            }
                        }
                    } else {
                        applyConfigOnObject(valObj, depth + 1);
                    }
                }
                // 5. direct shoulderSurfing visibility field inside a sub-config
                else if (depth > 0 && (fn.contains("shouldersurfing") || fn.contains("shoulder_surfing"))) {
                    setEnumConfigValue(valObj, "ALWAYS");
                }
                // 6. Recurse into nested Shoulder Surfing config holder classes
                else if (valObj.getClass().getName().contains("shouldersurfing") && !valObj.getClass().isEnum()) {
                    applyConfigOnObject(valObj, depth + 1);
                }
            } catch (Exception ignored) {
            }
        }

        // Also check direct setter methods on ClientConfig
        for (Method m : obj.getClass().getMethods()) {
            String mn = m.getName().toLowerCase(Locale.ROOT);
            try {
                if (mn.contains("decoupled") && mn.startsWith("set") && m.getParameterCount() == 1) {
                    Class<?> pt = m.getParameterTypes()[0];
                    if (pt == boolean.class || pt == Boolean.class) {
                        m.invoke(obj, false);
                    }
                } else if (mn.contains("replacedefaultperspective") && mn.startsWith("set") && m.getParameterCount() == 1) {
                    Class<?> pt = m.getParameterTypes()[0];
                    if (pt == boolean.class || pt == Boolean.class) {
                        m.invoke(obj, true);
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void setBooleanConfigValue(Object configVal, boolean target) {
        try {
            Method getM = configVal.getClass().getMethod("get");
            Object cur = getM.invoke(configVal);
            if (cur instanceof Boolean b && b != target) {
                Method setM = configVal.getClass().getMethod("set", Object.class);
                setM.invoke(configVal, Boolean.valueOf(target));
            }
        } catch (Exception ignored) {
        }
    }

    private void setEnumConfigValue(Object configVal, String targetEnumName) {
        if (configVal == null) return;
        try {
            Method getM = configVal.getClass().getMethod("get");
            Object cur = getM.invoke(configVal);
            if (cur != null && cur.getClass().isEnum()) {
                if (!cur.toString().equalsIgnoreCase(targetEnumName)) {
                    Object[] constants = cur.getClass().getEnumConstants();
                    if (constants != null) {
                        for (Object c : constants) {
                            if (c.toString().equalsIgnoreCase(targetEnumName)) {
                                Method setM = configVal.getClass().getMethod("set", Object.class);
                                setM.invoke(configVal, c);
                                return;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }
}
