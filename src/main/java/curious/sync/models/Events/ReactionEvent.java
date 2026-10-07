package curious.sync.models.Events;

import curious.sync.constants.ReactionAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactionEvent {
    private Long userId;
    private Long postId;
    private ReactionAction reactionAction;

    @Override
    public String toString() {
        return this.reactionAction.toString() + " " + this.userId + " " + this.postId;
    }
}