import { useCallback, useEffect, useState } from "react";
import {
  createSanction,
  getSanctions,
  liftSanction,
  listUsers,
} from "../../api/admin";
import RefreshButton from "./RefreshButton";
import "./AdminCommon.css";
import "./SanctionsPage.css";

const PAGE_SIZE = 20;
const TYPE_LABELS = { WARNING: "경고", SUSPENSION: "정지", BAN: "영구정지" };
const STATUS_LABELS = { ACTIVE: "활성", EXPIRED: "만료", LIFTED: "해제" };

const fmtDate = (iso) => (iso ? iso.replace("T", " ").slice(0, 16) : "—");

function statusBadgeClass(status) {
  if (status === "ACTIVE") return "adm-badge adm-badge--red";
  if (status === "LIFTED") return "adm-badge adm-badge--green";
  return "adm-badge adm-badge--gray"; // EXPIRED
}

const EMPTY_FORM = { userId: "", type: "WARNING", reason: "", durationDays: "7" };

function SanctionsPage() {
  const [data, setData] = useState(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [users, setUsers] = useState([]);
  const [form, setForm] = useState(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    getSanctions({ page, size: PAGE_SIZE })
      .then((res) => setData(res))
      .catch((e) => setError(e.message || "불러오기 실패"))
      .finally(() => setLoading(false));
  }, [page]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    listUsers({ size: 100 })
      .then((r) => setUsers(r.content || []))
      .catch(() => {});
  }, []);

  const setField = (k, v) => setForm((f) => ({ ...f, [k]: v }));

  const submit = (e) => {
    e.preventDefault();
    setFormError(null);
    if (!form.userId) return setFormError("대상 유저를 선택해주세요.");
    if (!form.reason.trim()) return setFormError("사유를 입력해주세요.");
    setSubmitting(true);
    createSanction({
      userId: Number(form.userId),
      type: form.type,
      reason: form.reason.trim(),
      durationDays:
        form.type === "SUSPENSION" ? Number(form.durationDays) : null,
    })
      .then(() => {
        setForm(EMPTY_FORM);
        setPage(0);
        load();
      })
      .catch((err) => setFormError(err.message || "제재 추가 실패"))
      .finally(() => setSubmitting(false));
  };

  const onLift = (id) => {
    liftSanction(id)
      .then(() => load())
      .catch((e) => setError(e.message || "해제 실패"));
  };

  const rows = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;

  return (
    <div className="adm-page">
      <div className="admin-page-head">
        <div>
          <h1 className="adm-title">제재 관리</h1>
          <p className="adm-sub">
            유저 제재를 추가·조회·해제합니다.{" "}
            {!loading && !error && (
              <span className="adm-accent">총 {totalElements}건</span>
            )}
          </p>
        </div>
        <RefreshButton onClick={load} disabled={loading} />
      </div>

      <form className="adm-card sanction-form" onSubmit={submit}>
        <div className="sanction-form-row">
          <label className="sanction-field">
            <span>대상 유저</span>
            <select
              value={form.userId}
              onChange={(e) => setField("userId", e.target.value)}
            >
              <option value="">선택</option>
              {users.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.nickname} ({u.email})
                </option>
              ))}
            </select>
          </label>
          <label className="sanction-field">
            <span>종류</span>
            <select
              value={form.type}
              onChange={(e) => setField("type", e.target.value)}
            >
              <option value="WARNING">경고</option>
              <option value="SUSPENSION">정지</option>
              <option value="BAN">영구정지</option>
            </select>
          </label>
          {form.type === "SUSPENSION" && (
            <label className="sanction-field sanction-field--sm">
              <span>기간(일)</span>
              <input
                type="number"
                min="1"
                value={form.durationDays}
                onChange={(e) => setField("durationDays", e.target.value)}
              />
            </label>
          )}
        </div>
        <label className="sanction-field">
          <span>사유</span>
          <input
            type="text"
            maxLength={255}
            value={form.reason}
            placeholder="제재 사유"
            onChange={(e) => setField("reason", e.target.value)}
          />
        </label>
        {formError && <p className="sanction-form-error">{formError}</p>}
        <button type="submit" className="sanction-submit" disabled={submitting}>
          {submitting ? "처리 중…" : "제재 추가"}
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
          <div className="adm-state">제재 기록이 없습니다.</div>
        ) : (
          <div className="adm-table-wrap">
            <table className="adm-table">
              <thead>
                <tr>
                  <th>유저</th>
                  <th>종류</th>
                  <th>사유</th>
                  <th>상태</th>
                  <th>생성</th>
                  <th>만료 / 해제</th>
                  <th>액션</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((s) => (
                  <tr key={s.id}>
                    <td>
                      <span className="adm-cell-main">
                        {s.userNickname || "—"}
                      </span>
                      <span className="adm-cell-sub">{s.userEmail}</span>
                    </td>
                    <td>
                      <span className="adm-badge">
                        {TYPE_LABELS[s.type] || s.type}
                      </span>
                    </td>
                    <td>{s.reason}</td>
                    <td>
                      <span className={statusBadgeClass(s.status)}>
                        {STATUS_LABELS[s.status] || s.status}
                      </span>
                    </td>
                    <td className="num">{fmtDate(s.createdAt)}</td>
                    <td className="num">
                      {s.liftedAt
                        ? `해제 ${fmtDate(s.liftedAt)}`
                        : s.expiresAt
                          ? fmtDate(s.expiresAt)
                          : "—"}
                    </td>
                    <td>
                      {s.status === "ACTIVE" ? (
                        <button
                          type="button"
                          className="sanction-lift"
                          onClick={() => onLift(s.id)}
                        >
                          해제
                        </button>
                      ) : (
                        "—"
                      )}
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
          <button
            type="button"
            onClick={() => setPage((p) => Math.max(p - 1, 0))}
            disabled={page <= 0}
          >
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

export default SanctionsPage;
