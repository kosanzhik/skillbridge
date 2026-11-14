package kz.skillbridge.challenge.dto;

import kz.skillbridge.challenge.ChallengeType;

public record ChallengeRef(Long id, String title, ChallengeType type, int level) {
}
