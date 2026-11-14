package kz.skillbridge.roadmap;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RoadmapRepository extends JpaRepository<Roadmap, Long> {

    Optional<Roadmap> findFirstByUserIdAndCareerGoalIdAndStatusOrderByCreatedAtDesc(Long userId, Long goalId,
                                                                                   RoadmapStatus status);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Roadmap r set r.status = kz.skillbridge.roadmap.RoadmapStatus.ARCHIVED "
            + "where r.userId = :userId and r.careerGoal.id = :goalId "
            + "and r.status = kz.skillbridge.roadmap.RoadmapStatus.ACTIVE")
    int archiveActive(Long userId, Long goalId);

    @Query("select i from RoadmapItem i join fetch i.roadmap r where i.id = :itemId and r.userId = :userId")
    Optional<RoadmapItem> findItem(Long itemId, Long userId);
}
