package com.vori.backend.paymentinstrument;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자가 등록한 카드·계좌 1개. 자동 연동이 아니라 "이 카드를 쓴다"는 꼬리표라 잔액·거래를 가져오지 않는다.
 * issuer = 카드사·은행 코드(프론트 constants/issuers.js 의 code). 삭제는 archivedAt 만 채운다.
 */
@Entity
@Table(name = "payment_instruments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PaymentInstrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('DEBIT','CREDIT','CHECKING','SAVINGS','CASH')")
    private InstrumentKind kind;

    @Column(nullable = false, length = 20)
    private String issuer;

    @Column(nullable = false, length = 40)
    private String name;

    // 등록할 때 적은 잔액(계좌·예적금·현금). 카드는 0. 이후 흐름은 아직 반영하지 않는다.
    @Column(name = "opening_balance", nullable = false, columnDefinition = "INT UNSIGNED")
    @Builder.Default
    private Integer openingBalance = 0;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "created_at", nullable = false,
        columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP",
        insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at",
        columnDefinition = "DATETIME NULL ON UPDATE CURRENT_TIMESTAMP",
        insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    /** 이름·카드사·잔액 수정. 종류는 바꾸지 않는다(지난 지출의 결제수단과 어긋나므로). */
    public void updateDetails(String issuer, String name, Integer openingBalance) {
        this.issuer = issuer;
        this.name = name;
        this.openingBalance = openingBalance;
    }

    public void archive(LocalDateTime at) {
        if (archivedAt == null) archivedAt = at;
    }
}
