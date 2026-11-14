package kz.skillbridge.resume;

import jakarta.validation.Valid;
import java.util.List;
import kz.skillbridge.common.CurrentUser;
import kz.skillbridge.resume.dto.ParsedResume;
import kz.skillbridge.resume.dto.ResumeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResumeDto upload(@AuthenticationPrincipal Jwt jwt, @RequestParam("file") MultipartFile file) {
        return service.upload(CurrentUser.id(jwt), file);
    }

    @GetMapping
    public List<ResumeDto> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(CurrentUser.id(jwt));
    }

    @GetMapping("/{id}")
    public ResumeDto get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.get(CurrentUser.id(jwt), id);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> file(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"cv.pdf\"")
                .body(service.file(CurrentUser.id(jwt), id));
    }

    @PostMapping("/{id}/parse")
    public ResumeDto reparse(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.reparse(CurrentUser.id(jwt), id);
    }

    @PostMapping("/{id}/confirm")
    public ResumeDto confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                             @Valid @RequestBody ParsedResume body) {
        return service.confirm(CurrentUser.id(jwt), id, body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        service.delete(CurrentUser.id(jwt), id);
        return ResponseEntity.noContent().build();
    }
}
