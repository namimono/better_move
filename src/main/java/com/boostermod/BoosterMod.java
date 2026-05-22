package com.boostermod;

import com.boostermod.command.BoosterModCommand;
import com.boostermod.item.BoosterEquipment;
import com.boostermod.item.BoosterLeggingsItem;
import com.boostermod.item.BoosterMotionTicker;
import com.boostermod.network.BoosterFeedbackPayload;
import com.boostermod.network.BoosterRequestPayload;
import com.boostermod.network.BoosterSteerPayload;
import com.boostermod.screen.BoosterUpgradeMenu;
import com.boostermod.screen.BoosterUpgradeOpenData;
import com.boostermod.tier.BoosterTier;
import com.boostermod.upgrade.BoosterUpgradeItem;
import com.boostermod.upgrade.BoosterUpgradeType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.MenuType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BoosterMod implements ModInitializer {
    public static final String MOD_ID = "boostermod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

//     public static final Item BOOSTER_LEGGINGS_WOOD = registerBoosterLeggings(BoosterTier.WOOD);
//     public static final Item BOOSTER_LEGGINGS_STONE = registerBoosterLeggings(BoosterTier.STONE);
    public static final Item BOOSTER_LEGGINGS_COPPER = registerBoosterLeggings(BoosterTier.COPPER);
    public static final Item BOOSTER_LEGGINGS_IRON = registerBoosterLeggings(BoosterTier.IRON);
    public static final Item BOOSTER_LEGGINGS_GOLD = registerBoosterLeggings(BoosterTier.GOLD);
    public static final Item BOOSTER_LEGGINGS_DIAMOND = registerBoosterLeggings(BoosterTier.DIAMOND);
    public static final Item BOOSTER_LEGGINGS_NETHERITE = registerBoosterLeggings(BoosterTier.NETHERITE);
    public static final Item AIR_DASH_UPGRADE = registerUpgrade("air_dash_upgrade", BoosterUpgradeType.AIR_DASH);

    public static final MenuType<BoosterUpgradeMenu> BOOSTER_UPGRADE_MENU = Registry.register(
            BuiltInRegistries.MENU,
            id("booster_upgrade"),
            new ExtendedScreenHandlerType<>(BoosterUpgradeMenu::new, BoosterUpgradeOpenData.CODEC));

    public static final ResourceKey<CreativeModeTab> ITEM_GROUP_KEY =
            ResourceKey.create(BuiltInRegistries.CREATIVE_MODE_TAB.key(), id("main"));

    @Override
    public void onInitialize() {
        Registry.register(
                BuiltInRegistries.CREATIVE_MODE_TAB,
                ITEM_GROUP_KEY,
                FabricItemGroup.builder()
                        .icon(() -> new ItemStack(BOOSTER_LEGGINGS_DIAMOND))
                        .title(Component.translatable("itemGroup.boostermod.main"))
                        .displayItems((params, output) -> {
                        //     output.accept(BOOSTER_LEGGINGS_WOOD);
                        //     output.accept(BOOSTER_LEGGINGS_STONE);
                            output.accept(BOOSTER_LEGGINGS_COPPER);
                            output.accept(BOOSTER_LEGGINGS_IRON);
                            output.accept(BOOSTER_LEGGINGS_GOLD);
                            output.accept(BOOSTER_LEGGINGS_DIAMOND);
                            output.accept(BOOSTER_LEGGINGS_NETHERITE);
                            output.accept(AIR_DASH_UPGRADE);
                        })
                        .build()
        );

        PayloadTypeRegistry.playC2S().register(BoosterRequestPayload.TYPE, BoosterRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BoosterSteerPayload.TYPE, BoosterSteerPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BoosterFeedbackPayload.TYPE, BoosterFeedbackPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(BoosterRequestPayload.TYPE, (payload, context) ->
                context.server().execute(() ->
                        BoosterLeggingsItem.tryBoostFromKey(
                                context.player(),
                                payload.dirX(),
                                payload.dirZ(),
                                payload.jumpTicksAgo(),
                                payload.landingTicksAgo()))
        );
        ServerPlayNetworking.registerGlobalReceiver(BoosterSteerPayload.TYPE, (payload, context) ->
                context.server().execute(() -> BoosterMotionTicker.setSteerInput(
                        context.player().getUUID(), payload.strafe(), payload.forward()))
        );
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                server.execute(() -> BoosterMotionTicker.cancel(handler.player)));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                BoosterModCommand.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(BoosterLeggingsItem::tickActiveMotions);

        BoosterEquipment.initTrinketsCompat();
        if (BoosterEquipment.isTrinketsEnabled()) {
            LOGGER.info("Trinkets detected: booster can also be equipped in the legs/booster trinket slot.");
        }

        LOGGER.info("Booster Mod initialized.");
    }

    private static Item registerBoosterLeggings(BoosterTier tier) {
        String name = "booster_leggings_" + tier.getId();
        ResourceLocation location = id(name);
        ResourceKey<Item> key = ResourceKey.create(BuiltInRegistries.ITEM.key(), location);
        Item.Properties properties = new Item.Properties()
                .stacksTo(1)
                .durability(tier.getDurability());
        BoosterLeggingsItem item = new BoosterLeggingsItem(properties, tier);
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    private static Item registerUpgrade(String name, BoosterUpgradeType type) {
        ResourceLocation location = id(name);
        ResourceKey<Item> key = ResourceKey.create(BuiltInRegistries.ITEM.key(), location);
        Item.Properties properties = new Item.Properties().stacksTo(1);
        BoosterUpgradeItem item = new BoosterUpgradeItem(properties, type);
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
