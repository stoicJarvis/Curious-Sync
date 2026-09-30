package curious.sync.services.kafka.kafkaBatchProcessors;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.data.cassandra.core.AsyncCassandraTemplate;
import org.springframework.stereotype.Service;

import curious.sync.models.Events.ReactionEvent;
import curious.sync.models.core.scyllaDb.Like.Like;
import curious.sync.models.core.scyllaDb.Like.LikeKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReactionsBatchProcessor {


    private final AsyncCassandraTemplate asyncCassandraTemplate;

    public void processBatchOfLikes(List<ReactionEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        /*
            1. add some filtering and duplication of likes and checks about user and post existence
            2. add the logic for adding the data to redis
        */

        processLikes(events);
    }

    private void processLikes(List<ReactionEvent> reactionEvents) {
        if (reactionEvents == null || reactionEvents.isEmpty()) {
            return;
        }

        log.info("Firing {} async like events to ScyllaDB", reactionEvents.size());

        CompletableFuture<?>[] futures = reactionEvents.stream()
                .map(reactionEvent -> {
                    LikeKey likeKey = LikeKey.builder()
                            .postId(Long.parseLong(reactionEvent.getPostId()))
                            .userId(Long.parseLong(reactionEvent.getUserId()))
                            .build();
                    
                    Like like = Like.builder().key(likeKey).build();
                    
                    return asyncCassandraTemplate.insert(like);
                })
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).join();
        
        log.info("Batch of {} successfully inserted", reactionEvents.size());
    }
}
