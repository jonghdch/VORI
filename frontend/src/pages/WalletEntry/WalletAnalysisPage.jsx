import { useEffect, useId, useRef, useState } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { toIsoDate } from "./utils";
import { answerInquiry, listInquiriesByDate } from "../../api/inquiries";
import { listExpensesByDate } from "../../api/ledger";
import { getActivePet } from "../../api/pet";
import { finalizeDateJudgment, getDateJudgment, startDateJudgment } from "../../api/dailyJudgment";
import JudgmentResults, { isSoftenedByReason, judgmentReason, RULE_TEXT } from "./JudgmentResults";
import { canUseAiJudge } from "../../config";
import { SIGNAL_STATUS } from "../../signalLabels";
import { petDisplayName } from "../../components/petVisual";
import Icon from "../../components/Icon";
import SignalChip, { categoryIcon } from "../../components/SignalChip";
import { REASON_CHIPS } from "./reasonChips";
import "../Home/HomeScreen.css";
import "./WalletAnalysisScreen.css";

// 답변 글자 수는 서버 AnswerRequest 의 @Size(max = 500) 과 같다. 넘으면 칩을 골라도 저장이 거절된다.
const ANSWER_MAX = 500;
const STAT_TYPES = ["ENERGY", "CHARM", "IQ", "ENDURANCE"];
const STAT_LABELS = {
  ENERGY: "식비",
  CHARM: "쇼핑·뷰티",
  IQ: "문화·여가",
  ENDURANCE: "생활·고정비",
};
function formatDateTitle(dateStr) {
  const [, month, day] = dateStr.split("-");
  return `${Number(month)}월 ${Number(day)}일 판정`;
}

function formatTime(expense) {
  if (!expense?.timeProvided || !expense.spentAt) return null;
  return String(expense.spentAt).split(/[T ]/)[1]?.slice(0, 5) || null;
}

function getResultSummary(judgment, expenses) {
  const finalGroups = judgment?.groupJudgments || {};
  if (Object.keys(finalGroups).length === 0) return RULE_TEXT.summaryNoDetail;
  // 그룹 카드의 판정 과정과 같은 기준 — 지출을 고쳐 순해진 그룹을 「사유가 인정돼」라고 하지 않는다
  const softened = STAT_TYPES.filter((statType) => isSoftenedByReason(statType, judgment, expenses));
  const exceeded = STAT_TYPES.filter((statType) =>
    finalGroups[statType]?.signal === "RED" && !finalGroups[statType]?.budgetExhausted
  );
  const exceededLabels = exceeded.map((statType) => STAT_LABELS[statType]).join(", ");
  if (softened.length > 0) {
    const labels = softened.map((statType) => STAT_LABELS[statType]).join(", ");
    const statuses = [...new Set(softened.map((statType) => finalGroups[statType].signal))];
    const status = statuses.length === 1 ? SIGNAL_STATUS[statuses[0]] : null;
    // 풀린 그룹만 말하면 다른 그룹이 넘어 맨 위가 「초과」인 날에 이유가 안 보인다
    if (exceeded.length > 0) return RULE_TEXT.summarySoftenedAndExceeded(labels, status, exceededLabels);
    return status ? RULE_TEXT.summarySoftened(labels, status) : RULE_TEXT.summarySoftenedMixed(labels);
  }
  if (exceeded.length > 0) return RULE_TEXT.summaryExceeded(exceededLabels);

  const exhausted = STAT_TYPES.filter((statType) => finalGroups[statType]?.budgetExhausted);
  if (exhausted.length > 0) {
    return RULE_TEXT.summaryExhausted(exhausted.map((statType) => STAT_LABELS[statType]).join(", "));
  }

  if (STAT_TYPES.every((statType) => finalGroups[statType]?.signal === "GREEN")) {
    return RULE_TEXT.summaryAllGreen;
  }
  return RULE_TEXT.summaryNoExceeded;
}

function WalletAnalysisPage({ user }) {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  // 오늘 날짜는 들어온 순간에 고정한다 — 자정을 넘기면 다른 날짜로 확정을 보내 그날 판정이 확정되지 못한다
  const [enteredOn] = useState(() => toIsoDate());
  const dateStr = user?.role === "ADMIN" ? params.get("date") || enteredOn : enteredOn;
  // 이벤트 활성 시간대 가드. 카드 버튼과 동일 기준(config.isAiJudgeOpen).
  // 진입 시점에 1회만 판정해 고정 — 매 렌더 재평가하면 23:59에 답변을
  // 타이핑하던 사용자가 자정을 넘는 순간 리다이렉트로 축출되고 작성 내용이 날아간다.
  const [isEventOpen] = useState(() => canUseAiJudge(user));

  const [loading, setLoading] = useState(true);
  const [loadProgress, setLoadProgress] = useState(8);
  const [loadError, setLoadError] = useState(false);
  const [inquiries, setInquiries] = useState([]);
  const [expenses, setExpenses] = useState([]);
  const [judgment, setJudgment] = useState(null);
  const [page, setPage] = useState(1);
  const [answers, setAnswers] = useState({}); // inquiryId → text
  const [reasons, setReasons] = useState({}); // inquiryId → 사유 칩(REASON_CHIPS 의 value)
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);
  const [petName, setPetName] = useState(null);
  // 부분 실패 후 retry 시 이미 POST 된 inquiry 재호출 방지. 백엔드도 answeredAt 가드가 있지만
  // 클라이언트도 skip 해 불필요한 round-trip 차단.
  const submittedRef = useRef(new Set());

  // 펫 이름 조회는 판정 조회와 분리해 실패·지연이 질문 화면을 막지 않게 한다.
  useEffect(() => {
    let cancelled = false;
    getActivePet()
      .then((pet) => {
        if (!cancelled && pet) setPetName(petDisplayName(pet));
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, []);

  // mount 시 fetch. 질문 행은 지출 저장과 같은 트랜잭션에서 템플릿 문구로 만들어지므로
  // 첫 조회에 잡힌다. 질문 없는 RED 지출이 보이면(저장 직후 아주 짧은 창, 또는 이 방식
  // 이전에 Gemini 가 실패해 질문이 아예 없는 옛 기록) 2s 간격으로 두 번만 더 물어보고,
  // 그래도 없으면 빈 화면 대신 그 사실을 알린다(questionPending). 옛 기록은 다시 열어도
  // 질문이 생기지 않으므로 오래 기다리게 하지 않는다.
  // reloadKey: 에러 화면의 "다시 시도"가 이 effect 를 재실행시키는 트리거.
  const [reloadKey, setReloadKey] = useState(0);
  const [waitingForQuestion, setWaitingForQuestion] = useState(false);
  const [questionPending, setQuestionPending] = useState(false);
  // needsConfirm: 판정 전 확인 창을 띄울지. null 은 아직 모름(이미 판정한 날짜인지 조회 중) —
  // 그동안은 "분석 중" 화면도 띄우지 않는다. confirmedDate: 확인 창에서 "판정하기"를 누른 날짜.
  const [needsConfirm, setNeedsConfirm] = useState(null);
  const [confirmedDate, setConfirmedDate] = useState(null);
  useEffect(() => {
    if (!isEventOpen) return;
    let cancelled = false;
    let attempts = 0;
    const MAX_RETRIES = 2;
    const RETRY_INTERVAL_MS = 2000;
    setLoading(true);
    setLoadProgress(8);
    setLoadError(false);
    setWaitingForQuestion(false);
    setQuestionPending(false);
    setNeedsConfirm(confirmedDate === dateStr ? false : null);
    setJudgment(null);
    setPage(1);
    setAnswers({});
    setReasons({});
    submittedRef.current.clear();
    const tryFetch = async (judgmentResult) => {
      if (cancelled) return;
      try {
        const [data, expenseData] = await Promise.all([
          listInquiriesByDate(dateStr),
          listExpensesByDate(dateStr),
        ]);
        if (cancelled) return;
        setExpenses(expenseData);
        // 질문 행이 있는 지출은 빼고 본다 — 같은 날 RED 가 둘인데 하나만 질문이 없는 경우도 잡는다.
        const asked = new Set(data.map((inq) => inq.expenseId));
        const redWithoutQuestion = expenseData.some(
          (e) => e.signalInitial === "RED" && !e.isRecurring && !e.reasonCategory && !asked.has(e.id)
        );
        if (redWithoutQuestion && attempts < MAX_RETRIES) {
          attempts++;
          setWaitingForQuestion(true);
          // 45% 에서 시작해 마지막 재시도에 95% 근처까지. 100% 는 결과가 났을 때만.
          setLoadProgress(45 + Math.round((attempts / MAX_RETRIES) * 50));
          setTimeout(() => tryFetch(judgmentResult), RETRY_INTERVAL_MS);
          return;
        }
        setQuestionPending(redWithoutQuestion);
        let pendingQuestions = data;
        if (judgmentResult?.status === "FINALIZED") {
          // 확정된 날은 사유를 다시 받지 않는다 — 답해도 그날 판정에는 반영되지 않는다
          pendingQuestions = [];
        } else if (judgmentResult?.status === "PENDING" && data.length === 0) {
          // 기다릴 사유가 없다(다른 화면에서 이미 답함) — 바로 확정한다
          const finalized = await finalizeDateJudgment(dateStr);
          if (cancelled) return;
          setJudgment(finalized);
        }
        setInquiries(pendingQuestions);
        setLoadProgress(100);
        setLoading(false);
      } catch {
        // 네트워크 단절 등 fetch 자체 실패 — "분석 중" 화면에 영원히 갇히지 않게
        // 에러 상태로 전환하고 탈출/재시도 경로를 준다.
        if (cancelled) return;
        setLoadError(true);
        setLoading(false);
      }
    };
    const start = async () => {
      try {
        setLoadProgress(20);
        const result = await startDateJudgment(dateStr);
        if (cancelled) return;
        setJudgment(result);
        setLoadProgress(45);
        tryFetch(result);
      } catch {
        if (cancelled) return;
        setLoadError(true);
        setLoading(false);
      }
    };
    // 판정 전 확인. 아직 판정하지 않은 날짜면 확인 창에서 "판정하기"를 눌러야 판정한다.
    // 이미 판정한 날짜는 결과를 다시 보는 것이라 묻지 않는다.
    const begin = async () => {
      if (confirmedDate === dateStr) {
        start();
        return;
      }
      try {
        const existing = await getDateJudgment(dateStr);
        if (cancelled) return;
        setNeedsConfirm(!existing);
        if (existing) start();
      } catch {
        if (cancelled) return;
        setNeedsConfirm(false);
        setLoadError(true);
        setLoading(false);
      }
    };
    begin();
    return () => {
      cancelled = true;
    };
  }, [dateStr, isEventOpen, reloadKey, confirmedDate]);

  // 질문을 넘기면 새 질문을 단계 표시부터 보이게 한다 — 스크롤이 아래에 남으면 사유 칩부터 보인다
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [page]);

  // 활성 시간대 밖이면 가계부로 돌려보낸다.
  if (!isEventOpen) {
    return <Navigate to="/wallet" replace />;
  }

  const total = inquiries.length;
  const judgedExpenseCount = expenses.length;
  const goPrev = () => setPage((p) => Math.max(1, p - 1));
  const goNext = () => setPage((p) => Math.min(total, p + 1));

  // 나가기는 늘 같은 날짜의 가계부로 돌아간다.
  const goDone = () => navigate(`/wallet?date=${dateStr}`);
  const goBack = goDone;

  // 칩을 고르거나 글을 적은 질문이 하나라도 있는지 — 건너뛰기 버튼 문구를 바꾼다.
  const hasTypedAnswer = inquiries.some(
    (inq) => (answers[inq.inquiryId] || "").trim() || reasons[inq.inquiryId]
  );

  // 답변 입력된 inquiry 들만 POST 한 뒤 판정을 확정한다. 확정 응답이 최종 판정·보상이다.
  // 「건너뛰고 판정 받기」도 같다 — 앞 질문에 적어 둔 답은 버리지 않고, 안 적은 질문만 건너뛴다
  // (1번에 답하고 2번에서 건너뛰면 1번 답까지 사라지던 문제). 아무것도 안 적었으면 1차 판정 그대로 확정된다.
  const finish = async () => {
    if (submitting) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      for (const inq of inquiries) {
        if (submittedRef.current.has(inq.inquiryId)) continue;
        const text = (answers[inq.inquiryId] || "").trim();
        const reason = reasons[inq.inquiryId] || null;
        if (!text && !reason) continue;
        await answerInquiry(inq.inquiryId, text, reason);
        submittedRef.current.add(inq.inquiryId);
      }
      setJudgment(await finalizeDateJudgment(dateStr));
      // 확정된 뒤에는 답해도 반영되지 않는다 — 아래 조회가 실패해도 질문이 남지 않게 먼저 비운다
      setInquiries([]);
      setPage(1);
      // 결과 화면은 맨 위(최종 판정 칩·요약)부터 보여 준다
      window.scrollTo(0, 0);
      const refreshedExpenses = await listExpensesByDate(dateStr);
      setExpenses(refreshedExpenses);
      setInquiries([]);
      setPage(1);
    } catch (e) {
      setSubmitError(e.message || "판정을 확정하지 못했어요");
    } finally {
      setSubmitting(false);
    }
  };

  const dateTitle = formatDateTitle(dateStr);
  const resultSignal = judgment?.signal || "GREEN";
  const resultStage = judgment?.status === "FINALIZED" ? "최종 판정" : "1차 판정";

  return (
    <div className="ja-page">
      <div className="ja hm">
        <header className="ja-header">
          <button type="button" className="ja-back" onClick={goBack} aria-label="가계부로 돌아가기">
            <Icon name="chevronLeft" size={21} />
          </button>
          <h1>{dateTitle}</h1>
          {user?.role === "ADMIN" && <span className="ja-admin">관리자 시연</span>}
        </header>

        {needsConfirm === null ? null : needsConfirm ? (
          <JudgeConfirmDialog onCancel={goBack} onConfirm={() => setConfirmedDate(dateStr)} />
        ) : loading ? (
          <main className="ja-state ja-loading">
            <div className="ja-title-block">
              <h2>{waitingForQuestion ? "질문을 준비하고 있어요" : "분석 중이에요"}</h2>
              <p>
                {waitingForQuestion
                  ? "평소보다 큰 지출이 있어서 물어볼 말을 고르는 중이에요. 조금만 더 기다려 주세요."
                  : "AI가 예외적인 지출을 살펴보고 있어요. 잠시만 기다려주세요."}
              </p>
              <div
                className="ja-progress"
                role="progressbar"
                aria-label="소비 판정 진행률"
                aria-valuemin="0"
                aria-valuemax="100"
                aria-valuenow={loadProgress}
              >
                <span style={{ width: `${loadProgress}%` }} />
              </div>
              <span className="ja-progress-label">{loadProgress}%</span>
            </div>
          </main>
        ) : loadError ? (
          <main className="ja-state ja-error-state">
            <div className="ja-title-block">
              <h2>질문을 불러오지 못했어요</h2>
              <p>네트워크 상태를 확인하고 다시 시도해 주세요.</p>
            </div>
            {expenses.length > 0 && (
              <div className="ja-expense-errors" aria-label="소비별 판정 결과">
                {expenses.map((expense) => {
                  const signal = expense.signalFinal || expense.signalInitial || "GRAY";
                  return (
                    <article key={expense.id} className="hm-card ja-error-card">
                      <div className="ja-error-card-head">
                        <div>
                          <strong>{expense.item || "지출"}</strong>
                          <span>{Number(expense.amount || 0).toLocaleString("ko-KR")}원</span>
                        </div>
                        <SignalChip signal={signal} />
                      </div>
                      <div className="ja-error-reason">
                        <span>판정 이유</span>
                        <p>{judgmentReason(expense, signal)}</p>
                      </div>
                    </article>
                  );
                })}
              </div>
            )}
            <div className="ja-actions ja-error-actions">
              <button type="button" className="hm-btn hm-btn--dark ja-primary" onClick={() => setReloadKey((k) => k + 1)}>
                다시 시도
              </button>
              <button type="button" className="ja-btn-line" onClick={goBack}>돌아가기</button>
            </div>
          </main>
        ) : total === 0 ? (
          <main className="ja-state ja-result-state">
            <div className="ja-status-row">
              <SignalChip
                signal={resultSignal}
                size="lg"
                label={`${resultStage} · ${SIGNAL_STATUS[resultSignal] || SIGNAL_STATUS.GRAY}`}
              />
            </div>
            <section className="hm-card ja-summary-card">
              <h2>
                {judgedExpenseCount === 0
                  ? "이날은 지출이 없어요"
                  : user?.role === "ADMIN" && dateStr !== enteredOn
                    ? "판정이 끝났어요"
                    : "오늘 판정이 끝났어요"}
              </h2>
              <p>
                {judgedExpenseCount === 0
                  ? "보상은 아래에서 확인해 주세요."
                  : getResultSummary(judgment, expenses)}
              </p>
            </section>
            {questionPending && (
              <p className="ja-question-pending" role="status">
                질문이 없는 초과 지출이 있어요. 사유를 남기고 싶으면 가계부에서 그 지출을 고쳐 주세요.
              </p>
            )}
            <JudgmentResults expenses={expenses} judgment={judgment} />
            <button type="button" className="hm-btn hm-btn--dark hm-btn--block ja-primary ja-done" onClick={goDone}>
              가계부로 돌아가기 <Icon name="arrowRight" size={17} />
            </button>
          </main>
        ) : (
          <main className="ja-state ja-question-state">
            <ol className="ja-steps" aria-label="판정 단계">
              <li className="ja-step ja-step--done">
                <span className="ja-step-dot"><Icon name="check" size={13} /></span>
                <span>1차 판정</span>
              </li>
              <li className="ja-step ja-step--current" aria-current="step">
                <span className="ja-step-dot" />
                <span>질문</span>
              </li>
              <li className="ja-step ja-step--upcoming">
                <span className="ja-step-dot" />
                <span>최종 판정</span>
              </li>
            </ol>

            {(() => {
              const inq = inquiries[page - 1];
              const expense = expenses.find((item) => item.id === inq.expenseId);
              const statType = expense?.statType;
              const groupName = statType ? STAT_LABELS[statType] : null;
              const firstSignal = statType
                ? judgment?.groupJudgments?.[statType]?.signal || judgment?.signal || "GRAY"
                : judgment?.signal || "GRAY";
              // 카테고리 이름은 질문 응답에만 있다(지출 응답에는 categoryId 뿐). 시간은 지출 응답에서.
              const categoryNames = [
                formatTime(expense),
                inq.parentCategoryName,
                inq.categoryName,
              ].filter(Boolean);
              const itemName = expense?.item || inq.item || "지출";
              const amount = expense?.amount ?? inq.amount;
              const categoryName = `${inq.parentCategoryName ?? ""} ${inq.categoryName ?? ""}`;
              const selectedReason = reasons[inq.inquiryId];
              const answer = answers[inq.inquiryId] || "";
              const isLastQuestion = page === total;

              return (
                <>
                  <div className="ja-context-row">
                    <span>{groupName ? `${groupName} 1차 판정 · 확인 필요` : "1차 판정 · 확인 필요"}</span>
                    <SignalChip signal={firstSignal} />
                  </div>

                  {total > 1 && (
                    <nav className="ja-pager" aria-label="질문 이동">
                      {/* 주 버튼 「다음 질문」과 화면 낭독 이름이 겹치지 않게 「보기」를 붙인다 */}
                      <button type="button" onClick={goPrev} disabled={page === 1} aria-label="이전 질문 보기">
                        <Icon name="chevronLeft" size={18} />
                      </button>
                      <span>{page} / {total}</span>
                      <button type="button" onClick={goNext} disabled={page === total} aria-label="다음 질문 보기">
                        <Icon name="chevronRight" size={18} />
                      </button>
                    </nav>
                  )}

                  <section className="hm-card ja-expense-card" aria-label="질문이 나온 지출">
                    <span className="ja-category-icon"><Icon name={categoryIcon(categoryName)} size={21} /></span>
                    <span className="ja-expense-copy">
                      <strong title={itemName}>{itemName}</strong>
                      <small>{categoryNames.join(" · ")}</small>
                    </span>
                    <strong className="ja-expense-amount">{Number(amount || 0).toLocaleString("ko-KR")}원</strong>
                  </section>

                  <section className="ja-question-block" aria-label="오늘의 질문">
                    <span className="ja-paw-tile"><Icon name="paw" size={20} /></span>
                    <div className="ja-speech-bubble">
                      {petName && <strong>{petName}</strong>}
                      <p>{inq.question}</p>
                    </div>
                  </section>

                  <section className="ja-answer-section">
                    <h2>지출 사유 선택</h2>
                    <div className="ja-reason-chips" role="group" aria-label="이유 고르기">
                      {REASON_CHIPS.map((chip) => {
                        const selected = selectedReason === chip.value;
                        return (
                          <button
                            key={chip.value}
                            type="button"
                            className={`ja-reason-chip${selected ? " ja-reason-chip--selected" : ""}`}
                            aria-pressed={selected}
                            onClick={() => setReasons((previous) => ({
                              ...previous,
                              [inq.inquiryId]: selected ? null : chip.value,
                            }))}
                          >
                            {selected && <Icon name="check" size={14} />}
                            {chip.label}
                          </button>
                        );
                      })}
                    </div>
                    <label className="ja-answer-wrap">
                      <span className="ja-sr-only">사유 직접 적기</span>
                      <textarea
                        aria-label="사유 직접 적기"
                        rows={4}
                        maxLength={ANSWER_MAX}
                        placeholder="가볍게 적어도 돼요 (선택)"
                        value={answer}
                        onChange={(event) => setAnswers((previous) => ({
                          ...previous,
                          [inq.inquiryId]: event.target.value,
                        }))}
                      />
                      <span className="ja-answer-count">{answer.length}/{ANSWER_MAX}</span>
                    </label>
                  </section>

                  <p className="ja-rule-note"><Icon name="info" size={16} />{RULE_TEXT.questionGuidance()}</p>
                  <div className="ja-actions">
                    {submitError && <p className="ja-submit-error" role="alert">{submitError}</p>}
                    <button
                      type="button"
                      className="hm-btn hm-btn--dark hm-btn--block ja-primary"
                      onClick={isLastQuestion ? finish : goNext}
                      disabled={submitting}
                    >
                      {submitting ? "판정하는 중…" : isLastQuestion ? "답하고 판정 받기" : "다음 질문"}
                    </button>
                    <button type="button" className="ja-skip" onClick={finish} disabled={submitting}>
                      {hasTypedAnswer ? "남은 질문 건너뛰고 판정 받기" : "건너뛰고 판정 받기"}
                    </button>
                  </div>
                  <p className="ja-leave-note">나가도 오늘 안에는 이어서 답할 수 있어요. 자정까지 답하지 않으면 1차 판정 그대로 보상을 받아요.</p>
                </>
              );
            })()}
          </main>
        )}
      </div>
    </div>
  );
}

// 판정 전 확인 창. 판정은 하루에 한 번이라 실수로 시작하지 않게 한 번 묻는다.
// Esc 는 "돌아가기"와 같다. 바깥을 눌러서는 닫히지 않는다.
function JudgeConfirmDialog({ onCancel, onConfirm }) {
  const titleId = useId();
  const textId = useId();
  const dialogRef = useRef(null);

  // 창이 떠 있는 동안 뒤 화면이 스크롤되지 않게 한다.
  useEffect(() => {
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = previous;
    };
  }, []);

  const onKeyDown = (event) => {
    if (event.key === "Escape") {
      onCancel();
      return;
    }
    // Tab 이 창 밖으로 나가지 않게 창 안에서만 돈다.
    if (event.key !== "Tab") return;
    const focusable = dialogRef.current?.querySelectorAll("button");
    if (!focusable || focusable.length === 0) return;
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  };

  return (
    <div className="ja-confirm-backdrop">
      <div
        ref={dialogRef}
        className="hm-card ja-confirm-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={textId}
        onKeyDown={onKeyDown}
      >
        <h2 id={titleId}>하루에 한 번만 판정할 수 있어요</h2>
        <p id={textId}>지금 판정할까요? 판정 뒤에 지출을 고쳐도 오늘 보상은 바뀌지 않아요.</p>
        <div className="ja-confirm-actions">
          <button type="button" className="ja-btn-line" onClick={onCancel}>돌아가기</button>
          <button type="button" className="hm-btn hm-btn--dark ja-primary" onClick={onConfirm} autoFocus>
            판정하기
          </button>
        </div>
      </div>
    </div>
  );
}

export default WalletAnalysisPage;
