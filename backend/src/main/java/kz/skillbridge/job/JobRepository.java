package kz.skillbridge.job;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JobRepository extends JpaRepository<Job, Long> {

    Optional<Job> findBySourceAndExternalId(JobSource source, String externalId);

    @Query("select j from Job j where j.profession.id = :professionId and j.archived = false "
            + "order by j.publishedAt desc nulls last, j.id desc")
    List<Job> findActiveByProfession(Long professionId, Pageable page);

    @Query("select js from JobSkill js join fetch js.skill where js.job.id in :jobIds")
    List<JobSkill> findSkillsForJobs(List<Long> jobIds);

    long countByProfessionIdAndArchivedFalse(Long professionId);
}
