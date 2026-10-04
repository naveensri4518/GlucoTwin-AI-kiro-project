package com.glucotwin.api;

import com.glucotwin.infrastructure.persistence.entity.DeadLetterEventJpaEntity;
import com.glucotwin.infrastructure.persistence.repository.DeadLetterEventJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin")
public class AdminController {

    private final DeadLetterEventJpaRepository deadLetterRepo;

    @GetMapping("/dead-letter-events")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List unresolved dead-letter events (admin only)")
    public ResponseEntity<Page<DeadLetterEventJpaEntity>> listDeadLetterEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                deadLetterRepo.findByResolvedFalseOrderByCreatedAtDesc(PageRequest.of(page, Math.min(size, 100))));
    }
}
