package kz.skillbridge.career;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serializable;
import kz.skillbridge.skill.Skill;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Baseline expectation of a skill for a profession; refined later from real vacancies. */
@Entity
@Table(name = "profession_skills")
@Getter
@Setter
@NoArgsConstructor
public class ProfessionSkill {

    @EmbeddedId
    private Id id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("professionId")
    @JoinColumn(name = "profession_id")
    private Profession profession;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("skillId")
    @JoinColumn(name = "skill_id")
    private Skill skill;

    @Column(name = "required_level", nullable = false)
    private Short requiredLevel;

    @Column(nullable = false)
    private Short importance;

    @Embeddable
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Id implements Serializable {
        @Column(name = "profession_id")
        private Long professionId;

        @Column(name = "skill_id")
        private Long skillId;
    }
}
