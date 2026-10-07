package com.vori.backend.gemini;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 없어도 기능이 멈추지 않는 AI 문장 생성을 끄는 스위치. 11/12 게스트 시연처럼 한도(모델당 하루 약 20회)가
 * 모자랄 때 끄고, 남은 한도를 펫 대화·영수증·사유 분류에 남긴다.
 * <ul>
 *   <li>질문 문구 — 끄면 템플릿 문구(AiInquiry.templateQuestion)가 그대로 나간다.</li>
 *   <li>일일 코멘트 — 끄면 리포트에 통계만 남는다(AI 가 실패했을 때와 같다). 0시 10분 배치가 전날 기록이 있는
 *       사용자 전원에게 1회씩 부르는데, 한도는 태평양 자정(한국 16~17시)에 풀리므로 그 배치와 다음 날 낮이
 *       같은 하루 한도를 나눠 쓴다.</li>
 * </ul>
 * 기본값은 환경변수, 관리자 화면(AiSettingsController)에서 재시작 없이 바꾼다. 메모리에만 두므로 재시작하면
 * 기본값으로 돌아간다 — 시연 날엔 환경변수로 꺼 두면 재시작해도 꺼진 채다.
 */
@Component
public class AiSwitches {

    private final AtomicBoolean questionWording;
    private final AtomicBoolean dailyComment;

    public AiSwitches(@Value("${ai.question-wording.enabled:true}") boolean questionWording,
                      @Value("${ai.daily-comment.enabled:true}") boolean dailyComment) {
        this.questionWording = new AtomicBoolean(questionWording);
        this.dailyComment = new AtomicBoolean(dailyComment);
    }

    /** 테스트·기본 — 둘 다 켜짐. */
    public static AiSwitches allOn() {
        return new AiSwitches(true, true);
    }

    public boolean questionWording() {
        return questionWording.get();
    }

    public boolean dailyComment() {
        return dailyComment.get();
    }

    public void setQuestionWording(boolean on) {
        questionWording.set(on);
    }

    public void setDailyComment(boolean on) {
        dailyComment.set(on);
    }
}
