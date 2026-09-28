import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import AppShell from "../../components/AppShell";
import { listTitles, setActiveTitle } from "../../api/titles";
import { getMe, updateMe } from "../../api/user";
import "./ProfileSettingsPage.css";

const formFromUser = (user) => ({
  nickname: user?.nickname || "",
  name: user?.name || "",
  age: user?.age ?? "",
  job: user?.job || "",
  monthlyIncome: user?.monthlyIncome ?? "",
});

function ProfileSettingsPage({ user, onLogout, onUserUpdate }) {
  const navigate = useNavigate();
  const [initial, setInitial] = useState(() => formFromUser(user));
  const [form, setForm] = useState(initial);
  const [email, setEmail] = useState(user?.email || "");
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [titles, setTitles] = useState([]);
  const [titleLoading, setTitleLoading] = useState(true);
  const [selectedTitle, setSelectedTitle] = useState("");
  const [savedTitle, setSavedTitle] = useState("");
  const [titleSaving, setTitleSaving] = useState(false);
  const [titleError, setTitleError] = useState("");
  const [titleNotice, setTitleNotice] = useState("");

  useEffect(() => {
    let alive = true;
    listTitles().then((items) => {
      if (!alive) return;
      setTitles(items.filter((item) => item.acquired));
      const active = items.find((item) => item.active);
      const value = active ? String(active.id) : "";
      setSelectedTitle(value);
      setSavedTitle(value);
    }).catch((err) => {
      if (alive) setTitleError(err.message || "칭호를 불러오지 못했어요.");
    }).finally(() => {
      if (alive) setTitleLoading(false);
    });
    return () => { alive = false; };
  }, []);

  const handleTitleSave = async () => {
    setTitleSaving(true);
    setTitleNotice("");
    try {
      await setActiveTitle(selectedTitle === "" ? null : Number(selectedTitle));
      setSavedTitle(selectedTitle);
      setTitleNotice(selectedTitle ? "칭호를 적용했어요." : "칭호를 해제했어요.");
    } catch (err) {
      setTitleNotice(err.message || "칭호 저장에 실패했어요.");
    } finally {
      setTitleSaving(false);
    }
  };

  // /auth/me 응답에는 헤더용 최소 정보만 있으므로, 프로필 상세값은 별도 API에서 읽는다.
  useEffect(() => {
    getMe()
      .then((current) => {
        const next = formFromUser(current);
        setInitial(next);
        setForm(next);
        setEmail(current.email || "");
        onUserUpdate(current);
      })
      .catch((err) => setError(err.message || "프로필을 불러오지 못했어요."));
  }, [onUserUpdate]);

  const changed = JSON.stringify(form) !== JSON.stringify(initial);
  const setField = (field) => (event) => {
    setForm((current) => ({ ...current, [field]: event.target.value }));
    setNotice("");
    setError("");
  };

  const handleSave = async (event) => {
    event.preventDefault();
    // 가입과 같은 규칙 — 서버(ProfileUpdateRequest)도 같은 기준으로 거절한다.
    const nickname = form.nickname.trim();
    const name = form.name.trim();
    if (nickname.length < 2 || nickname.length > 12) {
      setError("닉네임은 2~12자로 입력해 주세요.");
      return;
    }
    if (name.length < 2) {
      setError("이름은 2~30자로 입력해 주세요.");
      return;
    }
    setSaving(true);
    setError("");
    try {
      const updated = await updateMe({
        nickname,
        name,
        age: form.age === "" ? null : Number(form.age),
        job: form.job.trim() || null,
        monthlyIncome: form.monthlyIncome === "" ? null : Number(form.monthlyIncome),
      });
      onUserUpdate(updated);
      const next = formFromUser(updated);
      setInitial(next);
      setForm(next);
      setEmail(updated.email || "");
      setNotice("프로필을 저장했어요.");
    } catch (err) {
      setError(err.message || "프로필 저장에 실패했어요.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <AppShell activeTop="" activeSide="profile" onLogout={onLogout}>
      <main className="profile-settings-main home-main">
        <div>
          <h1>프로필 설정</h1>
          <p>내 정보를 수정하면 VORI 화면에 바로 반영됩니다.</p>
        </div>
        <section className="profile-form" aria-labelledby="profile-title-heading">
          <h2 id="profile-title-heading">칭호 설정</h2>
          <label>
            <span>표시할 칭호</span>
            <select
              value={selectedTitle}
              disabled={titleLoading || titleSaving || !!titleError}
              onChange={(event) => { setSelectedTitle(event.target.value); setTitleNotice(""); }}
            >
              <option value="">칭호 없음</option>
              {titles.map((title) => <option key={title.id} value={String(title.id)}>{title.name}</option>)}
            </select>
            <small>{titleLoading ? "칭호를 불러오는 중…" : titles.length ? "획득한 칭호를 선택하거나 ‘칭호 없음’으로 해제할 수 있어요." : "아직 획득한 칭호가 없어요. 업적/칭호에서 획득 조건을 확인하세요."}</small>
          </label>
          {titleError && <p role="alert" className="profile-message error">{titleError}</p>}
          {titleNotice && <p role="status" className="profile-message">{titleNotice}</p>}
          <div className="profile-actions">
            <button type="button" className="profile-cancel" onClick={() => navigate("/titles")}>업적/칭호 보기</button>
            <button type="button" className="profile-save" disabled={titleLoading || titleSaving || !!titleError || selectedTitle === savedTitle} onClick={handleTitleSave}>{titleSaving ? "적용 중…" : "칭호 적용"}</button>
          </div>
        </section>
        <form className="profile-form" onSubmit={handleSave}>
          <label>
            <span>이메일</span>
            <input value={email} disabled />
            <small>이메일은 로그인 계정으로 사용됩니다.</small>
          </label>
          <label>
            <span>닉네임 <b>필수</b></span>
            <input value={form.nickname} maxLength="12" onChange={setField("nickname")} placeholder="표시할 닉네임" />
          </label>
          <label>
            <span>이름 <b>필수</b></span>
            <input value={form.name} maxLength="30" onChange={setField("name")} placeholder="이름을 입력하세요" />
          </label>
          <div className="profile-form-grid">
            <label>
              <span>나이</span>
              <input type="number" min="1" max="120" value={form.age} onChange={setField("age")} placeholder="예: 23" />
            </label>
            <label>
              <span>직업</span>
              <input value={form.job} maxLength="50" onChange={setField("job")} placeholder="예: 대학생" />
            </label>
          </div>
          <label>
            <span>월 수입</span>
            <div className="profile-money-input">
              <input type="number" min="0" value={form.monthlyIncome} onChange={setField("monthlyIncome")} placeholder="예: 2500000" />
              <em>원</em>
            </div>
          </label>
          {error && <p className="profile-message error">{error}</p>}
          {notice && <p className="profile-message success">{notice}</p>}
          <div className="profile-actions">
            <button type="button" className="profile-cancel" onClick={() => { setForm(initial); setError(""); setNotice(""); }}>취소</button>
            <button type="submit" className="profile-save" disabled={!changed || saving}>{saving ? "저장 중..." : "저장하기"}</button>
          </div>
        </form>
      </main>
    </AppShell>
  );
}

export default ProfileSettingsPage;
