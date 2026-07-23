package com.boostermod.client;

import com.boostermod.combat.BoostStrikeSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 客户端推进破击窗口：同步触及加成，使本地准星选取与服务端加长距离一致。
 */
public final class BoostStrikeClientState {
    /**
     * 覆盖：推进推力（约 10 tick）+ 结束后破击宽限 + 余量。
     * 与服务端 {@link com.boostermod.combat.BoostStrikeSupport#POST_BOOST_GRACE_TICKS} 对齐。
     */
    private static final int REACH_WINDOW_TICKS =
            10 + BoostStrikeSupport.POST_BOOST_GRACE_TICKS + 4;

    private static int reachTicksLeft;

    private BoostStrikeClientState() {}

    public static void init() {
        BoostStrikeSupport.BoostStrikeClientHooks.setPredicate(BoostStrikeClientState::isActiveFor);
    }

    /** 客户端按下推进键时调用（早于服务端反馈），保证推进瞬间即可辅助锁定。 */
    public static void onBoostRequest() {
        openWindow();
    }

    /** 服务端推进反馈到达时刷新窗口时长（与 onBoostRequest 可叠加，取刷新）。 */
    public static void onBoostFeedback() {
        openWindow();
    }

    private static void openWindow() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !BoostStrikeSupport.hasBoostStrikeUpgrade(player)) {
            return;
        }
        BoostStrikeSupport.applyReachBonus(player);
        reachTicksLeft = REACH_WINDOW_TICKS;
    }

    public static void tick() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            reachTicksLeft = 0;
            return;
        }
        if (reachTicksLeft <= 0) {
            BoostStrikeSupport.removeReachBonus(player);
            return;
        }
        reachTicksLeft--;
        if (reachTicksLeft <= 0) {
            BoostStrikeSupport.removeReachBonus(player);
        } else if (BoostStrikeSupport.hasBoostStrikeUpgrade(player)) {
            BoostStrikeSupport.applyReachBonus(player);
        }
    }

    public static void reset() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            BoostStrikeSupport.removeReachBonus(player);
        }
        reachTicksLeft = 0;
    }

    private static boolean isActiveFor(Player player) {
        LocalPlayer local = Minecraft.getInstance().player;
        return local != null && local == player && reachTicksLeft > 0
                && BoostStrikeSupport.hasBoostStrikeUpgrade(player);
    }
}
