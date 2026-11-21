package com.custom.nodashbhop.wheel;

import com.custom.nodashbhop.JCraftStandCheck;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Core controller for JCraft Ability Wheel v3.2:
 * - Default Cast key: C (now supports HOLDING C for hold-type skills!)
 * - Tap X (< 175ms): Quick-cycle active non-Light abilities
 * - Hold X (>= 175ms): Open transparent PvP Wheel while preserving crouch (Shift never forces player to stand up!)
 * - Mandom special support: 2 halves (Countdown + Rewind mapped to Barrage), Rewind icon in center, normal M1 attack untouched
 * - Default to STAND whenever player has a Stand; switching to SPEC auto-desummons Stand before firing Spec move if needed
 * - Casting Stand move with C when Stand is unsummoned fires immediately without waiting for manual summon
 * - Spec wheel filters out Utility and missing/unusable moves so playable Spec skills fill the whole wheel
 */
public class WheelController {
    public static final KeyMapping KEY_CAST = new KeyMapping(
            "key.jcraftwheel.cast",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            "key.categories.jcraftwheel"
    );

    public static final KeyMapping KEY_WHEEL = new KeyMapping(
            "key.jcraftwheel.wheel",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            "key.categories.jcraftwheel"
    );

    private static final long HOLD_THRESHOLD_MS = 175L;

    public final MoveSlotData[] slots = new MoveSlotData[8];
    public final JCraftCooldownTracker cooldowns = new JCraftCooldownTracker();
    public final JCraftIconResolver iconResolver = new JCraftIconResolver();
    public final JCraftAboutParser aboutParser;

    private WheelSlot selectedWheelSlot = WheelSlot.HEAVY;
    private boolean specMode = false;
    private boolean hadStandPreviously = false;

    private String lastActiveEntityKey = "";
    private boolean hadSummonedEntity = false;
    private int refreshCooldownTicks = 0;

    // X tap vs hold tracking
    private boolean xWasDown = false;
    private long xPressStartMs = 0L;
    private boolean wheelOpenedThisHold = false;

    // C holdable cast tracking
    private boolean castWasDown = false;
    private boolean holdingCastKey = false;

    // Active JCraft simulated keybind hold
    private KeyMapping activeCastKey = null;
    private boolean activeCastForceShift = false;
    private int activeCastMinTicksRemaining = 0;

    // 1-tick handoff after instant summon/desummon
    private WheelSlot pendingMoveSlot = null;
    private boolean pendingMoveShift = false;
    private boolean pendingMoveHoldWhileC = false;
    private int pendingDelayTicks = 0;

    public WheelController() {
        for (WheelSlot ws : WheelSlot.values()) {
            slots[ws.index] = new MoveSlotData(ws);
        }
        this.aboutParser = new JCraftAboutParser(slots);
    }

    public boolean isCurrentMandom() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || specMode) return false;
        return StandThemeRegistry.isMandom(JCraftStandCheck.getStandId(mc.player));
    }

    /**
     * Returns ONLY the active non-Light slots for the wheel:
     * - Mandom: strictly 2 halves (Countdown on SPECIAL1 + Rewind on BARRAGE)
     * - Spec: strictly excludes UTILITY and any slot without a real move/icon
     * - Stand: excludes LIGHT (M1) and any missing slots so remaining moves expand across 360 deg
     */
    public List<MoveSlotData> getActiveSlots() {
        List<MoveSlotData> active = new ArrayList<>();

        if (isCurrentMandom()) {
            MoveSlotData countdown = slots[WheelSlot.SPECIAL1.index];
            MoveSlotData rewind = slots[WheelSlot.BARRAGE.index];
            if (countdown.normalName == null || countdown.normalName.equals(WheelSlot.SPECIAL1.defaultName)) {
                countdown.normalName = "Countdown";
            }
            if (rewind.normalName == null || rewind.normalName.equals(WheelSlot.BARRAGE.defaultName)) {
                rewind.normalName = "Rewind";
            }
            // Pull icon from ULTIMATE if Mandom's Rewind icon was indexed under ULT
            if (rewind.normalIcon == null && slots[WheelSlot.ULTIMATE.index].normalIcon != null) {
                rewind.normalIcon = slots[WheelSlot.ULTIMATE.index].normalIcon;
            }
            active.add(countdown);
            active.add(rewind);
            return active;
        }

        if (specMode) {
            // Specs do not have Utility; filter out slots with no icon/move so playable moves fill the wheel
            for (int i = 1; i < 7; i++) { // HEAVY..ULTIMATE (exclude LIGHT=0 and UTILITY=7)
                MoveSlotData d = slots[i];
                if (d.present && (d.normalIcon != null || !d.normalDescription.isEmpty())) {
                    active.add(d);
                }
            }
            if (active.isEmpty()) {
                for (int i = 1; i < 6; i++) { // Fallback: HEAVY, BARRAGE, SP1, SP2, SP3
                    if (slots[i].present || slots[i].normalIcon != null) {
                        active.add(slots[i]);
                    }
                }
            }
            if (active.isEmpty()) {
                active.add(slots[WheelSlot.HEAVY.index]);
                active.add(slots[WheelSlot.BARRAGE.index]);
                active.add(slots[WheelSlot.SPECIAL1.index]);
            }
            return active;
        }

        // Normal Stands: indices 1..7
        for (int i = 1; i < 8; i++) {
            if (slots[i].present) {
                active.add(slots[i]);
            }
        }
        if (active.isEmpty()) {
            for (int i = 1; i < 8; i++) {
                active.add(slots[i]);
            }
        }
        return active;
    }

    /**
     * Center hub icon:
     * - For Mandom: uses Rewind icon (BARRAGE / ULTIMATE)
     * - For all other Stands/Specs: uses Light Punch (M1) icon
     */
    public MoveSlotData getCenterHubSlotData() {
        if (isCurrentMandom()) {
            MoveSlotData rewind = slots[WheelSlot.BARRAGE.index];
            if (rewind.normalIcon != null) return rewind;
            if (slots[WheelSlot.ULTIMATE.index].normalIcon != null) return slots[WheelSlot.ULTIMATE.index];
            return rewind;
        }
        return slots[WheelSlot.LIGHT.index];
    }

    public WheelSlot getSelectedWheelSlot() {
        List<MoveSlotData> active = getActiveSlots();
        for (MoveSlotData d : active) {
            if (d.slot == selectedWheelSlot) {
                return selectedWheelSlot;
            }
        }
        selectedWheelSlot = active.get(0).slot;
        return selectedWheelSlot;
    }

    public void setSelectedWheelSlot(WheelSlot slot) {
        if (slot != null && slot != WheelSlot.LIGHT) {
            this.selectedWheelSlot = slot;
        }
    }

    public boolean isSpecMode() {
        return specMode;
    }

    public boolean canSwitchMode(Player player) {
        if (player == null) return false;
        return JCraftStandCheck.hasStand(player) && JCraftStandCheck.hasSpec(player);
    }

    public void toggleMode() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !canSwitchMode(mc.player)) return;
        specMode = !specMode;
        mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.45F, specMode ? 1.25F : 0.95F);
        forceRefresh(mc, true);
    }

    public static boolean isShiftHeldRaw() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return false;
        long win = mc.getWindow().getWindow();
        return InputConstants.isKeyDown(win, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(win, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    public static boolean isWheelKeyHeldRaw() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return false;
        InputConstants.Key key = KEY_WHEEL.getKey();
        if (key.getType() == InputConstants.Type.KEYSYM && key.getValue() > 0) {
            return InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue());
        }
        return KEY_WHEEL.isDown();
    }

    public static boolean isCastKeyHeldRaw() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return false;
        InputConstants.Key key = KEY_CAST.getKey();
        if (key.getType() == InputConstants.Type.KEYSYM && key.getValue() > 0) {
            return InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue());
        }
        return KEY_CAST.isDown();
    }

    public String getCastKeyShortLabel() {
        if (KEY_CAST.isUnbound()) return "C";
        String s = KEY_CAST.getTranslatedKeyMessage().getString().trim().toUpperCase(Locale.ROOT);
        return s.length() > 3 ? s.substring(0, 3) : s;
    }

    public String getBoundKeyLabel(WheelSlot slot, boolean isSpec) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return "";
        KeyMapping km = findJCraftKeyMapping(mc, slot, isSpec);
        if (km == null || km.isUnbound()) return "";
        String label = km.getTranslatedKeyMessage().getString().trim();
        if (label.isEmpty() || label.equalsIgnoreCase("key.keyboard.unknown") || label.equalsIgnoreCase("none")) {
            return "";
        }
        return label.toUpperCase(Locale.ROOT);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            cooldowns.resetAll();
            lastActiveEntityKey = "";
            hadSummonedEntity = false;
            hadStandPreviously = false;
            pendingMoveSlot = null;
            releaseActiveCast(mc);
            return;
        }

        // Preserve player crouch whenever Shift is physically held while Wheel is open!
        boolean wheelScreenOpen = mc.screen instanceof WheelRadialScreen;
        if (wheelScreenOpen && mc.options != null) {
            mc.options.keyShift.setDown(isShiftHeldRaw());
        }

        tickSimulatedCast(mc);
        tickPendingMove(mc);

        boolean hasStand = JCraftStandCheck.hasStand(mc.player);
        boolean hasSpec = JCraftStandCheck.hasSpec(mc.player);

        // Always default to STAND when player acquires a Stand (or has Stand and no explicit Spec switch)
        if (hasStand && !hadStandPreviously) {
            specMode = false;
        } else if (!specMode && !hasStand && hasSpec) {
            specMode = true;
        } else if (specMode && !hasSpec && hasStand) {
            specMode = false;
        }
        hadStandPreviously = hasStand;

        String activeId = specMode
                ? "SPEC:" + JCraftStandCheck.getSpecFullId(mc.player)
                : "STAND:" + JCraftStandCheck.getStandFullId(mc.player);

        boolean summonedNow = specMode
                ? (JCraftStandCheck.getSpecInstance(mc.player) != null)
                : (JCraftStandCheck.getStandEntity(mc.player) != null);

        if (!activeId.equals(lastActiveEntityKey)) {
            lastActiveEntityKey = activeId;
            hadSummonedEntity = summonedNow;
            if (hasStand || hasSpec) {
                forceRefresh(mc, true);
            }
        } else if (summonedNow && !hadSummonedEntity) {
            hadSummonedEntity = true;
            iconResolver.populateSlots(mc.player, specMode, slots, false);
        } else {
            hadSummonedEntity = summonedNow;
            if (refreshCooldownTicks > 0) refreshCooldownTicks--;
        }

        cooldowns.tick(mc.player, specMode);

        if (mc.screen != null && !wheelScreenOpen) {
            xWasDown = false;
            castWasDown = false;
            holdingCastKey = false;
            return;
        }

        if (!hasStand && !hasSpec) {
            return;
        }

        // 1. Handle X key (Tap = quick cycle, Hold = open Wheel without breaking Shift crouch!)
        boolean xDown = isWheelKeyHeldRaw();
        long now = System.currentTimeMillis();

        if (xDown && !xWasDown) {
            xPressStartMs = now;
            wheelOpenedThisHold = false;
        } else if (xDown && xWasDown) {
            if (!wheelOpenedThisHold && (now - xPressStartMs) >= HOLD_THRESHOLD_MS) {
                wheelOpenedThisHold = true;
                if (!(mc.screen instanceof WheelRadialScreen)) {
                    if (refreshCooldownTicks == 0) {
                        iconResolver.populateSlots(mc.player, specMode, slots, false);
                        refreshCooldownTicks = 40;
                    }
                    boolean shiftBeforeOpen = isShiftHeldRaw();
                    mc.setScreen(new WheelRadialScreen(this));
                    // Immediately restore Shift state after setScreen() calls KeyMapping.releaseAll()!
                    if (shiftBeforeOpen && mc.options != null) {
                        mc.options.keyShift.setDown(true);
                    }
                }
            }
        } else if (!xDown && xWasDown) {
            if (!wheelOpenedThisHold && (now - xPressStartMs) < HOLD_THRESHOLD_MS) {
                cycleNextActiveSlot(mc);
            }
            wheelOpenedThisHold = false;
        }
        xWasDown = xDown;

        // 2. Handle C key (HOLDABLE cast for hold-type moves!)
        boolean castDown = isCastKeyHeldRaw();
        if (castDown && !castWasDown && !wheelScreenOpen) {
            holdingCastKey = true;
            boolean shift = isShiftHeldRaw();
            castSlot(getSelectedWheelSlot(), shift, true);
        } else if (!castDown && castWasDown) {
            holdingCastKey = false;
            // Release held move key immediately when C is released (if min hold elapsed)
            if (activeCastMinTicksRemaining <= 0) {
                releaseActiveCast(mc);
            }
        }
        castWasDown = castDown;
    }

    private void cycleNextActiveSlot(Minecraft mc) {
        List<MoveSlotData> active = getActiveSlots();
        if (active.isEmpty()) return;
        WheelSlot cur = getSelectedWheelSlot();
        int curIdx = 0;
        for (int i = 0; i < active.size(); i++) {
            if (active.get(i).slot == cur) {
                curIdx = i;
                break;
            }
        }
        selectedWheelSlot = active.get((curIdx + 1) % active.size()).slot;
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.35F, 1.35F);
        }
    }

    public void forceRefresh(Minecraft mc, boolean requestAbout) {
        if (mc.player == null) return;
        for (WheelSlot ws : WheelSlot.values()) {
            slots[ws.index].normalDescription = "";
            slots[ws.index].crouchDescription = "";
        }
        iconResolver.populateSlots(mc.player, specMode, slots, true);
        if (requestAbout) {
            aboutParser.requestSilentSync(specMode);
        }
    }

    public void castSlot(WheelSlot slot, boolean shiftRequested) {
        castSlot(slot, shiftRequested, false);
    }

    public void castSlot(WheelSlot slot, boolean shiftRequested, boolean holdWhileC) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options == null || slot == null || slot == WheelSlot.LIGHT) return;

        this.selectedWheelSlot = slot;

        // Case A: In SPEC mode while a non-Mandom Stand is summoned -> desummon Stand first so Spec move executes cleanly without conflict!
        if (specMode && JCraftStandCheck.hasStand(mc.player)
                && JCraftStandCheck.getStandEntity(mc.player) != null
                && !StandThemeRegistry.isMandom(JCraftStandCheck.getStandId(mc.player))) {
            KeyMapping summonKey = findStandSummonKey(mc);
            if (summonKey != null) {
                tapKeyOnce(summonKey);
                pendingMoveSlot = slot;
                pendingMoveShift = shiftRequested;
                pendingMoveHoldWhileC = holdWhileC;
                pendingDelayTicks = 1;
                return;
            }
        }

        // Case B: In STAND mode while Stand is unsummoned -> summon + fire seamlessly with minimal 1-tick handoff!
        if (!specMode && JCraftStandCheck.hasStand(mc.player) && JCraftStandCheck.getStandEntity(mc.player) == null) {
            KeyMapping summonKey = findStandSummonKey(mc);
            if (summonKey != null) {
                tapKeyOnce(summonKey);
                // Also fire immediately on next tick so player doesn't wait for a separate manual summon
                pendingMoveSlot = slot;
                pendingMoveShift = shiftRequested;
                pendingMoveHoldWhileC = holdWhileC;
                pendingDelayTicks = 1;
                return;
            }
        }

        executeCastNow(mc, slot, shiftRequested, holdWhileC);
    }

    private void tapKeyOnce(KeyMapping km) {
        km.setDown(true);
        KeyMapping.click(km.getKey());
        km.setDown(false);
    }

    private void executeCastNow(Minecraft mc, WheelSlot slot, boolean shiftRequested, boolean holdWhileC) {
        MoveSlotData data = slots[slot.index];
        boolean useShift = shiftRequested && data.hasCrouchVariant;

        KeyMapping jcraftKey = findJCraftKeyMapping(mc, slot, specMode);
        if (jcraftKey != null) {
            if (useShift && mc.options != null) {
                mc.options.keyShift.setDown(true);
            }
            jcraftKey.setDown(true);
            KeyMapping.click(jcraftKey.getKey());
            activeCastKey = jcraftKey;
            activeCastForceShift = useShift;
            activeCastMinTicksRemaining = 2;
            holdingCastKey = holdWhileC && isCastKeyHeldRaw();
        }
    }

    private void tickSimulatedCast(Minecraft mc) {
        if (activeCastKey == null) return;
        if (activeCastMinTicksRemaining > 0) {
            activeCastMinTicksRemaining--;
        }
        // Keep holding activeCastKey as long as C is physically held down!
        if (holdingCastKey && isCastKeyHeldRaw()) {
            activeCastKey.setDown(true);
            if (activeCastForceShift && mc.options != null) {
                mc.options.keyShift.setDown(true);
            }
            return;
        }
        if (activeCastMinTicksRemaining <= 0) {
            releaseActiveCast(mc);
        }
    }

    private void releaseActiveCast(Minecraft mc) {
        if (activeCastKey != null) {
            activeCastKey.setDown(false);
            activeCastKey = null;
        }
        if (activeCastForceShift && mc != null && mc.options != null) {
            mc.options.keyShift.setDown(isShiftHeldRaw());
            activeCastForceShift = false;
        }
        holdingCastKey = false;
    }

    private void tickPendingMove(Minecraft mc) {
        if (pendingMoveSlot == null) return;
        if (pendingDelayTicks > 0) {
            pendingDelayTicks--;
            return;
        }
        WheelSlot toCast = pendingMoveSlot;
        boolean shift = pendingMoveShift;
        boolean holdC = pendingMoveHoldWhileC;
        pendingMoveSlot = null;
        pendingMoveShift = false;
        pendingMoveHoldWhileC = false;
        executeCastNow(mc, toCast, shift, holdC);
    }

    private KeyMapping findStandSummonKey(Minecraft mc) {
        for (KeyMapping km : mc.options.keyMappings) {
            String raw = km.getName().toLowerCase(Locale.ROOT);
            String cat = km.getCategory().toLowerCase(Locale.ROOT);
            if (!raw.contains("jcraft") && !cat.contains("jcraft")) continue;
            if (raw.contains("summon") || raw.contains("stand_summon") || raw.contains("toggle_stand")) {
                return km;
            }
        }
        return null;
    }

    private KeyMapping findJCraftKeyMapping(Minecraft mc, WheelSlot slot, boolean isSpec) {
        KeyMapping strictMatch = null;
        KeyMapping neutralFallback = null;
        for (KeyMapping km : mc.options.keyMappings) {
            String raw = km.getName().toLowerCase(Locale.ROOT);
            String cat = km.getCategory().toLowerCase(Locale.ROOT);
            if (!raw.contains("jcraft") && !cat.contains("jcraft")) continue;

            boolean matchesSlot = false;
            for (String tok : slot.keybindTokens) {
                if (raw.contains(tok)) {
                    matchesSlot = true;
                    break;
                }
            }
            if (!matchesSlot) continue;

            boolean keyIsSpec = raw.contains("spec");
            boolean keyIsStand = raw.contains("stand");

            if (isSpec) {
                if (keyIsSpec) return km;
                if (!keyIsStand && neutralFallback == null) neutralFallback = km;
            } else {
                if (!keyIsSpec) {
                    if (keyIsStand) return km;
                    if (strictMatch == null) strictMatch = km;
                }
            }
        }
        return isSpec ? neutralFallback : strictMatch;
    }

    @SubscribeEvent
    public void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("jwheel")
                        .then(Commands.literal("reload")
                                .executes(ctx -> {
                                    Minecraft mc = Minecraft.getInstance();
                                    iconResolver.invalidateCache();
                                    forceRefresh(mc, true);
                                    if (mc.player != null) {
                                        mc.player.displayClientMessage(
                                                Component.literal("§b[JCraft Wheel] §aĐã đồng bộ lại icon & mô tả từ /stand about!"),
                                                false
                                        );
                                    }
                                    return 1;
                                })
                        )
        );
    }
}
