package com.vori.backend.furniture.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 마이룸 배치 좌표. 격자 크기는 프론트 UI 가 확정되면 맞춰 조정한다.
 * 지금은 음수만 막고 넉넉한 상한을 둔다.
 */
public record FurniturePlaceRequest(
        @NotNull(message = "x 좌표를 입력해주세요.")
        @Min(value = 0, message = "좌표는 0 이상이어야 합니다.")
        @Max(value = 999, message = "좌표가 범위를 벗어났습니다.")
        Short positionX,

        @NotNull(message = "y 좌표를 입력해주세요.")
        @Min(value = 0, message = "좌표는 0 이상이어야 합니다.")
        @Max(value = 999, message = "좌표가 범위를 벗어났습니다.")
        Short positionY
) {}
