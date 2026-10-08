package com.vori.backend.user.dto;

import jakarta.validation.constraints.Size;

/**
 * 회원 탈퇴 요청. 본인 확인 수단이 가입 방식마다 달라 둘 다 선택 값이다.
 * - 이메일 가입: password 필수 (지금 비밀번호)
 * - 구글 가입(비밀번호 없음): confirmText 에 "탈퇴" 입력
 * 어느 쪽이 필요한지는 AccountDeletionService 가 계정을 보고 검사한다.
 */
public record WithdrawalRequest(
        @Size(max = 100) String password,
        @Size(max = 10) String confirmText
) {}
