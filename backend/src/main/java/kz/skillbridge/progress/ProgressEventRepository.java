package kz.skillbridge.progress;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgressEventRepository extends JpaRepository<ProgressEvent, Long> {
    List<ProgressEvent> findTop20ByUserIdOrderByCreatedAtDescIdDesc(Long userId);
}
