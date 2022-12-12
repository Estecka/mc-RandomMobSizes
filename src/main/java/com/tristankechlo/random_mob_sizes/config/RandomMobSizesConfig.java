package com.tristankechlo.random_mob_sizes.config;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.tristankechlo.random_mob_sizes.RandomMobSizesMod;
import com.tristankechlo.random_mob_sizes.sampler.GaussianSampler;
import com.tristankechlo.random_mob_sizes.sampler.ScalingSampler;
import com.tristankechlo.random_mob_sizes.sampler.StaticScalingSampler;
import com.tristankechlo.random_mob_sizes.sampler.UniformScalingSampler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

public final class RandomMobSizesConfig {

    private static final Map<String, BiFunction<JsonElement, String, ScalingSampler>> DESERIALIZER = setupDeserializers();
    public static Map<EntityType<?>, ScalingSampler> SETTINGS = new HashMap<>();
    public static ScalingSampler DEFAULT_SAMPLER = new GaussianSampler(0.5F, 1.5F);
    private static final Type MAP_TYPE = new TypeToken<Map<String, JsonElement>>() {}.getType();

    public static void setToDefault() {
        SETTINGS = getDefaultSettings();
    }

    public static JsonObject serialize(JsonObject json) {
        json.add("default_scaling", DEFAULT_SAMPLER.serialize());
        SETTINGS.forEach((entityType, scalingSampler) -> {
            ResourceLocation location = EntityType.getKey(entityType);
            JsonElement element = scalingSampler.serialize();
            json.add(location.toString(), element);
        });
        return json;
    }

    public static void deserialize(JsonObject json) {
        // deserialize default sampler
        JsonElement defaultElement = json.get("default_scaling");
        if (defaultElement != null) {
            DEFAULT_SAMPLER = deserializeSampler(defaultElement, "default_scaling");
        }
        // deserialize entity specific samplers
        Map<String, JsonElement> settings = ConfigManager.GSON.fromJson(json, MAP_TYPE);
        Map<EntityType<?>, ScalingSampler> newSettings = new HashMap<>();
        settings.forEach((key, value) -> {
            Optional<EntityType<?>> entityType = EntityType.byString(key);
            if (entityType.isPresent()) {
                try {
                    EntityType<?> type = entityType.get();
                    ScalingSampler scalingSampler = deserializeSampler(value, key);
                    newSettings.put(type, scalingSampler);
                } catch (Exception e) {
                    RandomMobSizesMod.LOGGER.error("Error while parsing scaling for entity {}", key);
                }
            } else {
                RandomMobSizesMod.LOGGER.error("Error loading config, unknown EntityType: '{}'", key);
            }
        });
        SETTINGS = ImmutableMap.copyOf(newSettings);
    }

    private static ScalingSampler deserializeSampler(JsonElement jsonElement, String entityType) {
        if (jsonElement.isJsonPrimitive()) {
            return new StaticScalingSampler(jsonElement.getAsFloat());
        } else if (jsonElement.isJsonObject()) {
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            String type = jsonObject.get("type").getAsString();
            BiFunction<JsonElement, String, ScalingSampler> deserializer = DESERIALIZER.get(type);
            if (deserializer == null) {
                throw new JsonParseException("Unknown ScalingType: " + type);
            }
            return deserializer.apply(jsonElement, entityType);
        }
        throw new JsonParseException("ScalingType must be a JsonPrimitive or JsonObject");
    }

    private static Map<String, BiFunction<JsonElement, String, ScalingSampler>> setupDeserializers() {
        Map<String, BiFunction<JsonElement, String, ScalingSampler>> deserializer = new HashMap<>();
        deserializer.put(UniformScalingSampler.TYPE, UniformScalingSampler::new);
        deserializer.put(GaussianSampler.TYPE, GaussianSampler::new);
        return deserializer;
    }

    public static Map<EntityType<?>, ScalingSampler> getDefaultSettings() {
        Map<EntityType<?>, ScalingSampler> settings = new HashMap<>();
        settings.put(EntityType.COW, new UniformScalingSampler(0.5F, 1.5F));
        settings.put(EntityType.BAT, new StaticScalingSampler(0.5F));
        settings.put(EntityType.SHEEP, new GaussianSampler(0.5F, 1.5F));
        return settings;
    }

}
