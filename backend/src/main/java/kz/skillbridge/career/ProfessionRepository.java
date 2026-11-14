package kz.skillbridge.career;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProfessionRepository extends JpaRepository<Profession, Long> {

    List<Profession> findAllByOrderByTitle();

    Optional<Profession> findBySlug(String slug);

    @Query("select ps from ProfessionSkill ps join fetch ps.skill where ps.profession.id = :professionId "
            + "order by ps.importance desc, ps.requiredLevel desc, ps.skill.name")
    List<ProfessionSkill> findRequirements(Long professionId);

    @Query("select ps.profession.id, count(ps) from ProfessionSkill ps group by ps.profession.id")
    List<Object[]> countRequirements();
}
