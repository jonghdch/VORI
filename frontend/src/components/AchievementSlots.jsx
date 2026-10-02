import { EQUIP_LIMIT } from "../api/titles";

/**
 * 장착한 업적 칸 3개 — 내 정보 상자(AccountMenu)와 도감 업적 탭(AchievementPanel)이 같이 쓴다.
 * shown: 올린 업적(순서대로). onRemove(achievement) 가 있으면 채워진 칸에 × 가 생긴다(업적 탭). 내 정보 상자는 보기만 한다.
 * onEmptyClick 이 있으면 빈 칸이 버튼이 되고(내 정보 상자 → 업적 탭으로), 없으면 자리 표시만 한다.
 * 스타일은 HomeDashboard.css 의 .account-menu-slot*.
 */
function AchievementSlots({ shown, onRemove, onEmptyClick, disabled = false, className = "" }) {
  const slots = Array.from({ length: EQUIP_LIMIT }, (_, i) => shown[i] ?? null);
  return (
    <div className={`account-menu-slots ${className}`} aria-label="내 업적">
      {slots.map((a, i) =>
        a ? (
          <div key={a.id} className="account-menu-slot is-filled" title={a.description}>
            <span className="account-menu-slot-icon" aria-hidden>🏆</span>
            <span className="account-menu-slot-name">{a.name}</span>
            {onRemove && (
              <button
                type="button"
                className="account-menu-slot-remove"
                disabled={disabled}
                onClick={() => onRemove(a)}
                aria-label={`${a.name} 빼기`}
              >
                ×
              </button>
            )}
          </div>
        ) : onEmptyClick ? (
          <button
            key={`empty-${i}`}
            type="button"
            className="account-menu-slot is-empty"
            onClick={onEmptyClick}
            aria-label="빈 업적 칸 — 업적 탭에서 업적 넣기"
          >
            <span aria-hidden>+</span>
          </button>
        ) : (
          <div key={`empty-${i}`} className="account-menu-slot is-empty is-static" aria-label="빈 업적 칸">
            <span aria-hidden>+</span>
          </div>
        ),
      )}
    </div>
  );
}

export default AchievementSlots;
