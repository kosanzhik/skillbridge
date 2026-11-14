package kz.skillbridge.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /** Bulk delete so the DB's ON DELETE CASCADE removes all user-owned rows. */
    @Modifying(clearAutomatically = true)
    @Query("delete from User u where u.id = :id")
    int hardDelete(Long id);
}
