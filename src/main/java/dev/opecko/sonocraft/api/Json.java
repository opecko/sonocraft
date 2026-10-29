package dev.opecko.sonocraft.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import dev.opecko.sonocraft.api.model.Album;
import dev.opecko.sonocraft.api.model.Artist;
import dev.opecko.sonocraft.api.model.Episode;
import dev.opecko.sonocraft.api.model.Item;
import dev.opecko.sonocraft.api.model.MediaItem;
import dev.opecko.sonocraft.api.model.Playlist;
import dev.opecko.sonocraft.api.model.Podcast;
import dev.opecko.sonocraft.api.model.Track;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;

/**
 * Gson for Sonolith API types. Unknown fields are ignored, unknown enum values are null and items of an unknown
 * `type` are null (SPEC §5.2).
 */
public final class Json {
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapterFactory(new ItemAdapterFactory())
            .disableHtmlEscaping()
            .create();

    private Json() {
    }

    public static <T> T parse(JsonElement json, Class<T> type) {
        return GSON.fromJson(json, type);
    }

    public static <T> T parse(JsonElement json, Type type) {
        return GSON.fromJson(json, type);
    }

    /** Builder for the params object (params are always named). */
    public static Params params() {
        return new Params();
    }

    public static final class Params {
        private final JsonObject obj = new JsonObject();

        private Params() {
        }

        /** Adds a value; null is skipped (sending null as a param is an error, SPEC §13.3). */
        public Params put(String key, Object value) {
            if (value != null) {
                obj.add(key, GSON.toJsonTree(value));
            }
            return this;
        }

        public JsonObject build() {
            return obj;
        }
    }

    private static final class ItemAdapterFactory implements TypeAdapterFactory {
        private static final Map<String, Class<? extends Item>> ITEMS = Map.of(
                "track", Track.class,
                "episode", Episode.class,
                "album", Album.class,
                "playlist", Playlist.class,
                "artist", Artist.class,
                "podcast", Podcast.class);

        @Override
        @SuppressWarnings("unchecked")
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            Class<?> raw = type.getRawType();
            if (raw != Item.class && raw != MediaItem.class) {
                return null;
            }
            boolean mediaOnly = raw == MediaItem.class;
            TypeAdapter<JsonElement> elements = gson.getAdapter(JsonElement.class);
            return (TypeAdapter<T>) new TypeAdapter<Item>() {
                @Override
                public void write(JsonWriter out, Item value) throws IOException {
                    if (value == null) {
                        out.nullValue();
                        return;
                    }
                    JsonObject o = gson.toJsonTree(value).getAsJsonObject();
                    o.addProperty("type", value.type().wire());
                    elements.write(out, o);
                }

                @Override
                public Item read(JsonReader in) throws IOException {
                    JsonElement e = elements.read(in);
                    if (e == null || e instanceof JsonNull) {
                        return null;
                    }
                    if (!e.isJsonObject()) {
                        throw new JsonParseException("item is not an object");
                    }
                    JsonElement t = e.getAsJsonObject().get("type");
                    Class<? extends Item> cls = t != null && t.isJsonPrimitive() ? ITEMS.get(t.getAsString()) : null;
                    if (cls == null || (mediaOnly && !MediaItem.class.isAssignableFrom(cls))) {
                        return null;
                    }
                    return gson.getDelegateAdapter(ItemAdapterFactory.this, TypeToken.get(cls)).fromJsonTree(e);
                }
            };
        }
    }
}
