// 판정 문구·색을 정하는 순수 함수. 서버 판정 규칙(DailyJudgmentService)이 바뀌면 여기가 먼저 깨져야 한다.
import { signalForUsage } from "../../components/SignalChip";
import { SIGNAL_STATUS } from "../../signalLabels";
import { RULE_TEXT, isSoftenedByReason, judgmentReason, recognizedExpensesFor } from "./JudgmentResults";

// DailyJudgmentService.evaluate 의 그룹 신호 — 쓴 돈 ≤ floor(쓸 수 있는 돈 × 0.85) 초록, ≤ 쓸 수 있는 돈 적정, 넘으면 초과.
const serverSignal = (spent, available) =>
  spent <= Math.floor(available * 0.85) ? "GREEN" : spent <= available ? "GRAY" : "RED";

describe("signalForUsage — 판정 전 홈 칩의 어림", () => {
  test("85% 까지 여유, 100% 까지 적정, 넘으면 초과", () => {
    expect(signalForUsage(8500, 10000)).toBe("GREEN");
    expect(signalForUsage(8501, 10000)).toBe("GRAY");
    expect(signalForUsage(10000, 10000)).toBe("GRAY");
    expect(signalForUsage(10001, 10000)).toBe("RED");
  });

  test("쓸 수 있는 돈이 0원이면 쓴 돈이 있을 때만 초과", () => {
    expect(signalForUsage(0, 0)).toBe("GRAY");
    expect(signalForUsage(1, 0)).toBe("RED");
  });

  test("원 단위 금액이면 서버 규칙과 경계가 같다", () => {
    const mismatches = [];
    for (const available of [1, 7, 99, 1001, 6406, 34323]) {
      for (let spent = 0; spent <= available + 2; spent += Math.max(1, Math.floor(available / 500))) {
        if (signalForUsage(spent, available) !== serverSignal(spent, available)) mismatches.push([spent, available]);
      }
      // 경계 바로 위·아래는 따로 본다
      const edge = Math.floor(available * 0.85);
      for (const spent of [edge - 1, edge, edge + 1, available, available + 1]) {
        if (spent >= 0 && signalForUsage(spent, available) !== serverSignal(spent, available)) mismatches.push([spent, available]);
      }
    }
    expect(mismatches).toEqual([]);
  });
});

describe("판정 결과 문구", () => {
  test("기준 문구가 판정 단어(여유·적정·초과)와 같다", () => {
    expect(RULE_TEXT.thresholds).toContain(`85% 이하 ${SIGNAL_STATUS.GREEN}`);
    expect(RULE_TEXT.thresholds).toContain(`100% 이하 ${SIGNAL_STATUS.GRAY}`);
    expect(RULE_TEXT.thresholds).toContain(SIGNAL_STATUS.RED);
  });

  test("조정된 판정 문구의 조사 — 「여유로」·「적정으로」", () => {
    expect(RULE_TEXT.summarySoftened("식비", "여유")).toBe("식비는 사유가 인정돼 여유로 조정됐어요.");
    expect(RULE_TEXT.summarySoftened("식비", "적정")).toBe("식비는 사유가 인정돼 적정으로 조정됐어요.");
  });

  test("지출 사유를 답했으면 그 사실을 말하고, 기록이 부족하면 기본 기준이라고 말한다", () => {
    expect(judgmentReason({ savedAmount: -167079, reasonCategory: "CEREMONY", zScore: 2.26 }, "GREEN")).toBe(
      "같은 스탯의 평균 소비보다 167,079원 많이 썼어요. 입력한 지출 사유를 반영했어요.",
    );
    expect(judgmentReason({ savedAmount: 0, zScore: null }, "GREEN")).toBe(
      "이전 기록이 충분하지 않아 기본 기준으로 표시했어요.",
    );
    expect(judgmentReason({ savedAmount: 1200, zScore: -0.8 }, "GREEN")).toBe(
      "같은 스탯의 평균 소비보다 1,200원 적게 썼어요.",
    );
  });
});

describe("사유가 인정돼 풀린 그룹", () => {
  const ceremony = { statType: "ENERGY", signalInitial: "RED", signalFinal: "GREEN" };
  const stillRed = { statType: "ENERGY", signalInitial: "RED", signalFinal: "RED" };
  const plain = { statType: "ENERGY", signalInitial: "GREEN", signalFinal: "GREEN" };
  const judgment = (initial, final) => ({
    initialGroupJudgments: { ENERGY: { signal: initial } },
    groupJudgments: { ENERGY: { signal: final } },
  });

  test("처음 빨강이었다가 빨강이 아니게 된 지출만 인정된 지출로 센다", () => {
    expect(recognizedExpensesFor("ENERGY", [ceremony, stillRed, plain])).toEqual([ceremony]);
  });

  test("스탯이 비어 있는 지출은 생활·고정비(ENDURANCE)로 본다", () => {
    const noStat = { signalInitial: "RED", signalFinal: "GRAY" };
    expect(recognizedExpensesFor("ENDURANCE", [noStat])).toEqual([noStat]);
  });

  test("1차 빨강 → 최종 초록이고 인정된 지출이 있을 때만 「사유로 풀림」", () => {
    expect(isSoftenedByReason("ENERGY", judgment("RED", "GREEN"), [ceremony])).toBe(true);
  });

  test("판정 대기 중에 지출을 고쳐 순해진 것은 사유로 풀린 게 아니다", () => {
    expect(isSoftenedByReason("ENERGY", judgment("RED", "GREEN"), [plain])).toBe(false);
  });

  test("1차가 빨강이 아니었거나 최종도 빨강이면 아니다", () => {
    expect(isSoftenedByReason("ENERGY", judgment("GRAY", "GREEN"), [ceremony])).toBe(false);
    expect(isSoftenedByReason("ENERGY", judgment("RED", "RED"), [ceremony])).toBe(false);
  });
});
