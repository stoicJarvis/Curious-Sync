package curious.sync.models.core.scyllaDb.Like;

import java.util.UUID;

import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import lombok.Builder;

@Builder
@Table("likesCount")
public class LikesCount {

    @PrimaryKey("postId")
    private UUID postId;

    @Column("count")
    private long count;

    public LikesCount() {}

    public LikesCount(UUID postId, long count) {
        this.postId = postId;
        this.count = count;
    }

    public UUID getPostId() {
        return postId;
    }

    public void setPostId(UUID postId) {
        this.postId = postId;
    }

    public long getLikesCount() {
        return count;
    }

    public void setLikesCount(long count) {
        this.count = Integer.toUnsignedLong((int) count);
    }
}