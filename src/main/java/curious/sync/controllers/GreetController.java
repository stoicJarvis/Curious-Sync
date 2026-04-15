package curious.sync.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class GreetController {
    
    @GetMapping
    public String greet() {
        return new String("Hey there!");
    }
}