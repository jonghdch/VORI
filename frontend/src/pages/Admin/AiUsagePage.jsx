import { useCallback, useEffect, useState } from "react";
import { getAiSettings, updateAiSettings } from "../../api/admin";
import RefreshButton from "./RefreshButton";
import "./AdminCommon.css";
import "./AiUsagePage.css";

// Gemini 한도 상태 + 없어도 되는 AI 문장 생성 끄기.
// 11/12 경진대회처럼 방문자가 몰리면 무료 한도(모델당 하루 약 20회)가 금방 끝난다.
// 질문 문구·일일 코멘트를 끄면 남은 한도를 펫 대화·영수증·사유 분류에 남긴다.
const SWITCHES = [
  {
    key: "questionWording",
    label: "질문 문구 AI 생성",
    off: "끄면 템플릿 질문이 나갑니다 — 「이번 ○○ 30,000원, 평소보다 큰 지출이었어. 어떤 일이었는지 골라 주거나 가볍게 적어 줄래?」",
  },
  {
    key: "dailyComment",
    label: "일일 코멘트 AI 생성",
    off: "끄면 일일 리포트에 통계만 남습니다. 0시 10분 배치가 전날 기록이 있는 사용자마다 한 번씩 쓰고, 그 한도는 다음 날 오후까지 이어집니다.",
  },
];

function AiUsagePage() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(null); // 바꾸는 중인 스위치 key
  const [error, setError] = useState(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    getAiSettings()
      .then(setData)
      .catch((e) => setError(e.message || "불러오기 실패"))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const toggle = (key, on) => {
    setSaving(key);
    setError(null);
    updateAiSettings({ [key]: on })
      .then(setData)
      .catch((e) => setError(e.message || "저장 실패"))
      .finally(() => setSaving(null));
  };

  return (
    <div className="adm-page">
      <div className="admin-page-head">
        <div>
          <h1 className="adm-title">AI 사용량</h1>
          <p className="adm-sub">
            질문 문구·사유 분류·펫 대화·일일 코멘트·영수증이 Gemini 무료 한도(모델당 하루 약 20회)를 같이
            씁니다. 한도는 태평양 자정(한국 오후 4~5시)에 풀립니다.
          </p>
        </div>
        <RefreshButton onClick={load} disabled={loading} />
      </div>

      {error && <div className="aiu-banner aiu-banner--error">{error}</div>}

      {loading && !data ? (
        <div className="adm-card">
          <div className="adm-state">불러오는 중…</div>
        </div>
      ) : data ? (
        <div className="aiu-grid">
          <section className="adm-card aiu-card">
            <h2 className="aiu-card-title">오늘 한도</h2>
            <ul className="aiu-models">
              {data.models.map((m, i) => (
                <li key={m.model} className="aiu-model">
                  <span className="aiu-model-name">
                    {m.model}
                    <span className="aiu-model-role">{i === 0 ? "주 모델" : "대체 모델"}</span>
                  </span>
                  {m.exhausted ? (
                    <span className="adm-badge adm-badge--red">다 씀 · {m.availableAt}에 풀림</span>
                  ) : (
                    <span className="adm-badge adm-badge--green">사용 가능</span>
                  )}
                </li>
              ))}
            </ul>
            <p className="aiu-note">
              서버가 한도 초과 응답을 받은 뒤에야 「다 씀」으로 압니다. 남은 횟수는 알 수 없습니다. 다 쓴 동안
              펫 대화·영수증은 「오늘 준비한 AI 사용량을 다 썼어요」 안내를, 지출 이유 답변은 칩·낱말 규칙으로
              처리합니다.
            </p>
          </section>

          <section className="adm-card aiu-card">
            <h2 className="aiu-card-title">없어도 되는 AI 끄기</h2>
            <ul className="aiu-switches">
              {SWITCHES.map((s) => (
                <li key={s.key} className="aiu-switch">
                  <label className="aiu-switch-row">
                    <input
                      type="checkbox"
                      role="switch"
                      checked={data[s.key]}
                      disabled={saving !== null}
                      onChange={(e) => toggle(s.key, e.target.checked)}
                    />
                    <span className="aiu-switch-label">{s.label}</span>
                    <span className={data[s.key] ? "aiu-state aiu-state--on" : "aiu-state"}>
                      {saving === s.key ? "저장 중…" : data[s.key] ? "켜짐" : "꺼짐"}
                    </span>
                  </label>
                  <p className="aiu-switch-desc">{s.off}</p>
                </li>
              ))}
            </ul>
            <p className="aiu-note">
              바로 적용되고, 서버를 다시 띄우면 환경변수(<code>AI_QUESTION_WORDING_ENABLED</code>·
              <code>AI_DAILY_COMMENT_ENABLED</code>, 기본 켜짐) 값으로 돌아갑니다. 펫 대화 1인당 횟수는{" "}
              <code>PET_CHAT_DAILY_LIMIT</code>(기본 10)로 줄입니다.
            </p>
          </section>
        </div>
      ) : null}
    </div>
  );
}

export default AiUsagePage;
