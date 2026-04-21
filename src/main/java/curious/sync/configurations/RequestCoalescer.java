package curious.sync.configurations;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RequestCoalescer: Prevents the "Thundering Herd" effect.
 * If X threads request the same Key at once, only ONE thread (the Leader) executes the Supplier. The other X-1 threads (Followers) wait for the Leader's result.
 * This protects our database from being crushed by duplicate concurrent requests.
 */
public class RequestCoalescer<T> {

    private static final Logger log = LoggerFactory.getLogger(RequestCoalescer.class);

    private final String name;
    private final ConcurrentHashMap<String, CompletableFuture<T>> inFlightRequests = new ConcurrentHashMap<>();

    public RequestCoalescer(String name) {
        this.name = name;
    }

    public T coalesce(String key, Supplier<T> supplier) {
        CompletableFuture<T> newFuture = new CompletableFuture<>();
        CompletableFuture<T> existing = inFlightRequests.putIfAbsent(key, newFuture);

        if (existing != null) {
            log.info("[{}] COALESCED request for key: {} (queuing in-flight request)", name, key);
            try {
                return existing.join();
            } catch (CompletionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                throw new RuntimeException(cause);
            }
        }

        log.debug("[{}] LEADER request for key: {} (executing actual work)", name, key);
        try {
            T result = supplier.get();
            newFuture.complete(result);
            return result;
        } catch (Exception e) {
            newFuture.completeExceptionally(e);
            throw e;
        } finally {
            inFlightRequests.remove(key);
        }
    }
}
