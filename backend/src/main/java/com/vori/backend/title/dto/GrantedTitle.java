package com.vori.backend.title.dto;

/**
 * 방금 새로 받은 칭호. 획득 순간을 응답에 실어 화면이 바로 알릴 때 쓴다.
 * hidden 이면 조건을 가려 두었던 히든 칭호를 딴 것이다.
 */
public record GrantedTitle(String name, boolean hidden) {
}
