package com.net128.oss.querytool;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class AsyncCacheManager<T> {
    private final AsyncLoadingCache<String, T> cache;
    private final ConcurrentHashMap<String, CacheEntryMetadata<T>> entryMetadataMap;
    private final ConcurrentHashMap<String, Long> accessTimeMap;

    public AsyncCacheManager() {
        accessTimeMap = new ConcurrentHashMap<>();
        entryMetadataMap = new ConcurrentHashMap<>();
        cache = Caffeine.newBuilder().buildAsync((key, executor) -> loadValue(key));
    }

    public void addEntry(String key, Duration maxTTL, Duration minTTL, Function<String, CompletableFuture<T>> refreshFunction) {
        var currentTime = System.currentTimeMillis();
        var metadata = new CacheEntryMetadata<>(maxTTL, minTTL, refreshFunction);
        entryMetadataMap.put(key, metadata);
        accessTimeMap.put(key, currentTime);
    }

    public CompletableFuture<T> readEntry(String key) {
        CacheEntryMetadata<T> metadata = entryMetadataMap.get(key);
        if(metadata==null) return null;
        return cache.get(key).thenApply(value -> {
            var elapsedTime = getTimeElapsedSinceLastAccess(key);
            var elapsedDuration = Duration.ofMillis(elapsedTime);

            if (elapsedDuration.compareTo(metadata.minTTL()) > 0 && elapsedDuration.compareTo(metadata.maxTTL()) < 0) {
                cache.synchronous().refresh(key);
                accessTimeMap.put(key, System.currentTimeMillis());
            }
            return value;
        });
    }

    private CompletableFuture<T> loadValue(String key) {
        var metadata = entryMetadataMap.get(key);
        if (metadata != null) {
            return metadata.refreshFunction().apply(key);
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    private long getTimeElapsedSinceLastAccess(String key) {
        return System.currentTimeMillis() - accessTimeMap.getOrDefault(key, System.currentTimeMillis());
    }

    private record CacheEntryMetadata<V>(Duration maxTTL, Duration minTTL,
         Function<String, CompletableFuture<V>> refreshFunction) {}
}
