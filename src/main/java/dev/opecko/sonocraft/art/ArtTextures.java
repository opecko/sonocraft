package dev.opecko.sonocraft.art;

import com.mojang.blaze3d.platform.NativeImage;
import dev.opecko.sonocraft.Sonocraft;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Artwork textures: downloaded and decoded on "Sonocraft-Art" threads, uploaded on the render thread, kept in an
 * LRU cache that releases evicted textures. All public methods run on the render thread.
 */
public final class ArtTextures {
    /** A loaded artwork: its texture ({@code size}×{@code size}), accent colour (-1 = none) and pixels. */
    public record Art(Identifier id, int size, int accent, Pixels pixels) {
    }

    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final long RETRY_MS = 60_000;
    private static final AtomicInteger THREADS = new AtomicInteger();

    private final int capacity;
    private final LinkedHashMap<String, Art> cache = new LinkedHashMap<>(16, 0.75f, true);
    private final Set<String> loading = new HashSet<>();
    private final Map<String, Long> failedAt = new HashMap<>();
    /** Rounded variants per base texture and corner radius in pixels. */
    private final Map<Identifier, Map<Integer, Identifier>> rounded = new HashMap<>();
    /** Thumbnails for lists; many at once. */
    private final ExecutorService executor = Executors.newFixedThreadPool(3, r -> daemon(r, "Sonocraft-Art-"));
    /** The current item's artwork never waits behind a page of thumbnails. */
    private final ExecutorService priorityExecutor = Executors.newSingleThreadExecutor(r -> daemon(r, "Sonocraft-Art-Now-"));
    // HttpClient keeps its own executor: sharing ours deadlocked once every thread was blocked in send()
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private BiConsumer<String, Art> onLoaded = (key, art) -> { };
    private int nextId;

    public ArtTextures(int capacity) {
        this.capacity = capacity;
    }

    /** Called on the render thread with the key ({@link #key}) and the art, or null when loading failed. */
    public void onLoaded(BiConsumer<String, Art> listener) {
        this.onLoaded = listener;
    }

    public static String key(String url, int size) {
        return size + "@" + url;
    }

    private static Thread daemon(Runnable r, String prefix) {
        Thread t = new Thread(r, prefix + THREADS.incrementAndGet());
        t.setDaemon(true);
        return t;
    }

    /** The art if it is ready; otherwise starts loading it (once) and returns null. */
    public Art get(String url, int size) {
        return get(url, size, false);
    }

    /** As {@link #get(String, int)}; {@code priority} loads it ahead of list thumbnails (the playing item). */
    public Art get(String url, int size, boolean priority) {
        if (url == null) {
            return null;
        }
        String key = key(url, size);
        Art art = cache.get(key);
        if (art != null || loading.contains(key)) {
            return art;
        }
        Long failed = failedAt.get(key);
        if (failed != null && System.currentTimeMillis() - failed < RETRY_MS) {
            return null;
        }
        loading.add(key);
        String sized = ArtUrls.sized(url, size);
        (priority ? priorityExecutor : executor).execute(() -> {
            Pixels px = null;
            int accent = -1;
            try {
                px = ArtDecoder.decode(download(sized), size);
                accent = ArtPalette.accent(px);
            } catch (IOException | RuntimeException e) {
                Sonocraft.LOGGER.debug("Artwork failed for {}: {}", sized, e.toString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            Pixels pixels = px;
            int color = accent;
            Minecraft.getInstance().execute(() -> install(key, pixels, size, color));
        });
        return null;
    }

    private byte[] download(String url) throws IOException, InterruptedException {
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IOException("not an https URL");
        }
        HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                .header("User-Agent", Sonocraft.NAME).GET().build();
        HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = res.body()) {
            if (res.statusCode() != 200) {
                throw new IOException("HTTP " + res.statusCode());
            }
            byte[] data = in.readNBytes(MAX_BYTES + 1);
            if (data.length > MAX_BYTES) {
                throw new IOException("artwork over " + MAX_BYTES + " bytes");
            }
            return data;
        }
    }

    private void install(String key, Pixels px, int size, int accent) {
        loading.remove(key);
        if (px == null) {
            failedAt.put(key, System.currentTimeMillis());
            onLoaded.accept(key, null);
            return;
        }
        Identifier id = upload(px, 0);
        Art art = new Art(id, size, accent, px);
        cache.put(key, art);
        evict();
        onLoaded.accept(key, art);
    }

    /** The art with corners rounded by {@code radius} pixels of its texture; made once per radius. */
    public Identifier rounded(Art art, int radius) {
        if (radius <= 0) {
            return art.id();
        }
        return rounded.computeIfAbsent(art.id(), k -> new HashMap<>())
                .computeIfAbsent(radius, r -> upload(art.pixels(), r));
    }

    private Identifier upload(Pixels px, int radius) {
        int size = px.width();
        NativeImage image = new NativeImage(size, px.height(), false);
        for (int y = 0; y < px.height(); y++) {
            for (int x = 0; x < size; x++) {
                image.setPixel(x, y, Corners.mask(px.at(x, y), x, y, size, px.height(), radius));
            }
        }
        Identifier id = Identifier.fromNamespaceAndPath(Sonocraft.MOD_ID, "art/" + nextId++);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Sonocraft artwork", image));
        return id;
    }

    private void evict() {
        Iterator<Map.Entry<String, Art>> it = cache.entrySet().iterator();
        while (cache.size() > capacity && it.hasNext()) {
            Art old = it.next().getValue();
            it.remove();
            var textures = Minecraft.getInstance().getTextureManager();
            textures.release(old.id());
            Map<Integer, Identifier> variants = rounded.remove(old.id());
            if (variants != null) {
                variants.values().forEach(textures::release);
            }
        }
    }

    public void close() {
        executor.shutdownNow();
        priorityExecutor.shutdownNow();
    }
}
