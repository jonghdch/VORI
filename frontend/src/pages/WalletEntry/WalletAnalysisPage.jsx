import { useEffect, useId, useRef, useState } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { toIsoDate } from "./utils";
import { answerInquiry, listInquiriesByDate } from "../../api/inquiries";
import { listExpensesByDate } from "../../api/ledger";
import { getDateJudgment, startDateJudgment } from "../../api/dailyJudgment";
import JudgmentResults, { judgmentReason } from "./JudgmentResults";
import { canUseAiJudge } from "../../config";
import "./WalletEntry.css";

// 소비 분석 — /wallet 의 ledger-ai-card 에서 저녁 이벤트로 진입하는 독립 페이지.
// (더 이상 가계부 작성 위저드의 단계가 아니다.)
// - 백엔드가 z-score 로 anomaly 감지한 expense 만 AI 질문 생성됨 (비동기).
// - 질문이 없으면 안내 + "완료" 만 표시.
// - 있으면 페이지네이션으로 한 건씩 답변. "다음에 할게요" 누르면 답변 안 한 채로 닫음.
// - 활성 시간대 밖에서 직접 URL 로 들어오면 /wallet 로 돌려보낸다.
//   열리는 시각은 config.AI_ACTIVE_FROM_HOUR (기본 20시).
function WalletAnalysisPage({ user }) {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const dateStr = user?.role === "ADMIN" ? params.get("date") || toIsoDate() : toIsoDate();
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
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);
  // 부분 실패 후 retry 시 이미 POST 된 inquiry 재호출 방지. 백엔드도 answeredAt 가드가 있지만
  // 클라이언트도 skip 해 불필요한 round-trip 차단.
  const submittedRef = useRef(new Set());

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
    submittedRef.current.clear();
    const tryFetch = async () => {
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
          setTimeout(tryFetch, RETRY_INTERVAL_MS);
          return;
        }
        setQuestionPending(redWithoutQuestion);
        setInquiries(data);
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
        tryFetch();
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

  // 활성 시간대 밖이면 가계부로 돌려보낸다.
  if (!isEventOpen) {
    return <Navigate to="/wallet" replace />;
  }

  const total = inquiries.length;
  const dailySignal = getDailySignal(expenses);
  const judgedExpenseCount = expenses.length;
  const goPrev = () => setPage((p) => Math.max(1, p - 1));
  const goNext = () => setPage((p) => Math.min(total, p + 1));

  // 이벤트 종료/닫기는 모두 가계부로 복귀.
  const goDone = () => navigate(`/wallet?date=${dateStr}`);
  const goBack = goDone;

  // 답변 입력된 inquiry 들만 POST. 완료 뒤 갱신된 소비별 판정 결과를 보여준다.
  const submitAll = async () => {
    if (submitting) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      for (const inq of inquiries) {
        if (submittedRef.current.has(inq.inquiryId)) continue;
        const text = (answers[inq.inquiryId] || "").trim();
        if (!text) continue;
        await answerInquiry(inq.inquiryId, text);
        submittedRef.current.add(inq.inquiryId);
      }
      const refreshedExpenses = await listExpensesByDate(dateStr);
      setExpenses(refreshedExpenses);
      setInquiries([]);
      setPage(1);
    } catch (e) {
      setSubmitError(e.message || "답변 저장 중 오류가 발생했어요");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="ledger">
      <header className="ledger-entry-header">
        <button
          type="button"
          className="ledger-logo-btn"
          onClick={() => navigate("/home")}
          aria-label="VORI 홈으로"
        >
          VORI
        </button>
      </header>

      <main className="ledger-entry-main">
        <p className="ledger-subtitle">{dateStr} 소비 판정{user?.role === "ADMIN" ? " · 관리자 시연" : ""}</p>
        {needsConfirm === null ? null : needsConfirm ? (
          <JudgeConfirmDialog onCancel={goBack} onConfirm={() => setConfirmedDate(dateStr)} />
        ) : loading ? (
          <div className="ledger-center-y">
            <div className="ledger-title-block ledger-title-block-center">
              <h1 className="ledger-title">{waitingForQuestion ? "질문을 준비하고 있어요" : "분석 중이에요"}</h1>
              <p className="ledger-subtitle">
                {waitingForQuestion
                  ? "평소보다 큰 지출이 있어서 물어볼 말을 고르는 중이에요. 조금만 더 기다려 주세요."
                  : "AI가 예외적인 지출을 살펴보고 있어요. 잠시만 기다려주세요."}
              </p>
              <div
                className="ledger-judgment-progress"
                role="progressbar"
                aria-label="소비 판정 진행률"
                aria-valuemin="0"
                aria-valuemax="100"
                aria-valuenow={loadProgress}
              >
                <span style={{ width: `${loadProgress}%` }} />
              </div>
              <span className="ledger-judgment-progress-label">{loadProgress}%</span>
            </div>
          </div>
        ) : loadError ? (
          // ───── 질문 조회 실패 — 갇히지 않게 재시도/복귀 제공 ─────
          <div className="ledger-center-y">
            <div className="ledger-title-block">
              <h1 className="ledger-title">질문을 불러오지 못했어요</h1>
              <p className="ledger-subtitle">
                네트워크 상태를 확인하고 다시 시도해주세요.
              </p>
            </div>
            {expenses.length > 0 && (
              <div className="ledger-judgment-list" aria-label="소비별 판정 결과">
                {expenses.map((expense) => {
                  const signal = expense.signalFinal || expense.signalInitial || "GRAY";
                  return (
                    <article key={expense.id} className="ledger-judgment-card">
                      <div className="ledger-judgment-card-head">
                        <div>
                          <strong>{expense.item || "지출"}</strong>
                          <span>{Number(expense.amount || 0).toLocaleString("ko-KR")}원</span>
                        </div>
                        <span className={`ledger-signal-result ledger-signal-result--${signal.toLowerCase()}`}>
                          <span className="ledger-signal-dot" aria-hidden />
                          {signalLabel(signal)}
                        </span>
                      </div>
                      <div className="ledger-judgment-reason">
                        <span>판정 이유</span>
                        <p>{judgmentReason(expense, signal)}</p>
                      </div>
                    </article>
                  );
                })}
              </div>
            )}
            <div className="ledger-actions">
              <div className="ledger-actions-row">
                <button type="button" className="ledger-back" onClick={goBack}>
                  돌아가기
                </button>
                <button
                  type="button"
                  className="ledger-next"
                  onClick={() => setReloadKey((k) => k + 1)}
                >
                  다시 시도
                </button>
              </div>
            </div>
          </div>
        ) : total === 0 ? (
          // ───── AI 질문 없음 — 무지출 또는 오늘 소비의 최종 신호 안내 ─────
          <div className="ledger-center-y">
            <div className="ledger-title-block ledger-title-block--result">
              <div className={`ledger-signal-result ledger-signal-result--${dailySignal.toLowerCase()}`}>
                <span className="ledger-signal-dot" aria-hidden />
                <strong>{signalLabel(dailySignal)}</strong>
              </div>
              <h1 className="ledger-title">
                {judgedExpenseCount === 0 ? "선택한 날짜에는 지출이 없습니다" : "소비 판정이 완료됐어요"}
              </h1>
              <p className="ledger-subtitle">
                {judgedExpenseCount === 0
                  ? "지출이 없어 초록으로 표시했어요. 지급 보상은 아래에서 확인하세요."
                  : dailySignal === "GREEN"
                    ? "모든 지출이 초록으로 분류됐어요. 항목별로 판정 이유를 확인해 보세요."
                    : dailySignal === "GRAY"
                      ? "주황 지출이 포함되어 있어요. 하루 색상은 빨강, 주황, 초록 순으로 가장 주의가 필요한 지출을 따라요."
                      : "빨강 지출이 포함되어 있어요. 아래 항목별 금액 비교와 사유를 확인해 보세요."}
              </p>
            </div>
            {questionPending && (
              <p className="ledger-hint">
                질문이 없는 빨강 지출이 있어요. 아래 항목별 판정을 확인하고, 사유를 남기고 싶으면 가계부에서 그 지출을 수정해 주세요.
              </p>
            )}
            <JudgmentResults expenses={expenses} judgment={judgment} />
            <div className="ledger-actions">
              <div className="ledger-actions-row">
                <button type="button" className="ledger-next" onClick={goDone}>
                  가계부로 돌아가기
                </button>
              </div>
            </div>
          </div>
        ) : (
          // ───── 질문 있음 — 페이지네이션으로 한 건씩 ─────
          <>
            <div className="ledger-title-block">
              <h1 className="ledger-title">
                선택한 날짜의 소비를 분석해볼게요
                <span className="ledger-info-wrap">
                  <button
                    type="button"
                    className="ledger-info-icon"
                    aria-label="안내"
                  >
                    i
                  </button>
                  <span className="ledger-info-tooltip" role="tooltip">
                    <span className="ledger-info-card-icon" aria-hidden>i</span>
                    <span className="ledger-info-tooltip-text">
                      <span>예외적인 지출이 있으면 AI가 질문해요</span>
                      <span>하루가 지나면 사라집니다</span>
                    </span>
                  </span>
                </span>
              </h1>
              <p className="ledger-subtitle">
                예외적인 지출이 있어 어떤 이유로 지출하게 되었는지 작성해주세요
              </p>
            </div>

            <div className="ledger-pager">
              <button
                type="button"
                className="ledger-pager-btn"
                onClick={goPrev}
                disabled={page === 1}
                aria-label="이전"
              >
                {"<"}
              </button>
              <span className="ledger-pager-count">{page} / {total}</span>
              <button
                type="button"
                className="ledger-pager-btn"
                onClick={goNext}
                disabled={page === total}
                aria-label="다음"
              >
                {">"}
              </button>
            </div>

            {/* 분석 대상 expense (읽기 전용) */}
            {(() => {
              const inq = inquiries[page - 1];
              return (
                <>
                  <div className="ledger-entry-row ledger-row-readonly">
                    <div className="ledger-row-head">
                      <span className="ledger-row-num">
                        {String(page).padStart(2, "0")}
                      </span>
                      <span className="ledger-chip ledger-chip-readonly">
                        {paymentLabel(inq.paymentMethod)}
                      </span>
                      <span className="ledger-row-spacer" />
                      <span className="ledger-chip ledger-chip-readonly ledger-chip-auto">
                        {inq.parentCategoryName
                          ? `${inq.parentCategoryName} · ${inq.categoryName}`
                          : inq.categoryName || "기타"}
                      </span>
                    </div>
                    <div className="ledger-row-field">
                      <span className="ledger-row-label">내역</span>
                      <input
                        type="text"
                        className="ledger-row-input"
                        value={inq.item || ""}
                        disabled
                      />
                    </div>
                    <div className="ledger-row-field">
                      <span className="ledger-row-label">금액</span>
                      <div className="ledger-row-input-wrap">
                        <input
                          type="text"
                          className="ledger-row-input"
                          value={
                            inq.amount != null
                              ? Number(inq.amount).toLocaleString("ko-KR")
                              : ""
                          }
                          disabled
                        />
                        <span className="ledger-row-unit">원</span>
                      </div>
                    </div>
                  </div>

                  <div className="ledger-qa-card">
                    <div className="ledger-qa-question">
                      <strong>Q.</strong> {inq.question}
                    </div>
                    <textarea
                      className="ledger-qa-answer"
                      rows={10}
                      value={answers[inq.inquiryId] || ""}
                      onChange={(e) =>
                        setAnswers((prev) => ({
                          ...prev,
                          [inq.inquiryId]: e.target.value,
                        }))
                      }
                    />
                  </div>
                </>
              );
            })()}

            <div className="ledger-actions">
              {submitError && <p className="ledger-hint">{submitError}</p>}
              <button
                type="button"
                className="ledger-skip-link"
                onClick={goDone}
                disabled={submitting}
              >
                다음에 할게요
              </button>
              <div className="ledger-actions-row">
                <button
                  type="button"
                  className="ledger-back"
                  onClick={goBack}
                  disabled={submitting}
                >
                  돌아가기
                </button>
                <button
                  type="button"
                  className="ledger-next"
                  onClick={submitAll}
                  disabled={submitting}
                >
                  {submitting ? "저장 중…" : "완료"}
                </button>
              </div>
            </div>
          </>
        )}
      </main>
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
    // Tab 이 창 밖(뒤 화면의 로고 버튼)으로 나가지 않게 창 안에서만 돈다.
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
    <div className="ledger-confirm-backdrop">
      <div
        ref={dialogRef}
        className="ledger-confirm-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={textId}
        onKeyDown={onKeyDown}
      >
        <h2 id={titleId} className="ledger-confirm-title">하루에 한 번 판정할 수 있습니다.</h2>
        <p id={textId} className="ledger-confirm-text">판정 시작할까요?</p>
        <div className="ledger-confirm-actions">
          <button type="button" className="ledger-back" onClick={onCancel}>
            돌아가기
          </button>
          <button type="button" className="ledger-next" onClick={onConfirm} autoFocus>
            판정하기
          </button>
        </div>
      </div>
    </div>
  );
}

function paymentLabel(pm) {
  switch (pm) {
    case "CASH": return "현금";
    case "DEBIT": return "체크카드";
    case "CREDIT": return "신용카드";
    case "TRANSFER": return "계좌이체";
    case "MOBILE_PAY": return "모바일페이";
    default: return "결제수단";
  }
}

function getDailySignal(expenses) {
  if (!expenses || expenses.length === 0) return "GREEN";
  const rank = { GREEN: 1, GRAY: 2, RED: 3 };
  return expenses.reduce((worst, expense) => {
    const signal = expense.signalFinal || expense.signalInitial || "GRAY";
    return rank[signal] > rank[worst] ? signal : worst;
  }, "GREEN");
}

function signalLabel(signal) {
  if (signal === "GREEN") return "초록 · 절약";
  if (signal === "RED") return "빨강 · 과소비";
  return "주황 · 보통";
}

export default WalletAnalysisPage;
