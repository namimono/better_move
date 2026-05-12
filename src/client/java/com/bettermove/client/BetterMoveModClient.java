package com.bettermove.client;

import com.bettermove.network.DashRequestPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端入口：注册冲刺按键并把按键事件转换为发往服务端的请求包。
 *
 * <p>这里只做"采集输入 + 发包"，所有判定（是否穿戴、冷却、距离）都在服务端完成，
 * 客户端不知道也不需要知道这些信息。冷却条会通过原版 {@code ClientboundCooldownPacket}
 * 自动同步到客户端 HUD（这是 {@link net.minecraft.world.item.ItemCooldowns} 自带的）。</p>
 */
public class BetterMoveModClient implements ClientModInitializer {
    /** 冲刺按键。默认绑定 Z，玩家可在「选项 → 控制」里自行修改。 */
    public static KeyMapping dashKey;

    @Override
    public void onInitializeClient() {
        dashKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.bettermove.dash",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_Z,
                "key.categories.bettermove"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            LocalPlayer player = client.player;
            if (player == null) {
                return;
            }
            // consumeClick 在按键被按下且未消费时返回 true，重复按键会逐次返回。
            // while 循环可避免一帧内多次按键被吃掉，但实际上 60 ticks 的冷却会在
            // 服务端兜底，每次发包代价极低。
            while (dashKey.consumeClick()) {
                ClientPlayNetworking.send(new DashRequestPayload(
                        intendedDashDirX(player),
                        intendedDashDirZ(player)));
            }
        });
    }

    /**
     * 取玩家按键表达出的"想往哪走"的水平世界坐标 x 分量。
     * 这样空中残留惯性与当前输入相反时，冲刺仍然跟随当前操作意图。
     */
    private static double intendedDashDirX(LocalPlayer player) {
        return horizontalInputVector(player)[0];
    }

    /** 见 {@link #intendedDashDirX(LocalPlayer)}。 */
    private static double intendedDashDirZ(LocalPlayer player) {
        return horizontalInputVector(player)[1];
    }

    private static double[] horizontalInputVector(LocalPlayer player) {
        float forward = player.input.forwardImpulse;
        float left = player.input.leftImpulse;
        if (Math.abs(forward) < 1.0e-4f && Math.abs(left) < 1.0e-4f) {
            return new double[] {0.0, 0.0};
        }

        double yawRad = Math.toRadians(player.getYRot());
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);
        double x = left * cos - forward * sin;
        double z = forward * cos + left * sin;
        return new double[] {x, z};
    }
}
