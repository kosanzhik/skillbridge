package kz.skillbridge.job;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import kz.skillbridge.career.Profession;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A real vacancy from an external source. Missing data stays null — never guessed (spec §4 step 4). */
@Entity
@Table(name = "jobs")
@Getter
@Setter
@NoArgsConstructor
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobSource source;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profession_id")
    private Profession profession;

    @Column(nullable = false)
    private String title;

    private String company;
    private String location;

    @Column(name = "salary_from")
    private Integer salaryFrom;

    @Column(name = "salary_to")
    private Integer salaryTo;

    @Column(name = "salary_currency")
    private String salaryCurrency;

    @Column(name = "salary_gross")
    private Boolean salaryGross;

    /** HOUR, DAY, WEEK, MONTH, YEAR; null when not stated. */
    @Column(name = "salary_period")
    private String salaryPeriod;

    /** Salary exactly as published, when the source only gives free text. */
    @Column(name = "salary_text")
    private String salaryText;

    @Column(nullable = false)
    private boolean remote;

    private String experience;
    private String employment;
    private String schedule;
    private String description;

    @Column(nullable = false)
    private String url;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt = Instant.now();

    @Column(nullable = false)
    private boolean archived;

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JobSkill> skills = new ArrayList<>();
}
