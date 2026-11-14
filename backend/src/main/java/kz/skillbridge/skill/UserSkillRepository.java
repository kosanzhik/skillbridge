package kz.skillbridge.skill;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserSkillRepository extends JpaRepository<UserSkill, Long> {

    @EntityGraph(attributePaths = "skill")
    List<UserSkill> findByUserIdOrderBySkillName(Long userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from UserSkill s where s.userId = :userId and s.source = :source")
    void deleteByUserIdAndSource(Long userId, SkillSource source);
}
