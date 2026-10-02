package com.vori.backend.pet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 펫 대화 요청. 대화 기록은 서버에 저장하지 않아서, 화면이 지금까지의 대화를 함께 보낸다.
 * 길이를 묶어 두는 건 프롬프트가 한없이 길어져 호출 비용·지연이 커지는 걸 막기 위해서다.
 *
 * @param message 이번에 사용자가 한 말 (1~200자)
 * @param history 직전 대화, 오래된 것부터. 최대 12턴.
 */
public record PetChatRequest(
        @NotBlank(message = "메시지를 입력해 주세요")
        @Size(max = 200, message = "메시지는 200자 이내로 입력해 주세요")
        String message,

        @Size(max = 12, message = "대화 기록이 너무 길어요")
        List<@Valid Turn> history
) {
    public PetChatRequest {
        message = message == null ? null : message.trim();
        history = history == null ? List.of() : history;
    }

    /** role: "user" = 사용자, "pet" = 펫. */
    public record Turn(
            @NotNull @Pattern(regexp = "user|pet") String role,
            @NotBlank @Size(max = 500) String text
    ) {}
}
