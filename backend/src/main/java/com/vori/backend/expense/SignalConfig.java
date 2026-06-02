package com.vori.backend.expense;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 합리성 신호 판정 임계값 설정. 단일 행(id=1).
 * z-score 가 z_green 이하면 GREEN, z_red 초과면 RED, 사이면 GRAY.
 * 관리자가 어드민 화면(합리성 판정 / AI 룰 설정)에서 조정한다.
 */
@Entity
@Table(name = "signal_config")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SignalConfig {

    @Id
    private Integer id;

    @Column(name = "z_red", nullable = false, precision = 4, scale = 2)
    private BigDecimal zRed;

    @Column(name = "z_green", nullable = false, precision = 4, scale = 2)
    private BigDecimal zGreen;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void update(BigDecimal zRed, BigDecimal zGreen) {
        this.zRed = zRed;
        this.zGreen = zGreen;
    }
}
