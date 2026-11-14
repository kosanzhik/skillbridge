package kz.skillbridge.career;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CareerGoalRepository extends JpaRepository<CareerGoal, Long> {

    @EntityGraph(attributePaths = "profession")
    List<CareerGoal> findByUserIdOrderByPrimaryDescCreatedAtAsc(Long userId);

    @EntityGraph(attributePaths = "profession")
    Optional<CareerGoal> findByUserIdAndPrimaryTrue(Long userId);

    Optional<CareerGoal> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndProfessionId(Long userId, Long professionId);

    long countByUserId(Long userId);

    /** Bulk + flush so the partial unique index (one primary per user) is never violated mid-transaction. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CareerGoal g set g.primary = false where g.userId = :userId and g.primary = true")
    void clearPrimary(Long userId);
}
