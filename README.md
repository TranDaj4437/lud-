# JCraft Wheel + NoDash + CS2 BHop (v3.0.0 — Forge 1.20.1)

All-in-one Forge 1.20.1 mod combining:
1. **JCraft Ability Wheel v3**:
   - Exact per-Stand & per-Spec move icons loaded dynamically from JCraft & addons.
   - Shift-only icon swap: holding **Shift** only switches slices that actually have a Shift variant; all other moves keep their normal icons.
   - Live radial cooldown overlay shrinking from the outer rim inward toward the center + remaining seconds timer (`4.2s`).
   - Controls: **R** to cast, tap **X** to quick-cycle, hold **X** to open transparent PvP wheel (**Shift + Hold X** opens Shift wheel; releasing **Shift** returns to normal).
   - Full move descriptions (Normal + Shift) parsed directly from `/stand about` (manual refresh: `/jwheel reload`).
   - Bottom-left HUD: pure Stand name directly above `RequireEvo: <item>`.
2. **JCraft NoDash** (OP-only `/jnodash on|off|status`, server-synced): locks Dash, Combo Breaker, and Cooldown Cancel when player has neither Stand nor Spec, and hides JCraft's default right-side Move UI / cooldown text HUD.
3. **CS2 BHop** (OP-only `/cs2bhop ...`, server-synced): smooth air-strafe physics (vanilla walk speed), plus compact bottom-right `u/s` speedometer toggleable by all players via `/bhophud`.
