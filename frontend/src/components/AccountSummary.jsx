import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getMe } from "../api/user";
import CoinIcon from "./CoinIcon";

// onClick 을 주면 클릭 시 그걸 부르고(헤더 아래 내 정보 상자 — AccountMenu), 없으면 프로필 설정으로 간다.
export default function AccountSummary({ onClick } = {}) {
  const navigate = useNavigate();
  const [account, setAccount] = useState(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    let alive = true;
    let revision = 0;
    const refresh = async () => {
      const current = ++revision;
      try {
        const user = await getMe();
        if (!alive || current !== revision) return;
        setAccount(user);
        setError(false);
      } catch {
        if (alive && current === revision) setError(true);
      }
    };
    refresh();
    window.addEventListener("vori:account-updated", refresh);
    window.addEventListener("focus", refresh);
    return () => {
      alive = false;
      window.removeEventListener("vori:account-updated", refresh);
      window.removeEventListener("focus", refresh);
    };
  }, []);

  if (error) return <button className="home-account-summary" onClick={() => window.dispatchEvent(new Event("vori:account-updated"))}>내 정보 다시 불러오기</button>;
  if (!account) return <span className="home-account-summary" role="status">내 정보 불러오는 중…</span>;
  return (
    <button type="button" className="home-account-summary" onClick={onClick ?? (() => navigate("/settings/profile"))} aria-haspopup={onClick ? "dialog" : undefined} aria-label={`${account.nickname}, 보유 코인 ${account.role === "ADMIN" ? "무제한" : account.gameMoney}. ${onClick ? "내 정보 열기" : "프로필 설정 열기"}`}>
      {/* 칭호 배지는 두지 않는다 — 장착 칭호와 올린 업적은 내 정보 상자(AccountMenu)에서 보여 준다 */}
      <strong className="home-account-nickname" title={account.nickname}>{account.nickname}</strong>
      <span className="home-account-coins">
        <CoinIcon className="home-account-coin-icon" />
        {account.role === "ADMIN" ? "∞" : (account.gameMoney ?? 0).toLocaleString("ko-KR")}
      </span>
    </button>
  );
}
