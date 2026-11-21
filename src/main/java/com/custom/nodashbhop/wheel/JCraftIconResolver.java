package com.custom.nodashbhop.wheel;

import com.custom.nodashbhop.JCraftStandCheck;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Resolves exact per-Stand / per-Spec move icons, move names, descriptions,
 * genuine Crouch/Shift variants, and which slots actually exist on the Stand/Spec.
 * Also resolves WheelSlot.LIGHT specifically for the center hub circle icon.
 */
public final class JCraftIconResolver {
    private static final String[] MOVE_CLASS_ENUMS = {
            "net.arna.jcraft.api.attack.enums.MoveClass",
            "net.arna.jcraft.common.attack.core.MoveClass",
            "net.arna.jcraft.common.util.MoveClass"
    };

    private static final Map<String, List<String>> STAND_ALIASES = new HashMap<>();

    static {
        addAliases("star_platinum", "star_platinum", "starplatinum", "sp");
        addAliases("star_platinum_the_world", "star_platinum_the_world", "sptw", "sp_tw");
        addAliases("the_world", "the_world", "theworld", "tw");
        addAliases("the_world_over_heaven", "the_world_over_heaven", "twoh", "tw_oh");
        addAliases("shadow_the_world", "shadow_the_world", "stw", "shadow_tw");
        addAliases("the_world_high_voltage", "the_world_high_voltage", "twhv", "tw_hv");
        addAliases("king_crimson", "king_crimson", "kingcrimson", "kc");
        addAliases("killer_queen", "killer_queen", "killerqueen", "kq");
        addAliases("killer_queen_bites_the_dust", "killer_queen_bites_the_dust", "kqbtd", "kq_btd", "btd");
        addAliases("killer_queen_alternate_universe", "killer_queen_alternate_universe", "kqau", "kq_au");
        addAliases("gold_experience", "gold_experience", "goldexperience", "ge");
        addAliases("gold_experience_requiem", "gold_experience_requiem", "ger");
        addAliases("whitesnake", "whitesnake", "white_snake", "ws");
        addAliases("c_moon", "c_moon", "cmoon");
        addAliases("made_in_heaven", "made_in_heaven", "madeinheaven", "mih");
        addAliases("silver_chariot", "silver_chariot", "silverchariot", "sc");
        addAliases("silver_chariot_requiem", "silver_chariot_requiem", "scr");
        addAliases("magicians_red", "magicians_red", "magiciansred", "mr");
        addAliases("hierophant_green", "hierophant_green", "hierophantgreen", "hg");
        addAliases("the_hand", "the_hand", "thehand", "th");
        addAliases("the_fool", "the_fool", "thefool", "fool");
        addAliases("cream", "cream");
        addAliases("d4c", "d4c", "dirty_deeds_done_dirt_cheap");
        addAliases("purple_haze", "purple_haze", "purplehaze", "ph");
        addAliases("purple_haze_distortion", "purple_haze_distortion", "phd", "ph_distortion");
        addAliases("metallica", "metallica");
        addAliases("horus", "horus");
        addAliases("mandom", "mandom");
        addAliases("aerosmith", "aerosmith", "aero");
        addAliases("crazy_diamond", "crazy_diamond", "crazydiamond", "cd");
        addAliases("anubis", "anubis");
        addAliases("brawler", "brawler");
        addAliases("vampire", "vampire", "vamp");
        addAliases("hamon", "hamon");
        addAliases("spin", "spin");
    }

    private static void addAliases(String canonical, String... aliases) {
        List<String> list = Arrays.asList(aliases);
        for (String a : aliases) {
            STAND_ALIASES.put(a.toLowerCase(Locale.ROOT), list);
        }
        STAND_ALIASES.put(canonical.toLowerCase(Locale.ROOT), list);
    }

    private final List<ResourceLocation> indexedTextures = new ArrayList<>();
    private boolean indexed = false;

    public void invalidateCache() {
        indexed = false;
        indexedTextures.clear();
    }

    private void ensureTexturesIndexed() {
        if (indexed) return;
        indexedTextures.clear();
        try {
            ResourceManager rm = Minecraft.getInstance().getResourceManager();
            String[] roots = {"textures/gui", "textures/moves", "textures/hud", "textures/ability"};
            Set<ResourceLocation> seen = new LinkedHashSet<>();
            for (String root : roots) {
                Map<ResourceLocation, Resource> found = rm.listResources(root, rl -> rl.getPath().endsWith(".png"));
                for (ResourceLocation rl : found.keySet()) {
                    String ns = rl.getNamespace().toLowerCase(Locale.ROOT);
                    String path = rl.getPath().toLowerCase(Locale.ROOT);
                    if (ns.contains("jcraft") || ns.contains("jojo") || path.contains("move") || path.contains("stand") || path.contains("spec")) {
                        seen.add(rl);
                    }
                }
            }
            indexedTextures.addAll(seen);
        } catch (Exception ignored) {
        }
        indexed = true;
    }

    /**
     * Populates all 8 MoveSlotData entries (index 0 = LIGHT used for center hub icon; 1..7 = wheel moves).
     * When attackerObj is null (e.g., Stand temporarily unsummoned), keeps existing MoveMap/about data intact
     * while still resolving per-Stand textures from standId!
     */
    public void populateSlots(Player player, boolean specMode, MoveSlotData[] slots, boolean resetPresence) {
        ensureTexturesIndexed();

        String fullId = specMode ? JCraftStandCheck.getSpecFullId(player) : JCraftStandCheck.getStandFullId(player);
        String shortId = specMode ? JCraftStandCheck.getSpecId(player) : JCraftStandCheck.getStandId(player);
        String namespace = fullId.contains(":") ? fullId.substring(0, fullId.indexOf(':')) : "jcraft";
        String entitySlug = shortId.toLowerCase(Locale.ROOT).replace("_entity", "").replace("entity", "").replaceAll("_+$", "");

        Object attackerObj = specMode
                ? JCraftStandCheck.getSpecInstance(player)
                : JCraftStandCheck.getStandEntity(player);
        Object moveMap = attackerObj != null
                ? JCraftStandCheck.invokeNoArg(attackerObj, "getMoveMap", "getMoves", "moves")
                : null;

        List<String> standTokens = buildStandTokens(entitySlug);

        for (WheelSlot ws : WheelSlot.values()) {
            MoveSlotData data = slots[ws.index];
            if (resetPresence) {
                data.present = false;
                data.normalName = ws.defaultName;
                data.crouchName = "";
                data.hasCrouchVariant = false;
                data.normalIcon = null;
                data.crouchIcon = null;
            }

            // 1. Live reflection from JCraft MoveMap / AbstractMove (when Stand is summoned or Spec is active)
            if (moveMap != null) {
                Object normalMove = findMoveInMap(moveMap, ws, false);
                Object crouchMove = findMoveInMap(moveMap, ws, true);

                if (normalMove != null) {
                    data.present = true;
                    String nName = extractMoveName(normalMove);
                    if (!nName.isEmpty()) data.normalName = nName;
                    String nDesc = extractMoveDescription(normalMove);
                    if (!nDesc.isEmpty() && data.normalDescription.isEmpty()) {
                        data.normalDescription = nDesc;
                    }
                    ResourceLocation directIcon = extractMoveIcon(normalMove);
                    if (directIcon != null) data.normalIcon = directIcon;
                }

                if (crouchMove != null && crouchMove != normalMove) {
                    String cName = extractMoveName(crouchMove);
                    if (!cName.isEmpty() && !cName.equalsIgnoreCase(data.normalName)) {
                        data.present = true;
                        data.hasCrouchVariant = true;
                        data.crouchName = cName;
                        String cDesc = extractMoveDescription(crouchMove);
                        if (!cDesc.isEmpty() && data.crouchDescription.isEmpty()) {
                            data.crouchDescription = cDesc;
                        }
                        ResourceLocation directCrouchIcon = extractMoveIcon(crouchMove);
                        if (directCrouchIcon != null) data.crouchIcon = directCrouchIcon;
                    }
                }
            }

            // 2. Match exact per-Stand texture PNG from JCraft / addon ResourceManager
            if (data.normalIcon == null) {
                ResourceLocation matchedTex = findBestTexture(namespace, standTokens, ws, data.normalName, false);
                if (matchedTex != null) {
                    data.normalIcon = matchedTex;
                    // If this texture explicitly belongs to this specific Stand/Spec folder, mark slot present
                    if (matchesStandToken(matchedTex.getPath().toLowerCase(Locale.ROOT), standTokens)) {
                        data.present = true;
                    }
                }
            }

            if (data.hasCrouchVariant) {
                if (data.crouchIcon == null) {
                    data.crouchIcon = findBestTexture(namespace, standTokens, ws, data.crouchName, true);
                }
                if (data.crouchIcon == null) {
                    data.crouchIcon = data.normalIcon;
                }
            } else {
                ResourceLocation explicitStandCrouchTex = findStrictStandCrouchTexture(namespace, standTokens, ws);
                if (explicitStandCrouchTex != null) {
                    data.hasCrouchVariant = true;
                    data.crouchIcon = explicitStandCrouchTex;
                } else {
                    data.crouchIcon = data.normalIcon;
                }
            }
        }
    }

    private boolean matchesStandToken(String path, List<String> standTokens) {
        for (String st : standTokens) {
            if (st.length() < 2) continue;
            if (path.contains("/" + st + "/") || path.contains("/" + st + "_") || path.contains("_" + st + "_")) {
                return true;
            }
        }
        return false;
    }

    private List<String> buildStandTokens(String slug) {
        List<String> out = new ArrayList<>();
        if (slug == null || slug.isEmpty() || slug.equals("none")) return out;
        String clean = slug.toLowerCase(Locale.ROOT);
        out.add(clean);
        out.add(clean.replace("_", ""));
        List<String> known = STAND_ALIASES.get(clean);
        if (known != null) {
            for (String a : known) {
                if (!out.contains(a)) out.add(a);
            }
        }
        return out;
    }

    private ResourceLocation findBestTexture(String preferredNs, List<String> standTokens,
                                             WheelSlot slot, String moveName, boolean crouch) {
        ResourceLocation best = null;
        int bestScore = 0;

        String cleanMove = moveName == null ? "" : moveName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");

        for (ResourceLocation rl : indexedTextures) {
            String path = rl.getPath().toLowerCase(Locale.ROOT);
            if (path.contains("bar") && !path.contains("barrage")) continue;
            if (path.contains("frame") || path.contains("border") || path.contains("cooldown")
                    || path.contains("meter") || path.contains("overlay") || path.contains("button")
                    || path.contains("logo") || path.contains("title")) {
                continue;
            }

            boolean pathLooksCrouch = path.contains("crouch") || path.contains("_cr.") || path.contains("_cr_")
                    || path.contains("/cr_") || path.contains("shift");

            if (!crouch && pathLooksCrouch) continue;
            if (crouch && !pathLooksCrouch && cleanMove.isEmpty()) continue;

            int score = 0;
            if (rl.getNamespace().equalsIgnoreCase(preferredNs)) {
                score += 15;
            }

            boolean matchedStand = false;
            for (String st : standTokens) {
                if (st.length() < 2) continue;
                if (path.contains("/" + st + "/") || path.contains("/" + st + "_") || path.contains("_" + st + "_")) {
                    score += (st.length() >= 4 ? 120 : 85);
                    matchedStand = true;
                    break;
                }
            }

            boolean matchedSlot = false;
            for (String tok : slot.iconTokens) {
                if (path.endsWith("/" + tok + ".png") || path.endsWith("_" + tok + ".png")
                        || path.contains("/" + tok + "_") || path.contains("_" + tok + "_")) {
                    score += 65;
                    matchedSlot = true;
                    break;
                }
            }

            boolean matchedMoveName = false;
            if (cleanMove.length() >= 3 && path.contains(cleanMove)) {
                score += 100;
                matchedMoveName = true;
            }

            if (crouch && pathLooksCrouch) {
                score += 55;
            }

            if (!matchedSlot && !matchedMoveName) continue;

            if (!matchedStand) {
                if (path.contains("default") || path.contains("generic") || path.contains("common")) {
                    score += 10;
                } else if (looksLikeAnotherStand(path, standTokens)) {
                    continue;
                }
            }

            if (score > bestScore) {
                bestScore = score;
                best = rl;
            }
        }
        return best;
    }

    private ResourceLocation findStrictStandCrouchTexture(String preferredNs, List<String> standTokens, WheelSlot slot) {
        if (standTokens.isEmpty()) return null;
        for (ResourceLocation rl : indexedTextures) {
            String path = rl.getPath().toLowerCase(Locale.ROOT);
            boolean pathLooksCrouch = path.contains("crouch") || path.contains("_cr.") || path.contains("_cr_") || path.contains("shift");
            if (!pathLooksCrouch) continue;
            if (!matchesStandToken(path, standTokens)) continue;

            for (String tok : slot.iconTokens) {
                if (path.contains(tok)) {
                    return rl;
                }
            }
        }
        return null;
    }

    private boolean looksLikeAnotherStand(String path, List<String> currentStandTokens) {
        for (String knownKey : STAND_ALIASES.keySet()) {
            if (knownKey.length() < 3) continue;
            if (currentStandTokens.contains(knownKey)) continue;
            if (path.contains("/" + knownKey + "/") || path.contains("/" + knownKey + "_")) {
                return true;
            }
        }
        return false;
    }

    private Object findMoveInMap(Object moveMap, WheelSlot slot, boolean crouching) {
        if (moveMap == null) return null;
        Class<?> moveClassEnum = findMoveClassEnum();
        if (moveClassEnum == null || !moveClassEnum.isEnum()) return null;

        Object enumVal = matchEnumConstant(moveClassEnum, slot.moveClassTokens);
        if (enumVal == null) return null;

        try {
            for (Method m : moveMap.getClass().getMethods()) {
                String mn = m.getName();
                Class<?>[] params = m.getParameterTypes();
                if ((mn.equals("getMove") || mn.equals("get") || mn.equals("getEntry") || mn.equals("getFirst"))
                        && params.length == 2
                        && params[0].isAssignableFrom(moveClassEnum)
                        && (params[1] == boolean.class || params[1] == Boolean.class)) {
                    Object res = m.invoke(moveMap, enumVal, crouching);
                    return unwrapMoveEntry(res);
                }
            }
            if (!crouching) {
                for (Method m : moveMap.getClass().getMethods()) {
                    String mn = m.getName();
                    Class<?>[] params = m.getParameterTypes();
                    if ((mn.equals("getMove") || mn.equals("get") || mn.equals("getEntries"))
                            && params.length == 1
                            && params[0].isAssignableFrom(moveClassEnum)) {
                        Object res = m.invoke(moveMap, enumVal);
                        if (res instanceof Collection<?> col && !col.isEmpty()) {
                            return unwrapMoveEntry(col.iterator().next());
                        }
                        return unwrapMoveEntry(res);
                    }
                }
            } else {
                for (Method m : moveMap.getClass().getMethods()) {
                    String mn = m.getName();
                    Class<?>[] params = m.getParameterTypes();
                    if ((mn.equals("getEntries") || mn.equals("get"))
                            && params.length == 1
                            && params[0].isAssignableFrom(moveClassEnum)) {
                        Object res = m.invoke(moveMap, enumVal);
                        if (res instanceof Collection<?> col) {
                            for (Object entry : col) {
                                Object crouchVar = JCraftStandCheck.invokeNoArg(entry, "getCrouchingVariant", "crouchingVariant");
                                if (crouchVar != null) {
                                    return unwrapMoveEntry(crouchVar);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Object unwrapMoveEntry(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Optional<?> opt) obj = opt.orElse(null);
        if (obj == null) return null;
        Object inner = JCraftStandCheck.invokeNoArg(obj, "getMove", "move");
        return inner != null ? inner : obj;
    }

    private String extractMoveName(Object move) {
        if (move == null) return "";
        Object val = JCraftStandCheck.invokeNoArg(move, "getName", "name");
        if (val instanceof Component comp) return comp.getString().trim();
        if (val instanceof String s) return s.trim();
        return "";
    }

    private String extractMoveDescription(Object move) {
        if (move == null) return "";
        Object val = JCraftStandCheck.invokeNoArg(move, "getDescription", "description");
        if (val instanceof Component comp) return comp.getString().trim();
        if (val instanceof String s) return s.trim();
        return "";
    }

    private ResourceLocation extractMoveIcon(Object move) {
        if (move == null) return null;
        for (String getter : new String[]{"getMoveIcon", "getIcon", "getTexture", "icon"}) {
            Object val = JCraftStandCheck.invokeNoArg(move, getter);
            if (val instanceof ResourceLocation rl) {
                return rl;
            }
        }
        return null;
    }

    private Class<?> findMoveClassEnum() {
        for (String clsName : MOVE_CLASS_ENUMS) {
            try {
                return Class.forName(clsName);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private Object matchEnumConstant(Class<?> enumCls, String[] tokens) {
        Object[] constants = enumCls.getEnumConstants();
        if (constants == null) return null;
        for (String tok : tokens) {
            for (Object c : constants) {
                if (c.toString().equalsIgnoreCase(tok)) return c;
            }
        }
        for (String tok : tokens) {
            for (Object c : constants) {
                if (c.toString().toUpperCase(Locale.ROOT).contains(tok.toUpperCase(Locale.ROOT))) return c;
            }
        }
        return null;
    }
}
