package curious.sync.repositories.Likes;

import java.util.List;
import java.util.Map;

import curious.sync.models.Events.ReactionEvent;

public interface ILikesRepository {
    /**
     * Removes events from the batch if the user has already liked the post.
     */
    Map<String, List<ReactionEvent>> filterAlreadyLikedEvents(Map<String, List<ReactionEvent>> events);

    /**
     * Inserts the given batch of like events into the database and updates the like count of the posts.
     * Returns a map of postId → actual number of NEW likes inserted (excluding ON CONFLICT duplicates).
     */
    Map<String, Long> insertBatchOfLikeAndLikesCount(Map<String, List<ReactionEvent>> events);

    /**
     * Removes events from the batch if the user has already unliked the post.
     */
    Map<String, List<ReactionEvent>> discardOrphanedUnlikes(Map<String, List<ReactionEvent>> events);

    /**
     * Removes the given batch of unlike events from the database and updates the like count of the posts.
     * Returns a map of postId → actual number of likes deleted.
     */
    Map<String, Long> deleteBatchOfUnlikeAndLikesCount(Map<String, List<ReactionEvent>> unlikesEvent);
}