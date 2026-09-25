package curious.sync.services.kafka.kafkaBatchProcessors;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import curious.sync.models.Events.ReactionEvent;
import curious.sync.models.core.scyllaDb.Like.Like;
import curious.sync.models.core.scyllaDb.Like.LikeKey;
import curious.sync.repositories.scyllaDb.Likes.LikeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class LikesBatchProcessor {

    private final LikeRepository likeRepository;

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

        log.info("Processing batch of {} like events", reactionEvents.size());

        for (ReactionEvent reactionEvent : reactionEvents) {
            LikeKey likeKey = LikeKey.builder()
                    .postId(UUID.fromString(reactionEvent.getPostId()))
                    .userId(UUID.fromString(reactionEvent.getUserId()))
                    .build();
            Like like = Like.builder().key(likeKey).build();
            likeRepository.insert(like);
        }
    }
}
