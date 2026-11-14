package kz.skillbridge.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
public class UserProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "full_name")
    private String fullName;

    private String location;

    private String headline;

    /** Stored comma-separated; exposed as a list. */
    private String languages;

    @Enumerated(EnumType.STRING)
    @Column(name = "desired_level")
    private DesiredLevel desiredLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_format")
    private WorkFormat workFormat;

    @Column(name = "onboarded_at")
    private Instant onboardedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UserProfile(User user) {
        this.user = user;
    }

    public List<String> getLanguageList() {
        if (languages == null || languages.isBlank()) {
            return List.of();
        }
        return Arrays.stream(languages.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public void setLanguageList(List<String> list) {
        this.languages = list == null ? null
                : String.join(",", list.stream().map(String::trim).filter(s -> !s.isEmpty()).distinct().toList());
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
