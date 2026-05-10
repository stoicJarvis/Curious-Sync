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
     * Inserts the given batch of like events into the database and updates the like count of the posts
     */
    void insertBatchOfLikeAndLikesCount(Map<String, List<ReactionEvent>> events);
}