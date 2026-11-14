package kz.skillbridge.challenge;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChallengeRepository extends JpaRepository<Challenge, Long> {

    @EntityGraph(attributePaths = "skill")
    List<Challenge> findBySkillSlugOrderByLevelAscIdAsc(String slug);

    @EntityGraph(attributePaths = "skill")
    List<Challenge> findBySkillIdInOrderByLevelAscIdAsc(Collection<Long> skillIds);
}
