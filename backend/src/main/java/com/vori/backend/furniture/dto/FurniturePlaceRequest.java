package com.vori.backend.furniture.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 마이룸 배치 좌표 — 방 크기 대비 **백분율**(0~100)이다. 픽셀이 아니다.
 *
 * PetPage 의 배치 UI 가 이미 퍼센트로 좌표를 들고 있어(INITIAL_FURNITURE_POSITIONS)
 * 그 단위를 그대로 받는다. 비율이라 방 이미지 크기나 화면 폭이 바뀌어도 배치가 깨지지 않는다
 * — 반응형 웹이라 픽셀로 저장하면 창 크기마다 가구가 다른 자리에 놓인다.
 *
 * 상한이 999 였던 것을 100 으로 좁혔다. 넉넉하게 열어 두면 잘못된 단위(픽셀)로 보낸 요청이
 * 조용히 저장되고, 그 뒤에 단위를 바꾸면 이미 배치된 데이터를 전부 옮겨야 한다.
 * user_furniture 가 비어 있는 지금 좁혀 두는 편이 싸다.
 */
public record FurniturePlaceRequest(
        @NotNull(message = "x 좌표를 입력해주세요.")
        @Min(value = 0, message = "좌표는 0 이상이어야 합니다.")
        @Max(value = 100, message = "좌표는 100 이하여야 합니다. (방 크기 대비 %)")
        Short positionX,

        @NotNull(message = "y 좌표를 입력해주세요.")
        @Min(value = 0, message = "좌표는 0 이상이어야 합니다.")
        @Max(value = 100, message = "좌표는 100 이하여야 합니다. (방 크기 대비 %)")
        Short positionY
) {}
