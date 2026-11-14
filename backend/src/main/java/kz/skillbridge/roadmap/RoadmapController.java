package kz.skillbridge.roadmap;

import jakarta.validation.Valid;
import kz.skillbridge.common.CurrentUser;
import kz.skillbridge.roadmap.dto.RoadmapDto;
import kz.skillbridge.roadmap.dto.UpdateItemRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me/roadmap")
@RequiredArgsConstructor
public class RoadmapController {

    private final RoadmapService service;

    @GetMapping
    public RoadmapDto current(@AuthenticationPrincipal Jwt jwt) {
        return service.current(CurrentUser.id(jwt));
    }

    /** (Re)generates the roadmap for the primary goal; the previous one is archived. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoadmapDto generate(@AuthenticationPrincipal Jwt jwt) {
        return service.generate(CurrentUser.id(jwt));
    }

    @PatchMapping("/items/{id}")
    public RoadmapDto updateItem(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                 @Valid @RequestBody UpdateItemRequest req) {
        return service.updateItem(CurrentUser.id(jwt), id, req.status());
    }
}
