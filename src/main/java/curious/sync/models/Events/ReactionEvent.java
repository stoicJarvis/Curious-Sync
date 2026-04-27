package curious.sync.models.Events;

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
    private String userId;
    private String postId;
    private String eventType;

    @Override
    public String toString() {
        return this.eventType + " " + this.userId + " " + this.postId;
    }
}