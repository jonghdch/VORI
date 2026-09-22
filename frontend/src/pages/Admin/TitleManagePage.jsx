import { useCallback, useEffect, useState } from "react";
import {
  createTitle,
  deleteTitle,
  listAdminTitles,
  setTitleEnabled,
  updateTitle,
} from "../../api/admin";
import RefreshButton from "./RefreshButton";
import "./AdminCommon.css";
import "./TitleManagePage.css";

// 판정 지표 — 백엔드 TitleMetricType 과 1:1. 새 지표를 추가하면 여기도 같이 고친다.
const METRICS = [
  { value: "TOTAL_SAVED", label: "누적 절약액(원)" },
  { value: "EXPENSE_COUNT", label: "지출 기록 수" },
  { value: "GOALS_ACHIEVED", label: "절약 목표 달성 수" },
  { value: "PETS_RELEASED", label: "펫 분양 수" },
  { value: "S_TIER_PETS", label: "S등급 펫 획득 수" },
  { value: "AI_ANSWERS", label: "AI 질문 답변 수" },
  { value: "RECEIPT_SCANS", label: "영수증 인식 성공 수" },
  { value: "LOGIN_COUNT", label: "로그인 횟수" },
];
const METRIC_LABEL = Object.fromEntries(METRICS.map((m) => [m.value, m.label]));

const EMPTY_FORM = {
  code: "",
  name: "",
  description: "",
  metricType: "EXPENSE_COUNT",
  threshold: "10",
  enabled: true,
  sortOrder: "100",
};

const fmtDate = (iso) => (iso ? iso.replace("T", " ").slice(0, 16) : "—");

function TitleManagePage() {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // editingId 가 null 이면 생성 모드, 값이 있으면 그 칭호 수정 모드. 폼은 하나만 쓴다.
  const [editingId, setEditingId] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState(null);
  const [notice, setNotice] = useState(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    listAdminTitles()
      .then(setRows)
      .catch((e) => setError(e.message || "불러오기 실패"))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const setField = (k, v) => setForm((f) => ({ ...f, [k]: v }));

  const startEdit = (t) => {
    setEditingId(t.id);
    setForm({
      code: t.code,
      name: t.name,
      description: t.description,
      metricType: t.metricType,
      threshold: String(t.threshold),
      enabled: t.enabled,
      sortOrder: String(t.sortOrder),
    });
    setFormError(null);
    setNotice(null);
  };

  const cancelEdit = () => {
    setEditingId(null);
    setForm(EMPTY_FORM);
    setFormError(null);
  };

  const submit = (e) => {
    e.preventDefault();
    setFormError(null);
    setNotice(null);
    if (!editingId && !form.code.trim()) return setFormError("코드를 입력해주세요.");
    if (!form.name.trim()) return setFormError("칭호 이름을 입력해주세요.");
    if (!form.description.trim()) return setFormError("조건 설명을 입력해주세요.");
    const threshold = Number(form.threshold);
    if (!Number.isInteger(threshold) || threshold < 1) return setFormError("목표치는 1 이상의 정수여야 합니다.");
    const sortOrder = Number(form.sortOrder);
    if (!Number.isInteger(sortOrder) || sortOrder < 0) return setFormError("정렬 순서는 0 이상의 정수여야 합니다.");

    const body = {
      code: editingId ? undefined : form.code.trim(),
      name: form.name.trim(),
      description: form.description.trim(),
      metricType: form.metricType,
      threshold,
      enabled: form.enabled,
      sortOrder,
    };
    setSubmitting(true);
    (editingId ? updateTitle(editingId, body) : createTitle(body))
      .then((saved) => {
        setNotice(editingId ? `'${saved.name}' 수정됨` : `'${saved.name}' 생성됨`);
        cancelEdit();
        load();
      })
      .catch((err) => setFormError(err.message || "저장 실패"))
      .finally(() => setSubmitting(false));
  };

  const onToggle = (t) => {
    setNotice(null);
    setTitleEnabled(t.id, !t.enabled)
      .then(() => load())
      .catch((e) => setError(e.message || "상태 변경 실패"));
  };

  const onDelete = (t) => {
    // 보유자가 있으면 서버가 409 로 막지만, 실수로 누르는 것 자체를 한 번 더 확인한다.
    if (!window.confirm(`'${t.name}' 칭호를 삭제할까요? 되돌릴 수 없습니다.`)) return;
    setNotice(null);
    deleteTitle(t.id)
      .then(() => {
        if (editingId === t.id) cancelEdit();
        load();
      })
      .catch((e) => setError(e.message || "삭제 실패"));
  };

  const enabledCount = rows.filter((r) => r.enabled).length;

  return (
    <div className="adm-page">
      <div className="admin-page-head">
        <div>
          <h1 className="adm-title">업적 / 칭호 관리</h1>
          <p className="adm-sub">
            칭호 마스터를 추가·수정하고 활성 여부를 바꿉니다. 지급 판정은 활성 칭호만 봅니다.{" "}
            {!loading && !error && (
              <span className="adm-accent">
                총 {rows.length}개 · 활성 {enabledCount}개
              </span>
            )}
          </p>
        </div>
        <RefreshButton onClick={load} disabled={loading} />
      </div>

      <form className="adm-card title-form" onSubmit={submit}>
        <div className="title-form-head">
          <strong>{editingId ? "칭호 수정" : "새 칭호"}</strong>
          {editingId && (
            <button type="button" className="title-link" onClick={cancelEdit}>
              취소하고 새로 만들기
            </button>
          )}
        </div>
        <div className="title-form-row">
          <label className="title-field title-field--sm">
            <span>코드</span>
            <input
              type="text"
              maxLength={50}
              value={form.code}
              placeholder="SAVER_SPROUT"
              disabled={!!editingId}
              title={editingId ? "코드는 생성 후 바꿀 수 없습니다" : undefined}
              onChange={(e) => setField("code", e.target.value.toUpperCase())}
            />
          </label>
          <label className="title-field">
            <span>이름</span>
            <input
              type="text"
              maxLength={50}
              value={form.name}
              placeholder="절약 새싹"
              onChange={(e) => setField("name", e.target.value)}
            />
          </label>
          <label className="title-field">
            <span>조건 설명 (카드에 표시)</span>
            <input
              type="text"
              maxLength={200}
              value={form.description}
              placeholder="누적 절약 10만원"
              onChange={(e) => setField("description", e.target.value)}
            />
          </label>
        </div>
        <div className="title-form-row">
          <label className="title-field">
            <span>판정 지표</span>
            <select
              value={form.metricType}
              onChange={(e) => setField("metricType", e.target.value)}
            >
              {METRICS.map((m) => (
                <option key={m.value} value={m.value}>
                  {m.label}
                </option>
              ))}
            </select>
          </label>
          <label className="title-field title-field--sm">
            <span>목표치</span>
            <input
              type="number"
              min="1"
              value={form.threshold}
              onChange={(e) => setField("threshold", e.target.value)}
            />
          </label>
          <label className="title-field title-field--sm">
            <span>정렬 순서</span>
            <input
              type="number"
              min="0"
              value={form.sortOrder}
              onChange={(e) => setField("sortOrder", e.target.value)}
            />
          </label>
          <label className="title-field title-field--check">
            <span>활성</span>
            <input
              type="checkbox"
              checked={form.enabled}
              onChange={(e) => setField("enabled", e.target.checked)}
            />
          </label>
        </div>
        {formError && <p className="title-form-error">{formError}</p>}
        {notice && <p className="title-form-notice">{notice}</p>}
        <button type="submit" className="title-submit" disabled={submitting}>
          {submitting ? "저장 중…" : editingId ? "수정 저장" : "칭호 추가"}
        </button>
      </form>

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
        ) : rows.length === 0 ? (
          <div className="adm-state">등록된 칭호가 없습니다.</div>
        ) : (
          <div className="adm-table-wrap">
            <table className="adm-table">
              <thead>
                <tr>
                  <th>순서</th>
                  <th>칭호</th>
                  <th>조건</th>
                  <th>상태</th>
                  <th>보유자</th>
                  <th>해금 테마</th>
                  <th>수정일</th>
                  <th>액션</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((t) => (
                  <tr key={t.id} className={t.enabled ? "" : "title-row--off"}>
                    <td className="num">{t.sortOrder}</td>
                    <td>
                      <span className="adm-cell-main">{t.name}</span>
                      <span className="adm-cell-sub">{t.code}</span>
                    </td>
                    <td>
                      <span className="adm-cell-main">{t.description}</span>
                      <span className="adm-cell-sub">
                        {METRIC_LABEL[t.metricType] || t.metricType} ≥ {t.threshold.toLocaleString("ko-KR")}
                      </span>
                    </td>
                    <td>
                      <span className={`adm-badge ${t.enabled ? "adm-badge--green" : "adm-badge--gray"}`}>
                        {t.enabled ? "활성" : "비활성"}
                      </span>
                    </td>
                    <td className="num">{t.holderCount.toLocaleString("ko-KR")}명</td>
                    <td>{t.unlocksThemeName ?? "—"}</td>
                    <td className="num">{fmtDate(t.updatedAt)}</td>
                    <td className="title-actions">
                      <button type="button" className="title-action" onClick={() => startEdit(t)}>
                        수정
                      </button>
                      <button type="button" className="title-action" onClick={() => onToggle(t)}>
                        {t.enabled ? "비활성화" : "활성화"}
                      </button>
                      <button
                        type="button"
                        className="title-action title-action--danger"
                        disabled={t.holderCount > 0 || !!t.unlocksThemeName}
                        title={
                          t.holderCount > 0
                            ? "보유자가 있어 삭제할 수 없습니다. 비활성화하세요."
                            : t.unlocksThemeName
                              ? `'${t.unlocksThemeName}' 테마의 해금 조건이라 삭제할 수 없습니다.`
                              : undefined
                        }
                        onClick={() => onDelete(t)}
                      >
                        삭제
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

export default TitleManagePage;
