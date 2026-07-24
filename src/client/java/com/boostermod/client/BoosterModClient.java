package com.boostermod.client;

import com.boostermod.BoosterMod;
import com.boostermod.client.screen.BoosterUpgradeScreen;
import com.boostermod.network.BoosterFeedbackPayload;
import com.boostermod.network.BoosterHudStatePayload;
import com.boostermod.network.BoosterShakeStatePayload;
import com.boostermod.network.BoosterStrikeFeedbackPayload;
import com.boostermod.network.BoosterStrikeStackPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;

public class BoosterModClient implements ClientModInitializer {
    public static KeyMapping boostKey;

    @Override
    public void onInitializeClient() {
        MenuScreens.register(BoosterMod.BOOSTER_UPGRADE_MENU, BoosterUpgradeScreen::new);

        boostKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.boostermod.boost",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_Z,
                "key.categories.boostermod"
        ));

        BoostStrikeClientState.init();

        ClientPlayNetworking.registerGlobalReceiver(BoosterFeedbackPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    BoosterFeedbackEffects.trigger(payload.hyper());
                    BoostStrikeClientState.onBoostFeedback();
                }));
        ClientPlayNetworking.registerGlobalReceiver(BoosterStrikeFeedbackPayload.TYPE, (payload, context) ->
                context.client().execute(() -> BoosterStrikeFeedbackEffects.trigger(payload.kill())));
        ClientPlayNetworking.registerGlobalReceiver(BoosterStrikeStackPayload.TYPE, (payload, context) ->
                context.client().execute(() -> BoostStrikeStackState.apply(
                        payload.stackAmount(), payload.maxStack(), payload.remainingTicks())));
        ClientPlayNetworking.registerGlobalReceiver(BoosterHudStatePayload.TYPE, (payload, context) ->
                context.client().execute(() -> BoosterHudState.setEnabled(payload.enabled())));
        ClientPlayNetworking.registerGlobalReceiver(BoosterShakeStatePayload.TYPE, (payload, context) ->
                context.client().execute(() -> BoosterShakeState.setEnabled(payload.enabled())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            BoosterHudState.reset();
            BoosterShakeState.reset();
            BoostStrikeClientState.reset();
            BoostStrikeStackState.reset();
            BoosterInputHandler.reset();
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> BoosterCooldownHud.render(drawContext));

        // 推进键必须在 tick 前半处理，保证同帧攻击包晚于推进包到达服务端。
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            LocalPlayer player = client.player;
            if (player == null) {
                return;
            }
            BoosterInputHandler.tickBoostKey(player, boostKey);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            LocalPlayer player = client.player;
            if (player == null) {
                BoosterInputHandler.reset();
                BoosterReadySound.reset();
                BoostStrikeClientState.reset();
                BoostStrikeStackState.reset();
                return;
            }

            BoosterReadySound.tick(player);
            BoosterFeedbackEffects.tick();
            BoosterStrikeFeedbackEffects.tick();
            BoostStrikeClientState.tick();
            BoostStrikeStackState.tick();
            BoosterInputHandler.tickEnd(player);
        });
    }
}
