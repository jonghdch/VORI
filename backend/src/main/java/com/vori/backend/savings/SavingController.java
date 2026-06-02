package com.vori.backend.savings;

import com.vori.backend.auth.UserPrincipal;
import com.vori.backend.savings.dto.SavingCreateRequest;
import com.vori.backend.savings.dto.SavingResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/savings")
@RequiredArgsConstructor
public class SavingController {

    private final SavingService savingService;

    @PostMapping
    public ResponseEntity<SavingResponse> createSaving(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SavingCreateRequest req
    ) {
        SavingResponse response = savingService.createSaving(principal.getUser().getId(), req);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<SavingResponse>> listByDate(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(savingService.listByDate(principal.getUser().getId(), date));
    }
}
