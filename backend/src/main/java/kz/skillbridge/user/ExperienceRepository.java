package kz.skillbridge.user;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ExperienceRepository extends JpaRepository<Experience, Long> {

    List<Experience> findByUserIdOrderByStartDateDesc(Long userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from Experience e where e.userId = :userId and e.source = :source")
    void deleteByUserIdAndSource(Long userId, DataSource source);
}
