package curious.sync.models.core.scyllaDb.Like;

import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import lombok.Builder;

@Builder
@Table("likes")
public class Like {

    @PrimaryKey
    private LikeKey key;

    public Like(LikeKey key) {
        this.key = key;
    }

    public LikeKey getKey() {
        return key;
    }

    public void setKey(LikeKey key) {
        this.key = key;
    }
}