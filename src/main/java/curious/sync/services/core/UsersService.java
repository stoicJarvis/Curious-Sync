package curious.sync.services.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import curious.sync.models.core.User;
import curious.sync.repositories.UsersRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class UsersService {

    @Autowired
    UsersRepository usersRepository;

    @Autowired
    BloomFilterService bloomFilterService;

    public User createUser(User userToCreate) {
        User created = usersRepository.save(userToCreate);
        if (created.getUsername() != null) {
            bloomFilterService.addUsernameToFilter(created.getUsername());
        }
        return created;
    }

    public User getUser(String userId) {
        return usersRepository.findById(userId)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: " + userId));
    }

    public boolean isUsernameAvailable(String username) {
        return usersRepository.findByUsername(username).isEmpty();
    }
}
