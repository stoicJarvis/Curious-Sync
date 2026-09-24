package curious.sync.services.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

import curious.sync.models.core.User;
import curious.sync.repositories.UsersRepository;

@Service
@Slf4j
public class BloomFilterService {

    @Autowired
    private UsersRepository usersRepository;

    private BloomFilter<String> usernameBloomFilter;
    private static final double FALSE_POSITIVE_PROBABILITY = 0.01;
    private volatile boolean isInitialized = false;

    public void initializeBloomFilter() {
        try {
            log.info("Initializing Bloom filter for usernames");
            List<User> allUsers = usersRepository.findAll();

            usernameBloomFilter = BloomFilter.create(
                    Funnels.stringFunnel(StandardCharsets.UTF_8),
                    Math.max(allUsers.size(), 1000),
                    FALSE_POSITIVE_PROBABILITY
            );

            for (User user : allUsers) {
                if (user.getUsername() != null) {
                    usernameBloomFilter.put(user.getUsername());
                }
            }

            isInitialized = true;
            log.info("Bloom filter initialized with {} usernames", allUsers.size());
        } catch (Exception e) {
            log.error("Error initializing Bloom filter", e);
            isInitialized = false;
        }
    }

    public boolean mightUsernameExist(String username) {
        if (!isInitialized) {
            log.warn("Bloom filter not initialized, initializing now");
            initializeBloomFilter();
        }

        if (username == null || username.trim().isEmpty()) {
            return false;
        }

        return usernameBloomFilter.mightContain(username.trim());
    }

    public void addUsernameToFilter(String username) {
        if (isInitialized && username != null && !username.trim().isEmpty()) {
            usernameBloomFilter.put(username.trim());
            log.debug("Added username to Bloom filter: {}", username);
        }
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public void reinitializeBloomFilter() {
        log.info("Reinitializing Bloom filter");
        initializeBloomFilter();
    }
}
