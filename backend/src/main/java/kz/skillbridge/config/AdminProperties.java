package kz.skillbridge.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("skillbridge")
public record AdminProperties(List<String> adminEmails) {

    public boolean isAdmin(String email) {
        return adminEmails != null && adminEmails.stream().anyMatch(e -> e.strip().equalsIgnoreCase(email));
    }
}
