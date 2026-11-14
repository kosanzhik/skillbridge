package kz.skillbridge.challenge;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AssessmentRepository extends JpaRepository<Assessment, Long> {

    @Query("select a from Assessment a join fetch a.submission s join fetch s.challenge c join fetch c.skill "
            + "where s.userId = :userId order by a.createdAt desc, a.id desc")
    List<Assessment> findForUser(Long userId);
}
