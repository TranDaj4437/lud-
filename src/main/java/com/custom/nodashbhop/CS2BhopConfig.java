package com.custom.nodashbhop;

/**
 * Cấu hình vật lý CS2 BHop (đồng bộ từ Server do OP quản lý).
 * Riêng showHud là tùy chọn cá nhân từng người chơi (mặc định TẮT, bật/tắt bằng /cs2bhop hud không cần OP).
 */
public final class CS2BhopConfig {
    // Tùy chọn cá nhân client (mọi người chơi tự bật/tắt)
    public static volatile boolean showHud = false;

    // Trạng thái đồng bộ toàn server (chỉ OP thay đổi được)
    public static volatile boolean noDashEnabled = true;
    public static volatile boolean bhopEnabled = true;
    public static volatile boolean autoBhopHoldSpace = true;
    public static volatile boolean scrollWheelJump = false;

    // Thông số BHop CS2 mượt & dễ ăn đà hơn (không ảnh hưởng đi bộ thường)
    public static volatile double airAccelerate = 22.0;
    public static volatile double wishSpeedCap = 0.085;
    public static volatile double maxBhopSpeed = 0.62;      // ~553 u/s
    public static volatile double landingPreserve = 1.00;
    public static volatile double jumpBoost = 0.055;

    private CS2BhopConfig() {}

    public static void applyMatchmakingPreset() {
        airAccelerate = 18.0;
        wishSpeedCap = 0.072;
        maxBhopSpeed = 0.48;     // ~428 u/s (CS2 MM mượt)
        landingPreserve = 0.985;
        jumpBoost = 0.040;
    }

    public static void applyCommunityPreset() {
        airAccelerate = 26.0;
        wishSpeedCap = 0.095;
        maxBhopSpeed = 0.74;     // ~660 u/s (Server BHop/KZ)
        landingPreserve = 1.01;
        jumpBoost = 0.065;
    }
}
