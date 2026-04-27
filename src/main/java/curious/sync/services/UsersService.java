package curious.sync.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import curious.sync.models.User;
import curious.sync.repositories.UsersRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class UsersService {

    @Autowired
    UsersRepository usersRepository;

    public User createUser(User userToCreate) {
        return usersRepository.save(userToCreate);
    }

    public User getUser(String userId) {
        return usersRepository.findById(userId)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: " + userId));
    }
}
