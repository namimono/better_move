package com.boostermod.feature;

import com.boostermod.BoosterMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.server.MinecraftServer;

public final class BoosterFeatureSettings {
    private static final String CONFIG_DIR = "config";
    private static final String CONFIG_FILE = "boostermod-features.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicReference<BoosterFeatureSettings> INSTANCE = new AtomicReference<>();

    private final MinecraftServer server;
    private boolean burrowEnabled;
    private boolean verticalLaunchEnabled;
    private boolean noCooldownEnabled;
    private boolean randomImpulseEnabled;

    private BoosterFeatureSettings(MinecraftServer server, FeatureConfig config) {
        this.server = server;
        this.burrowEnabled = config.burrowEnabled();
        this.verticalLaunchEnabled = config.verticalLaunchEnabled();
        this.noCooldownEnabled = config.noCooldownEnabled();
        this.randomImpulseEnabled = config.randomImpulseEnabled();
    }

    public static BoosterFeatureSettings get(MinecraftServer server) {
        BoosterFeatureSettings existing = INSTANCE.get();
        if (existing != null && existing.server == server) {
            return existing;
        }

        BoosterFeatureSettings loaded = load(server);
        INSTANCE.set(loaded);
        return loaded;
    }

    public boolean isEnabled(BoosterFeature feature) {
        return switch (feature) {
            case BURROW -> burrowEnabled;
            case VERTICAL_LAUNCH -> verticalLaunchEnabled;
            case NO_COOLDOWN -> noCooldownEnabled;
            case RANDOM_IMPULSE -> randomImpulseEnabled;
        };
    }

    public boolean setEnabled(BoosterFeature feature, boolean enabled) {
        if (isEnabled(feature) == enabled) {
            return false;
        }

        switch (feature) {
            case BURROW -> burrowEnabled = enabled;
            case VERTICAL_LAUNCH -> verticalLaunchEnabled = enabled;
            case NO_COOLDOWN -> noCooldownEnabled = enabled;
            case RANDOM_IMPULSE -> randomImpulseEnabled = enabled;
        }
        save();
        return true;
    }

    public void save() {
        Path path = configPath(server);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(toConfig(), writer);
            }
        } catch (IOException e) {
            BoosterMod.LOGGER.error("Failed to save booster feature config to {}", path, e);
        }
    }

    private static BoosterFeatureSettings load(MinecraftServer server) {
        Path path = configPath(server);
        if (!Files.exists(path)) {
            BoosterFeatureSettings settings = new BoosterFeatureSettings(server, FeatureConfig.defaults());
            settings.save();
            return settings;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            FeatureConfig config = GSON.fromJson(reader, FeatureConfig.class);
            return new BoosterFeatureSettings(server, config == null ? FeatureConfig.defaults() : config);
        } catch (IOException | JsonParseException e) {
            BoosterMod.LOGGER.warn("Failed to load booster feature config from {}, using defaults.", path, e);
            return new BoosterFeatureSettings(server, FeatureConfig.defaults());
        }
    }

    private FeatureConfig toConfig() {
        return new FeatureConfig(
                burrowEnabled,
                verticalLaunchEnabled,
                noCooldownEnabled,
                randomImpulseEnabled);
    }

    private static Path configPath(MinecraftServer server) {
        return server.getServerDirectory().resolve(CONFIG_DIR).resolve(CONFIG_FILE);
    }

    private record FeatureConfig(
            boolean burrowEnabled,
            boolean verticalLaunchEnabled,
            boolean noCooldownEnabled,
            boolean randomImpulseEnabled) {
        private static FeatureConfig defaults() {
            return new FeatureConfig(false, false, false, false);
        }
    }
}
