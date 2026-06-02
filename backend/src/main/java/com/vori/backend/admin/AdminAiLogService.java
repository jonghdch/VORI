package com.vori.backend.admin;

import com.vori.backend.admin.dto.AiLogResponse;
import com.vori.backend.admin.dto.PageResponse;
import com.vori.backend.inquiry.AiInquiry;
import com.vori.backend.inquiry.AiInquiryRepository;
import com.vori.backend.inquiry.ReasonCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAiLogService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AiInquiryRepository aiInquiryRepository;

    /** AI 대사 로그. 최근 질문순. reason 주어지면 해당 분류만. */
    @Transactional(readOnly = true)
    public PageResponse<AiLogResponse> list(int page, int size, ReasonCategory reason) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "askedAt"));

        Page<AiInquiry> result = (reason == null)
                ? aiInquiryRepository.findAll(pageable)
                : aiInquiryRepository.findByReasonCategory(reason, pageable);

        return PageResponse.of(result, AiLogResponse::from);
    }
}
