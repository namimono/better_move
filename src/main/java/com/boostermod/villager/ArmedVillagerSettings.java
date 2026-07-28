package com.boostermod.villager;

import com.boostermod.BoosterMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.server.MinecraftServer;

/**
 * 武装村民服务端配置：锁敌目标类型，落盘到 {@code config/boostermod-armed-villager.json}。
 */
public final class ArmedVillagerSettings {
    private static final String CONFIG_DIR = "config";
    private static final String CONFIG_FILE = "boostermod-armed-villager.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicReference<ArmedVillagerSettings> INSTANCE = new AtomicReference<>();

    private final MinecraftServer server;
    private ArmedVillagerTargetMode targetMode;

    private ArmedVillagerSettings(MinecraftServer server, ArmedVillagerTargetMode targetMode) {
        this.server = server;
        this.targetMode = targetMode == null ? ArmedVillagerTargetMode.PLAYERS : targetMode;
    }

    public static ArmedVillagerSettings get(MinecraftServer server) {
        ArmedVillagerSettings existing = INSTANCE.get();
        if (existing != null && existing.server == server) {
            return existing;
        }

        ArmedVillagerSettings loaded = load(server);
        INSTANCE.set(loaded);
        return loaded;
    }

    public ArmedVillagerTargetMode getTargetMode() {
        return targetMode;
    }

    /**
     * @return true 表示模式发生变化并已落盘
     */
    public boolean setTargetMode(ArmedVillagerTargetMode mode) {
        if (mode == null || this.targetMode == mode) {
            return false;
        }
        this.targetMode = mode;
        save();
        return true;
    }

    private void save() {
        Path path = configPath(server);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(new ArmedConfig(targetMode.getId()), writer);
            }
        } catch (IOException e) {
            BoosterMod.LOGGER.error("Failed to save armed villager config to {}", path, e);
        }
    }

    private static ArmedVillagerSettings load(MinecraftServer server) {
        Path path = configPath(server);
        if (!Files.exists(path)) {
            ArmedVillagerSettings settings = new ArmedVillagerSettings(server, ArmedVillagerTargetMode.PLAYERS);
            settings.save();
            return settings;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            ArmedConfig config = GSON.fromJson(reader, ArmedConfig.class);
            ArmedVillagerTargetMode mode = ArmedVillagerTargetMode.PLAYERS;
            if (config != null && config.targetMode() != null) {
                mode = ArmedVillagerTargetMode.byId(config.targetMode()).orElse(ArmedVillagerTargetMode.PLAYERS);
            }
            return new ArmedVillagerSettings(server, mode);
        } catch (IOException e) {
            BoosterMod.LOGGER.warn("Failed to load armed villager config from {}, using defaults.", path, e);
            return new ArmedVillagerSettings(server, ArmedVillagerTargetMode.PLAYERS);
        }
    }

    private static Path configPath(MinecraftServer server) {
        return server.getServerDirectory().resolve(CONFIG_DIR).resolve(CONFIG_FILE);
    }

    private record ArmedConfig(String targetMode) {}
}
