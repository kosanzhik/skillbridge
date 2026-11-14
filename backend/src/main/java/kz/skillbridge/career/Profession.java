package kz.skillbridge.career;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "professions")
@Getter
@Setter
@NoArgsConstructor
public class Profession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "hh_query")
    private String hhQuery;

    /** Comma-separated phrases that identify this profession in vacancy titles. */
    @Column(name = "title_keywords")
    private String titleKeywords;

    @OneToMany(mappedBy = "profession")
    private List<ProfessionSkill> skills = new ArrayList<>();
}
