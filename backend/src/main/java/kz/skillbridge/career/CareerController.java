package kz.skillbridge.career;

import jakarta.validation.Valid;
import java.util.List;
import kz.skillbridge.career.dto.AddCareerGoalRequest;
import kz.skillbridge.career.dto.CareerGoalDto;
import kz.skillbridge.career.dto.ProfessionDetail;
import kz.skillbridge.career.dto.ProfessionSummary;
import kz.skillbridge.common.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CareerController {

    private final CareerService service;

    @GetMapping("/professions")
    public List<ProfessionSummary> professions() {
        return service.listProfessions();
    }

    @GetMapping("/professions/{slug}")
    public ProfessionDetail profession(@PathVariable String slug) {
        return service.profession(slug);
    }

    @GetMapping("/me/career-goals")
    public List<CareerGoalDto> goals(@AuthenticationPrincipal Jwt jwt) {
        return service.goals(CurrentUser.id(jwt));
    }

    @PostMapping("/me/career-goals")
    @ResponseStatus(HttpStatus.CREATED)
    public CareerGoalDto add(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddCareerGoalRequest req) {
        return service.addGoal(CurrentUser.id(jwt), req);
    }

    @PutMapping("/me/career-goals/{id}/primary")
    public CareerGoalDto makePrimary(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.makePrimary(CurrentUser.id(jwt), id);
    }

    @DeleteMapping("/me/career-goals/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        service.deleteGoal(CurrentUser.id(jwt), id);
        return ResponseEntity.noContent().build();
    }
}
