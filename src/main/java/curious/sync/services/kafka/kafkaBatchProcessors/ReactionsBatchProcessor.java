package curious.sync.services.kafka.kafkaBatchProcessors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

import org.springframework.stereotype.Service;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;

import curious.sync.models.Events.ReactionEvent;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ReactionsBatchProcessor {

    private final CqlSession session;
    private final PreparedStatement insertLikeStmt;
    private final Semaphore inFlightLimiter;

    public ReactionsBatchProcessor(CqlSession session) {
        this.session = session;
        
        // Limit to 2048 concurrent requests to prevent driver queue overflow
        this.inFlightLimiter = new Semaphore(2048);
        
        // Prepare the statement ONCE during bean initialization
        this.insertLikeStmt = session.prepare(
            "INSERT INTO dev_sandbox.likes (\"postId\", \"userId\") VALUES (?, ?)"
        );
    }

    public void processBatchOfLikes(List<ReactionEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        /*
            1. add some filtering and duplication of likes
            2. add the logic for adding the data to redis
        */

        processLikes(events);
    }

    private void processLikes(List<ReactionEvent> reactionEvents) {
        if (reactionEvents == null || reactionEvents.isEmpty()) {
            return;
        }

        log.info("Firing {} async like events to ScyllaDB", reactionEvents.size());

        List<CompletableFuture<?>> futures = new ArrayList<>(reactionEvents.size());

        for (ReactionEvent event : reactionEvents) {
            try {
                // Block the loop if there are already 2048 queries waiting for the DB
                inFlightLimiter.acquire();

                long postId = Long.parseLong(event.getPostId());
                long userId = Long.parseLong(event.getUserId());

                // Bind directly to the pre-compiled binary statement
                BoundStatement boundStatement = insertLikeStmt.bind(postId, userId);

                // Execute async and release the permit ONLY when finished
                CompletableFuture<?> future = session.executeAsync(boundStatement)
                        .toCompletableFuture()
                        .whenComplete((res, ex) -> inFlightLimiter.release());

                futures.add(future);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Thread interrupted while waiting for ScyllaDB permit", e);
            }
        }

        // Wait for all futures in this batch to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        log.info("Batch of {} successfully inserted", reactionEvents.size());
    }
}