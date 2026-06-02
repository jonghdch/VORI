import { useCallback, useEffect, useState } from "react";
import { getAiLogs } from "../../api/admin";
import "./AdminCommon.css";

const PAGE_SIZE = 20;

// reason_category enum → 한국어 라벨
const REASON_LABELS = {
  CEREMONY: "경조사",
  EMERGENCY: "긴급",
  SOCIAL: "사교",
  SELF_INVEST: "자기투자",
  IMPULSE: "충동",
  ETC: "기타",
};

const REASON_TABS = [
  { key: null, label: "전체" },
  ...Object.entries(REASON_LABELS).map(([key, label]) => ({ key, label })),
];

function fmtDateTime(iso) {
  if (!iso) return "—";
  return iso.replace("T", " ").slice(0, 16);
}

function AiLogsPage() {
  const [reason, setReason] = useState(null);
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    getAiLogs({ page, size: PAGE_SIZE, reason })
      .then((res) => setData(res))
      .catch((e) => setError(e.message || "불러오기 실패"))
      .finally(() => setLoading(false));
  }, [page, reason]);

  useEffect(() => {
    load();
  }, [load]);

  const changeReason = (next) => {
    setReason(next);
    setPage(0);
  };

  const logs = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;

  return (
    <div className="adm-page">
      <h1 className="adm-title">AI 대사 로그</h1>
      <p className="adm-sub">
        AI가 지출 사유를 물은 기록과 사용자 답변·분류 결과입니다.{" "}
        {!loading && !error && (
          <span className="adm-accent">총 {totalElements.toLocaleString("ko-KR")}건</span>
        )}
      </p>

      <div className="adm-toolbar">
        <div className="adm-tabs" role="tablist" aria-label="분류 필터">
          {REASON_TABS.map((tab) => (
            <button
              key={tab.label}
              type="button"
              role="tab"
              aria-selected={reason === tab.key}
              className={reason === tab.key ? "adm-tab adm-tab--active" : "adm-tab"}
              onClick={() => changeReason(tab.key)}
            >
              {tab.label}
            </button>
          ))}
        </div>
        <button type="button" className="adm-btn" onClick={load} disabled={loading}>
          새로고침
        </button>
      </div>

      <div className="adm-card">
        {error ? (
          <div className="adm-state adm-state--error">
            <span>{error}</span>
            <button type="button" onClick={load}>
              다시 시도
            </button>
          </div>
        ) : loading ? (
          <div className="adm-state">불러오는 중…</div>
        ) : logs.length === 0 ? (
          <div className="adm-state">표시할 로그가 없습니다.</div>
        ) : (
          <div className="adm-table-wrap">
            <table className="adm-table">
              <thead>
                <tr>
                  <th>질문 시각</th>
                  <th className="num">유저</th>
                  <th>질문 / 답변</th>
                  <th>분류</th>
                  <th>보정</th>
                </tr>
              </thead>
              <tbody>
                {logs.map((l) => (
                  <tr key={l.id}>
                    <td className="num">{fmtDateTime(l.askedAt)}</td>
                    <td className="num">{l.userId}</td>
                    <td>
                      <span className="adm-cell-main">Q. {l.question}</span>
                      <span className="adm-cell-sub">
                        {l.answerText ? `A. ${l.answerText}` : "미답변"}
                      </span>
                    </td>
                    <td>
                      {l.reasonCategory ? (
                        <span className="adm-badge">
                          {REASON_LABELS[l.reasonCategory] || l.reasonCategory}
                        </span>
                      ) : (
                        <span className="adm-cell-sub">—</span>
                      )}
                    </td>
                    <td>
                      <span
                        className={
                          l.signalAdjusted ? "adm-badge adm-badge--green" : "adm-badge adm-badge--gray"
                        }
                      >
                        {l.signalAdjusted ? "보정됨" : "유지"}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {!loading && !error && totalPages > 1 && (
        <div className="adm-pager">
          <button type="button" onClick={() => setPage((p) => Math.max(p - 1, 0))} disabled={page <= 0}>
            이전
          </button>
          <span className="adm-pager-info">
            {page + 1} / {totalPages}
          </span>
          <button
            type="button"
            onClick={() => setPage((p) => Math.min(p + 1, totalPages - 1))}
            disabled={page >= totalPages - 1}
          >
            다음
          </button>
        </div>
      )}
    </div>
  );
}

export default AiLogsPage;
