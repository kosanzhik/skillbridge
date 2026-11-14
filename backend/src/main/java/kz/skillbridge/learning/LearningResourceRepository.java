package kz.skillbridge.learning;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningResourceRepository extends JpaRepository<LearningResource, Long> {

    @EntityGraph(attributePaths = "skill")
    List<LearningResource> findBySkillIdInAndCheckFailedFalseOrderByLevelAscTitleAsc(Collection<Long> skillIds);

    @EntityGraph(attributePaths = "skill")
    List<LearningResource> findBySkillSlugAndCheckFailedFalseOrderByLevelAscTitleAsc(String slug);
}
