package com.vori.backend.receipt.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Gemini 가 영수증에서 뽑아 준 JSON 의 매핑 대상.
 *
 * 모든 필드가 null 일 수 있다 — 영수증이 아니거나 판독이 안 되면 모델이 null 을 채우도록
 * 프롬프트에 지시해 두었다. 호출부는 null 을 정상 결과로 다뤄야 한다.
 * ignoreUnknown: 모델이 형식 밖의 키를 덧붙여도 파싱이 깨지지 않게.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExtractedReceipt(
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

    /** 가계부 항목명으로 쓸 값. 대표 품목이 없으면 상호명으로 대체한다. */
    public String itemLabel() {
        if (representativeItem != null && !representativeItem.isBlank()) return representativeItem;
        return storeName;
    }
}
