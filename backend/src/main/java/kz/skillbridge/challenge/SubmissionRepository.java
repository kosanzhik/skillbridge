package kz.skillbridge.challenge;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionRepository extends JpaRepository<ChallengeSubmission, Long> {
}
