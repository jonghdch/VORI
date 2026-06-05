package com.vori.backend.sanction;

import com.vori.backend.admin.dto.PageResponse;
import com.vori.backend.sanction.dto.SanctionCreateRequest;
import com.vori.backend.sanction.dto.SanctionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 제재 관리. /api/admin/** 라서 SecurityConfig 가 hasRole("ADMIN") 으로 보호.
 */
@RestController
@RequestMapping("/api/admin/sanctions")
@RequiredArgsConstructor
public class SanctionController {

    private final SanctionService sanctionService;

    @GetMapping
    public ResponseEntity<PageResponse<SanctionResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(sanctionService.list(page, size));
    }

    @PostMapping
    public ResponseEntity<SanctionResponse> create(@Valid @RequestBody SanctionCreateRequest req) {
        return ResponseEntity.ok(sanctionService.create(req));
    }

    @PostMapping("/{id}/lift")
    public ResponseEntity<SanctionResponse> lift(@PathVariable Long id) {
        return ResponseEntity.ok(sanctionService.lift(id));
    }
}
