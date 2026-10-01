package com.vori.backend.receipt.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Gemini 가 영수증·결제 캡처에서 뽑아 준 JSON 의 매핑 대상.
 *
 * 모든 필드가 null 일 수 있다 — 영수증이 아니거나 판독이 안 되면 모델이 null 을 채우도록
 * 프롬프트에 지시해 두었다. 호출부는 null 을 정상 결과로 다뤄야 한다.
 * ignoreUnknown: 모델이 형식 밖의 키를 덧붙여도 파싱이 깨지지 않게.
 *
 * sourceType: 사진 종류(RECEIPT·CARD_ALERT·PAYMENT_SCREEN·TRANSFER·MULTIPLE·NOT_PAYMENT).
 * 이 필드가 생기기 전에 저장된 원문에는 없으므로 null 일 수 있다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExtractedReceipt(
        String sourceType,
        String storeName,
        String date,
        String time,
        Integer totalAmount,
        List<Item> items,
        String paymentMethod,
        String representativeItem
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String name, Integer quantity, Integer amount) {}

    /** 한 화면에 여러 건이 찍혀 어느 것인지 정할 수 없는 사진인지. 이때는 값을 채우지 않는다. */
    public boolean isMultiple() {
        return "MULTIPLE".equals(sourceType);
    }

    /** 가계부 항목명으로 쓸 값. 대표 품목이 없으면 상호명으로 대체한다. */
    public String itemLabel() {
        if (representativeItem != null && !representativeItem.isBlank()) return representativeItem;
        return storeName;
    }
}
