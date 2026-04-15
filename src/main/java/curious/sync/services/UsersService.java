package curious.sync.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import curious.sync.models.User;
import curious.sync.repositories.UsersRepository;

@Service
public class UsersService {

    @Autowired
    UsersRepository usersRepository;

    public User createUser(User userToCreate) {
        return usersRepository.save(userToCreate);
    }
}
