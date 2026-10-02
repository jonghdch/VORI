package com.vori.backend.onboarding;

import com.vori.backend.auth.UserPrincipal;
import com.vori.backend.onboarding.dto.OnboardingStatusResponse;
import com.vori.backend.onboarding.dto.SpendingProfileRequest;
import com.vori.backend.onboarding.dto.SpendingProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    @GetMapping("/status")
    public ResponseEntity<OnboardingStatusResponse> status(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(onboardingService.status(principal.getId()));
    }

    @PostMapping("/spending-profile")
    public ResponseEntity<SpendingProfileResponse> saveProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SpendingProfileRequest req
    ) {
        return ResponseEntity.ok(onboardingService.saveProfile(principal.getId(), req));
    }

    @PostMapping("/complete")
    public ResponseEntity<Void> complete(@AuthenticationPrincipal UserPrincipal principal) {
        onboardingService.complete(principal.getId());
        return ResponseEntity.noContent().build();
    }
}
