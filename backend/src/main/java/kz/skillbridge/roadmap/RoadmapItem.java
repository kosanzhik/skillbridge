package kz.skillbridge.roadmap;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import kz.skillbridge.learning.LearningResource;
import kz.skillbridge.skill.Skill;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "roadmap_items")
@Getter
@Setter
@NoArgsConstructor
public class RoadmapItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "roadmap_id")
    private Roadmap roadmap;

    /** Null for the final project module. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id")
    private Skill skill;

    @Column(nullable = false)
    private Integer position;

    @Column(nullable = false)
    private String title;

    private String goal;

    /** ';'-separated. */
    private String topics;

    /** Skill level that completes this module when reached through an assessment. */
    @Column(name = "target_level")
    private Short targetLevel;

    @Column(name = "estimated_hours")
    private Integer estimatedHours;

    @Column(name = "completion_criteria")
    private String completionCriteria;

    private String rationale;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemStatus status = ItemStatus.TODO;

    @Column(name = "completed_at")
    private Instant completedAt;

    @ManyToMany
    @JoinTable(name = "roadmap_item_resources",
            joinColumns = @JoinColumn(name = "roadmap_item_id"),
            inverseJoinColumns = @JoinColumn(name = "resource_id"))
    private List<LearningResource> resources = new ArrayList<>();
}
