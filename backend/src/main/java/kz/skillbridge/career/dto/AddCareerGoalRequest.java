package kz.skillbridge.career.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code primary} defaults to true for the user's first goal. */
public record AddCareerGoalRequest(@NotBlank String professionSlug, Boolean primary) {
}
