package kz.skillbridge.user;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EducationRepository extends JpaRepository<Education, Long> {

    List<Education> findByUserIdOrderByEndYearDesc(Long userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from Education e where e.userId = :userId and e.source = :source")
    void deleteByUserIdAndSource(Long userId, DataSource source);
}
