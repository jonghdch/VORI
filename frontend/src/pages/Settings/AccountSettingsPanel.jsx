import { useId, useState } from "react";
import { useNavigate } from "react-router-dom";
import { withdraw } from "../../api/user";

// 구글 가입자(비밀번호 없음)가 본인 확인으로 입력하는 문구 — 서버 AccountDeletionService.CONFIRM_TEXT 와 같아야 한다
const CONFIRM_TEXT = "탈퇴";
// 서버 account.deletion.grace-days 기본값. 실제 삭제 예정일은 응답의 deleteAt 으로 안내한다
const GRACE_DAYS = 30;

const formatDate = (iso) =>
  new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "long", day: "numeric" }).format(new Date(iso));

/**
 * 환경설정 > 계정 탭. 회원 탈퇴 신청.
 * 탈퇴하면 바로 지우지 않고 30일 뒤 영구 삭제한다 — 그 전에 로그인하면 복구된다.
 * 관리자에게는 이 탭이 보이지 않는다(서버도 403).
 */
function AccountSettingsPanel({ user, onLogout }) {
  const navigate = useNavigate();
  const inputId = useId();
  const [open, setOpen] = useState(false);
  const [value, setValue] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  // 응답에 값이 없던 예전 세션은 비밀번호 계정으로 본다 — 서버가 다시 검사한다
  const usesPassword = user?.hasPassword !== false;
  const ready = usesPassword ? value.length > 0 : value.trim() === CONFIRM_TEXT;

  const cancel = () => {
    setOpen(false);
    setValue("");
    setError("");
  };

  const submit = async (e) => {
    e.preventDefault();
    if (!ready || busy) return;
    setBusy(true);
    setError("");
    try {
      const res = await withdraw(usesPassword ? { password: value } : { confirmText: value.trim() });
      window.alert(
        `탈퇴 신청이 완료됐어요.\n${formatDate(res.deleteAt)}에 모든 기록이 삭제돼요. 그 전에 다시 로그인하면 계정이 복구돼요.`,
      );
      // 서버가 세션을 이미 끊었다. 화면의 로그인 상태만 정리한다
      if (typeof onLogout === "function") await onLogout();
      navigate("/", { replace: true });
    } catch (err) {
      setError(err.message || "탈퇴 신청을 처리하지 못했어요.");
      setBusy(false);
    }
  };

  return (
    <div className="settings-general">
      <section className="settings-section settings-withdraw">
        <div className="settings-row">
          <div className="settings-row-label">
            <h3 className="settings-row-name">회원 탈퇴</h3>
            <div className="settings-row-desc">
              신청하면 바로 로그아웃되고, {GRACE_DAYS}일 뒤 지출·수입 기록, 펫, 칭호, 코인이 모두 삭제돼요.
              {` ${GRACE_DAYS}`}일 안에 다시 로그인하면 탈퇴가 취소되고 그대로 복구돼요.
            </div>
          </div>
          {!open && (
            <div className="settings-row-control">
              <button type="button" className="settings-withdraw-btn" onClick={() => setOpen(true)}>
                탈퇴 신청
              </button>
            </div>
          )}
        </div>

        {open && (
          <form className="settings-withdraw-form" onSubmit={submit}>
            <label className="settings-row-desc" htmlFor={inputId}>
              {usesPassword
                ? "본인 확인을 위해 비밀번호를 입력해 주세요."
                : `본인 확인을 위해 '${CONFIRM_TEXT}'라고 입력해 주세요.`}
            </label>
            <input
              id={inputId}
              className="settings-input"
              type={usesPassword ? "password" : "text"}
              autoComplete={usesPassword ? "current-password" : "off"}
              value={value}
              onChange={(e) => setValue(e.target.value)}
              placeholder={usesPassword ? "비밀번호" : CONFIRM_TEXT}
              maxLength={100}
              disabled={busy}
              autoFocus
            />
            {error && (
              <p className="settings-hint-error" role="alert">
                {error}
              </p>
            )}
            <div className="settings-withdraw-actions">
              <button type="button" className="settings-withdraw-cancel" onClick={cancel} disabled={busy}>
                취소
              </button>
              <button type="submit" className="settings-withdraw-btn settings-withdraw-btn--confirm" disabled={!ready || busy}>
                {busy ? "처리 중…" : "탈퇴하기"}
              </button>
            </div>
          </form>
        )}
      </section>
    </div>
  );
}

export default AccountSettingsPanel;
