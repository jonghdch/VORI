package com.vori.backend.admin;

import com.vori.backend.admin.dto.AiSettingsResponse;
import com.vori.backend.admin.dto.AiSettingsUpdateRequest;
import com.vori.backend.gemini.AiSwitches;
import com.vori.backend.gemini.GeminiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 관리자 화면 「AI 사용량」 스위치 — 보낸 칸만 바꾸고 한도 상태를 같이 돌려준다. */
class AiSettingsControllerTest {

    private final AiSwitches switches = AiSwitches.allOn();
    private final GeminiClient gemini = mock(GeminiClient.class);
    private final AiSettingsController controller = new AiSettingsController(switches, gemini);

    @Test
    @DisplayName("보낸 스위치만 바꾸고 null 인 칸은 그대로 둔다")
    void updatesOnlyGivenSwitches() {
        List<GeminiClient.ModelQuota> models = List.of(new GeminiClient.ModelQuota("primary", true, "오후 5시"));
        when(gemini.quotaStatus()).thenReturn(models);

        AiSettingsResponse r = controller.update(null, new AiSettingsUpdateRequest(false, null));

        assertThat(r.questionWording()).isFalse();
        assertThat(r.dailyComment()).isTrue();
        assertThat(r.models()).isEqualTo(models);
        assertThat(switches.questionWording()).isFalse();

        controller.update(null, new AiSettingsUpdateRequest(null, false));
        assertThat(controller.get().questionWording()).isFalse();
        assertThat(controller.get().dailyComment()).isFalse();
    }
}
