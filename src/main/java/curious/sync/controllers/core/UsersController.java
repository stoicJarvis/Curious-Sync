package curious.sync.controllers.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import curious.sync.models.User;
import curious.sync.services.UsersService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/users")
public class UsersController {

    @Autowired
    UsersService usersService;

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
}