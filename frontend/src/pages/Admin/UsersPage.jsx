import { useCallback, useEffect, useState } from "react";
import { listUsers } from "../../api/admin";
import "./UsersPage.css";

const PAGE_SIZE = 20;

// 권한 필터 탭. null = 전체.
const ROLE_TABS = [
  { key: null, label: "전체" },
  { key: "USER", label: "일반" },
  { key: "ADMIN", label: "관리자" },
];

function formatDate(iso) {
  if (!iso) return "—";
  // 백엔드 LocalDateTime → "2026-06-03T14:22:09" 형태. 날짜만 노출.
  return iso.slice(0, 10);
}

function formatNumber(n) {
  if (n == null) return "—";
  return n.toLocaleString("ko-KR");
}

function cell(value) {
  return value == null || value === "" ? "—" : value;
}

function UsersPage() {
  const [role, setRole] = useState(null);
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    listUsers({ page, size: PAGE_SIZE, role })
      .then((res) => setData(res))
      .catch((e) => setError(e.message || "불러오기 실패"))
      .finally(() => setLoading(false));
  }, [page, role]);

  useEffect(() => {
    load();
  }, [load]);

  const changeRole = (next) => {
    setRole(next);
    setPage(0); // 필터 바뀌면 첫 페이지로
  };

  const totalElements = data?.totalElements ?? 0;
  const totalPages = data?.totalPages ?? 0;
  const users = data?.content ?? [];

  return (
    <div className="admin-users">
      <h1 className="admin-users-title">유저 현황</h1>
      <p className="admin-users-sub">
        가입한 회원 목록입니다.{" "}
        {!loading && !error && (
          <span className="admin-users-count">총 {formatNumber(totalElements)}명</span>
        )}
      </p>

      <div className="admin-users-toolbar">
        <div className="admin-users-tabs" role="tablist" aria-label="권한 필터">
          {ROLE_TABS.map((tab) => (
            <button
              key={tab.label}
              type="button"
              role="tab"
              aria-selected={role === tab.key}
              className={
                role === tab.key
                  ? "admin-users-tab admin-users-tab--active"
                  : "admin-users-tab"
              }
              onClick={() => changeRole(tab.key)}
            >
              {tab.label}
            </button>
          ))}
        </div>
        <button
          type="button"
          className="admin-users-refresh"
          onClick={load}
          disabled={loading}
        >
          새로고침
        </button>
      </div>

      <div className="admin-users-card">
        {error ? (
          <div className="admin-users-state admin-users-state--error">
            <span>{error}</span>
            <button type="button" onClick={load}>
              다시 시도
            </button>
          </div>
        ) : loading ? (
          <div className="admin-users-state">불러오는 중…</div>
        ) : users.length === 0 ? (
          <div className="admin-users-state">표시할 회원이 없습니다.</div>
        ) : (
          <div className="admin-users-table-wrap">
            <table className="admin-users-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>이메일</th>
                  <th>닉네임</th>
                  <th>이름</th>
                  <th className="num">나이</th>
                  <th>직업</th>
                  <th>권한</th>
                  <th className="num">게임머니</th>
                  <th className="num">누적절약</th>
                  <th>가입일</th>
                </tr>
              </thead>
              <tbody>
                {users.map((u) => (
                  <tr key={u.id}>
                    <td className="num">{u.id}</td>
                    <td>{cell(u.email)}</td>
                    <td>{cell(u.nickname)}</td>
                    <td>{cell(u.name)}</td>
                    <td className="num">{u.age ?? "—"}</td>
                    <td>{cell(u.job)}</td>
                    <td>
                      <span
                        className={
                          u.role === "ADMIN"
                            ? "admin-role-badge admin-role-badge--admin"
                            : "admin-role-badge"
                        }
                      >
                        {u.role === "ADMIN" ? "관리자" : "일반"}
                      </span>
                    </td>
                    <td className="num">{formatNumber(u.gameMoney)}</td>
                    <td className="num">{formatNumber(u.totalSaved)}</td>
                    <td>{formatDate(u.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {!loading && !error && totalPages > 1 && (
        <div className="admin-users-pager">
          <button
            type="button"
            onClick={() => setPage((p) => Math.max(p - 1, 0))}
            disabled={page <= 0}
          >
            이전
          </button>
          <span className="admin-users-pager-info">
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

export default UsersPage;
