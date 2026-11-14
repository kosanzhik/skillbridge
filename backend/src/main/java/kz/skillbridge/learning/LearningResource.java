package kz.skillbridge.learning;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import kz.skillbridge.skill.Skill;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Curated material with a real, periodically re-checked link (spec §4 step 7: no invented courses). */
@Entity
@Table(name = "learning_resources")
@Getter
@Setter
@NoArgsConstructor
public class LearningResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id")
    private Skill skill;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String type;

    private Short level;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    private String language;

    @Column(name = "is_free")
    private Boolean free;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "check_failed", nullable = false)
    private boolean checkFailed;
}
