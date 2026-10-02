package com.vori.backend.sanction;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SanctionPolicyTest {

    private final SanctionRepository repository = mock(SanctionRepository.class);
    private final SanctionPolicy policy = new SanctionPolicy(repository);

    private void given(Sanction... active) {
        when(repository.findByUserIdAndLiftedAtIsNull(1L)).thenReturn(List.of(active));
    }

    @Test
    void banBlocks() {
        given(Sanction.builder().type(SanctionType.BAN).build());
        assertTrue(policy.isBlocked(1L));
    }

    @Test
    void suspensionBlocksOnlyUntilExpiry() {
        given(Sanction.builder().type(SanctionType.SUSPENSION).expiresAt(LocalDateTime.now().plusDays(1)).build());
        assertTrue(policy.isBlocked(1L));

        given(Sanction.builder().type(SanctionType.SUSPENSION).expiresAt(LocalDateTime.now().minusMinutes(1)).build());
        assertFalse(policy.isBlocked(1L));
    }

    @Test
    void warningOrNothingDoesNotBlock() {
        given(Sanction.builder().type(SanctionType.WARNING).build());
        assertFalse(policy.isBlocked(1L));

        given();
        assertFalse(policy.isBlocked(1L));
    }
}
