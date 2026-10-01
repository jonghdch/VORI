import { useCallback, useEffect, useState } from "react";
import { growUserPet, listUsers } from "../../api/admin";
import RefreshButton from "./RefreshButton";
import "./AdminCommon.css";

const PAGE_SIZE = 20;

const STAGE_LABELS = {
  INFANT: "유년기",
  JUVENILE: "청소년기",
  ADULT: "성체",
};

function PetManagePage() {
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [working, setWorking] = useState(null);
  const [notices, setNotices] = useState({});

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    listUsers({ page, size: PAGE_SIZE, role: "USER" })
      .then(setData)
      .catch((e) => setError(e.message || "회원 목록을 불러오지 못했습니다"))
      .finally(() => setLoading(false));
  }, [page]);

  useEffect(() => {
    load();
  }, [load]);

  const grow = async (user, stage) => {
    const workKey = `${user.id}:${stage}`;
    setWorking(workKey);
    setNotices((prev) => ({ ...prev, [user.id]: null }));
    try {
      const pet = await growUserPet(user.id, stage);
      const stageLabel = STAGE_LABELS[pet.stage] || pet.stage;
      setNotices((prev) => ({
        ...prev,
        [user.id]: { kind: "ok", text: `${pet.speciesName || "펫"} · ${stageLabel} 적용 완료` },
      }));
    } catch (e) {
      setNotices((prev) => ({
        ...prev,
        [user.id]: { kind: "error", text: e.message || "펫 성장에 실패했습니다" },
      }));
    } finally {
      setWorking(null);
    }
  };

  const users = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

  return (
    <div className="adm-page">
      <div className="admin-page-head">
        <div>
          <h1 className="adm-title">펫 성장 지원</h1>
          <p className="adm-sub">시연·QA 를 위해 회원의 활성 펫을 원하는 단계까지 성장시킵니다.</p>
        </div>
        <RefreshButton onClick={load} disabled={loading || working !== null} />
      </div>

      <div className="adm-card">
        {error ? (
          <div className="adm-state adm-state--error">
            <span>{error}</span>
            <button type="button" onClick={load}>다시 시도</button>
          </div>
        ) : loading ? (
          <div className="adm-state">불러오는 중…</div>
        ) : users.length === 0 ? (
          <div className="adm-state">표시할 일반 회원이 없습니다.</div>
        ) : (
          <div className="adm-table-wrap">
            <table className="adm-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>회원</th>
                  <th>가입일</th>
                  <th>성장 작업</th>
                  <th>결과</th>
                </tr>
              </thead>
              <tbody>
                {users.map((user) => {
                  const notice = notices[user.id];
                  return (
                    <tr key={user.id}>
                      <td className="num">{user.id}</td>
                      <td>
                        <span className="adm-cell-main">{user.nickname || "닉네임 없음"}</span>
                        <span className="adm-cell-sub">{user.email}</span>
                      </td>
                      <td>{user.createdAt?.slice(0, 10) || "—"}</td>
                      <td>
                        <div className="adm-action-row">
                          <button
                            type="button"
                            className="adm-btn adm-btn--small"
                            disabled={working !== null}
                            onClick={() => grow(user, "JUVENILE")}
                          >
                            {working === `${user.id}:JUVENILE` ? "처리 중…" : "청소년기까지"}
                          </button>
                          <button
                            type="button"
                            className="adm-btn adm-btn--small adm-btn--primary"
                            disabled={working !== null}
                            onClick={() => grow(user, "ADULT")}
                          >
                            {working === `${user.id}:ADULT` ? "처리 중…" : "성체까지"}
                          </button>
                        </div>
                      </td>
                      <td>
                        {notice ? (
                          <span className={notice.kind === "ok" ? "adm-result adm-result--ok" : "adm-result adm-result--error"}>
                            {notice.text}
                          </span>
                        ) : "—"}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {!loading && !error && totalPages > 1 && (
        <div className="adm-pager">
          <button type="button" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>이전</button>
          <span className="adm-pager-info">{page + 1} / {totalPages}</span>
          <button type="button" disabled={page >= totalPages - 1} onClick={() => setPage((p) => p + 1)}>다음</button>
        </div>
      )}
    </div>
  );
}

export default PetManagePage;
