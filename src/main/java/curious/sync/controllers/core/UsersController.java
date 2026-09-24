package curious.sync.controllers.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import curious.sync.models.core.User;
import curious.sync.services.core.UsersService;
import curious.sync.services.core.BloomFilterService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/users")
public class UsersController {

    @Autowired
    UsersService usersService;

    @Autowired
    BloomFilterService bloomFilterService;

    @GetMapping
    public String usersGreet() {
        log.debug("GET /api/users - greet endpoint hit");
        return "Greet from users controller";
    }

    @PostMapping()
    public User createFreshUser(@RequestBody User userToCreate) {
        log.info("POST /api/users - creating user with email: {}", userToCreate.getEmail());
        User created = usersService.createUser(userToCreate);
        log.info("User created successfully with id: {}", created.getUser_id());
        return created;
    }

    @GetMapping("/check-username/{username}")
    public boolean isUserNameAvailable(@PathVariable String username) {
        log.info("GET /api/users/check-username/{} - checking username availability", username);
        return usersService.isUsernameAvailable(username);
    }

    @GetMapping("/check-username-bloom/{username}")
    public boolean isUserNameAvailableBloom(@PathVariable String username) {
        log.info("GET /api/users/check-username-bloom/{} - checking username availability using Bloom filter", username);
        boolean mightExist = bloomFilterService.mightUsernameExist(username);
        return !mightExist;
    }
}