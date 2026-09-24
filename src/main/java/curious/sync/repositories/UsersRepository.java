package curious.sync.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import curious.sync.models.core.User;

@Repository
public interface UsersRepository extends JpaRepository<User, String> {
    
    public Optional<User> findByUsername(String username);
}
