package com.vori.backend.inquiry.dto;

import com.vori.backend.common.PaymentMethod;

/**
 * Step 2 "소비 분석" 화면에서 보여줄 미답변 질문 1건.
 */
public record InquiryResponse(
        Long inquiryId,
        String question,
        Long expenseId,
        String item,
        Integer amount,
        PaymentMethod paymentMethod,
        Long categoryId,
        String categoryName,
        String parentCategoryName
) {}
