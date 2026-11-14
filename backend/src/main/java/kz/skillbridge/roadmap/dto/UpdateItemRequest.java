package kz.skillbridge.roadmap.dto;

import jakarta.validation.constraints.NotNull;
import kz.skillbridge.roadmap.ItemStatus;

public record UpdateItemRequest(@NotNull ItemStatus status) {
}
