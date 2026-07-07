import { useEffect, useRef, useState } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { toIsoDate } from "./utils";
import { answerInquiry, listInquiriesByDate } from "../../api/inquiries";
import "./WalletEntry.css";

// 소비 분석 — /wallet 의 ledger-ai-card 에서 오후 8시~자정 이벤트로 진입하는 독립 페이지.
// (더 이상 가계부 작성 위저드의 단계가 아니다.)
// - 백엔드가 z-score 로 anomaly 감지한 expense 만 AI 질문 생성됨 (비동기).
// - 질문이 없으면 안내 + "완료" 만 표시.
// - 있으면 페이지네이션으로 한 건씩 답변. "다음에 할게요" 누르면 답변 안 한 채로 닫음.
// - 오후 8시~자정 밖에서 직접 URL 로 들어오면 /wallet 로 돌려보낸다.
function WalletAnalysisPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const dateStr = params.get("date") || toIsoDate();
  // 이벤트 활성 시간대(오후 8시~자정) 가드. 카드 버튼과 동일 기준.
  // 진입 시점에 1회만 판정해 고정 — 매 렌더 재평가하면 23:59에 답변을
  // 타이핑하던 사용자가 자정을 넘는 순간 리다이렉트로 축출되고 작성 내용이 날아간다.
  const [isEventOpen] = useState(() => new Date().getHours() >= 20);

  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [inquiries, setInquiries] = useState([]);
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
    setLoadError(false);
    const tryFetch = async () => {
      if (cancelled) return;
      try {
        const data = await listInquiriesByDate(dateStr);
        if (cancelled) return;
        if (data.length === 0 && attempts < MAX_RETRIES) {
          attempts++;
          setTimeout(tryFetch, RETRY_INTERVAL_MS);
          return;
        }
        setInquiries(data);
        setLoading(false);
      } catch {
        // 네트워크 단절 등 fetch 자체 실패 — "분석 중" 화면에 영원히 갇히지 않게
        // 에러 상태로 전환하고 탈출/재시도 경로를 준다.
        if (cancelled) return;
        setLoadError(true);
        setLoading(false);
      }
    };
    tryFetch();
    return () => {
      cancelled = true;
    };
  }, [dateStr, isEventOpen, reloadKey]);

  // 활성 시간대(오후 8시~자정) 밖이면 가계부로 돌려보낸다.
  if (!isEventOpen) {
    return <Navigate to="/wallet" replace />;
  }

  const total = inquiries.length;
  const goPrev = () => setPage((p) => Math.max(1, p - 1));
  const goNext = () => setPage((p) => Math.min(total, p + 1));

  // 이벤트 종료/닫기는 모두 가계부로 복귀.
  const goDone = () => navigate("/wallet");
  const goBack = () => navigate("/wallet");

  // 답변 입력된 inquiry 들만 POST. 끝나면 Step 3 로 이동.
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
      goDone();
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
        {loading ? (
          <div className="ledger-center-y">
            <div className="ledger-title-block ledger-title-block-center">
              <h1 className="ledger-title">분석 중이에요</h1>
              <p className="ledger-subtitle">
                AI가 예외적인 지출을 살펴보고 있어요. 잠시만 기다려주세요.
              </p>
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
          // ───── 분석할 항목 없음 — 안내 + 닫기만 ─────
          <div className="ledger-center-y">
            <div className="ledger-title-block">
              <h1 className="ledger-title">예외적인 지출이 없어요</h1>
              <p className="ledger-subtitle">
                평소와 비슷한 패턴이라 오늘은 분석할 지출이 없어요.
              </p>
            </div>
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
                오늘의 소비를 분석해볼게요
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

export default WalletAnalysisPage;
