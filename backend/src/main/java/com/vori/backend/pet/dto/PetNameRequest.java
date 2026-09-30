package com.vori.backend.pet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 펫 이름 짓기 요청. 1~10자.
 */
public record PetNameRequest(
        @NotBlank(message = "펫 이름을 입력해 주세요")
        @Size(max = 10, message = "펫 이름은 10자 이내로 입력해 주세요")
        String name
) {
    /** 공백을 먼저 잘라 둔다. 검사(@Size)와 저장이 같은 값을 봐야 한다. */
    public PetNameRequest {
        name = name == null ? null : name.trim();
    }
}
