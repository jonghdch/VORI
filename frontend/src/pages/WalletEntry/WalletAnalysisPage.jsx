import { useEffect, useRef, useState } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { toIsoDate } from "./utils";
import { answerInquiry, listInquiriesByDate } from "../../api/inquiries";
import { listExpensesByDate } from "../../api/ledger";
import { startDateJudgment } from "../../api/dailyJudgment";
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

  // mount 시 fetch. Gemini 비동기 (질문 생성에 보통 5~10s) 라 즉시 응답엔 비어있음.
  // 2s 간격으로 최대 6번 polling — 첫 호출 + 5회 retry = 최대 10s 대기.
  // reloadKey: 에러 화면의 "다시 시도"가 이 effect 를 재실행시키는 트리거.
  const [reloadKey, setReloadKey] = useState(0);
  useEffect(() => {
    if (!isEventOpen) return;
    let cancelled = false;
    let attempts = 0;
    const MAX_RETRIES = 5;
    const RETRY_INTERVAL_MS = 2000;
    setLoading(true);
    setLoadProgress(8);
    setLoadError(false);
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
        if (data.length === 0 && expenseData.some((e) => e.signalFinal === "RED" && !e.reasonCategory && !e.isRecurring) && attempts < MAX_RETRIES) {
          attempts++;
          setLoadProgress(45 + attempts * 8);
          setTimeout(tryFetch, RETRY_INTERVAL_MS);
          return;
        }
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
    start();
    return () => {
      cancelled = true;
    };
  }, [dateStr, isEventOpen, reloadKey]);

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
        {loading ? (
          <div className="ledger-center-y">
            <div className="ledger-title-block ledger-title-block-center">
              <h1 className="ledger-title">분석 중이에요</h1>
              <p className="ledger-subtitle">
                AI가 예외적인 지출을 살펴보고 있어요. 잠시만 기다려주세요.
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
                  가계부로 돌아가기
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
            <div className="ledger-title-block">
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
                    ? "모든 지출이 초록으로 분류됐어요. 항목별 판정 근거를 확인해 보세요."
                    : dailySignal === "GRAY"
                      ? "주황 지출이 포함되어 있어요. 하루 색상은 빨강, 주황, 초록 순으로 가장 주의가 필요한 지출을 따라요."
                      : "빨강 지출이 포함되어 있어요. 아래 항목별 금액 비교와 사유를 확인해 보세요."}
              </p>
            </div>
            {judgment?.alreadyJudged && <p className="ledger-hint">이미 판정한 날짜예요. 현재 지출의 결과와 기존 지급 내역을 보여드려요.</p>}
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
