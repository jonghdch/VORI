package com.vori.backend.pet.dto;

/**
 * 펫 대화 응답·상태.
 *
 * @param reply       펫의 답. 상태 조회(GET)일 때는 null.
 * @param personality 지금 성격 이름 (예: "꼼꼼한 계산쟁이"). 우세 스탯이 바뀌면 달라진다.
 * @param remaining   오늘 남은 대화 횟수
 * @param dailyLimit  하루 대화 횟수
 */
public record PetChatResponse(String reply, String personality, int remaining, int dailyLimit) {}
