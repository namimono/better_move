package com.bettermove;

import com.bettermove.item.DashToolItem;
import com.bettermove.network.DashRequestPayload;
import com.bettermove.tier.DashTier;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BetterMoveMod implements ModInitializer {
    public static final String MOD_ID = "bettermove";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Item DASH_TOOL_WOOD = registerDashTool(DashTier.WOOD);
    public static final Item DASH_TOOL_COPPER = registerDashTool(DashTier.COPPER);
    public static final Item DASH_TOOL_IRON = registerDashTool(DashTier.IRON);
    public static final Item DASH_TOOL_DIAMOND = registerDashTool(DashTier.DIAMOND);
    public static final Item DASH_TOOL_NETHERITE = registerDashTool(DashTier.NETHERITE);

    public static final ResourceKey<CreativeModeTab> ITEM_GROUP_KEY =
            ResourceKey.create(BuiltInRegistries.CREATIVE_MODE_TAB.key(), id("main"));

    @Override
    public void onInitialize() {
        Registry.register(
                BuiltInRegistries.CREATIVE_MODE_TAB,
                ITEM_GROUP_KEY,
                FabricItemGroup.builder()
                        .icon(() -> new ItemStack(DASH_TOOL_DIAMOND))
                        .title(Component.translatable("itemGroup.bettermove.main"))
                        .displayItems((params, output) -> {
                            output.accept(DASH_TOOL_WOOD);
                            output.accept(DASH_TOOL_COPPER);
                            output.accept(DASH_TOOL_IRON);
                            output.accept(DASH_TOOL_DIAMOND);
                            output.accept(DASH_TOOL_NETHERITE);
                        })
                        .build()
        );

        // 双端同时注册 payload 类型——客户端用于发包，服务端用于解析。
        // 放在 main 入口里两边都会执行（环境是 "*"），与客户端入口分离。
        PayloadTypeRegistry.playC2S().register(DashRequestPayload.TYPE, DashRequestPayload.CODEC);

        // 服务端接收冲刺请求。Fabric 的回调发生在网络线程，必须切到主线程
        // 再操作世界/玩家状态，否则会有并发风险（瞬移、播粒子都不是线程安全的）。
        ServerPlayNetworking.registerGlobalReceiver(DashRequestPayload.TYPE, (payload, context) ->
                context.server().execute(() -> DashToolItem.tryDashFromKey(context.player()))
        );

        LOGGER.info("Better Move initialized.");
    }

    private static Item registerDashTool(DashTier tier) {
        String name = "dash_tool_" + tier.getId();
        ResourceLocation location = id(name);
        ResourceKey<Item> key = ResourceKey.create(BuiltInRegistries.ITEM.key(), location);
        Item.Properties properties = new Item.Properties()
                .stacksTo(1)
                .durability(tier.getDurability());
        DashToolItem item = new DashToolItem(properties, tier);
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
