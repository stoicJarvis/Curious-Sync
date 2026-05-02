package curious.sync.constants;

public class Strings {
    // Kafka Events
    public static final String LIKE_EVENT = "like";
    public static final String UNLIKE_EVENT = "unlike";

    // Kafka Consumer Group
    public static final String LIKES_PROCESSOR_GROUP = "likes-processor-group";

    // Redis Key Prefixes
    public static final String LIKE_STATE_CACHE = "like:state:";
    public static final String LIKE_COUNT_CACHE = "like:count:";
}