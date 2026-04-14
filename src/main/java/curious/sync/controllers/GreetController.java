package curious.sync.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class GreetController {
    
    @GetMapping("greet")
    public String getMethodName() {
        return new String("Hey there!");
    }
}