package curious.sync.repositories.postgres.Users;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import curious.sync.models.core.postgres.Users.User;

@Repository
public interface UsersRepository extends JpaRepository<User, Long> {
    
}
