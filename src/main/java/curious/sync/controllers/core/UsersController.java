package curious.sync.controllers.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import curious.sync.models.User;
import curious.sync.services.UsersService;

@RestController
@RequestMapping("/api/users")
public class UsersController {

    @Autowired
    UsersService usersService;

    @GetMapping
    public String usersGreet() {
        return "Greet from users controller";
    }

    @PostMapping()
    public User createFreshUser(@RequestBody User userToCreate) {
        return usersService.createUser(userToCreate);
    }
}