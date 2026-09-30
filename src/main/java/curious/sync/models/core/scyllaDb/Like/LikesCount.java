package curious.sync.models.core.scyllaDb.Like;

import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import lombok.Builder;

@Builder
@Table("likesCount")
public class LikesCount {

    @PrimaryKey("postId")
    private Long postId;

    @Column("count")
    private long count;

    public LikesCount() {}

    public LikesCount(Long postId, long count) {
        this.postId = postId;
        this.count = count;
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public long getLikesCount() {
        return count;
    }

    public void setLikesCount(long count) {
        this.count = Integer.toUnsignedLong((int) count);
    }
}