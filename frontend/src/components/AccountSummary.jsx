import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getMe } from "../api/user";
import { listTitles } from "../api/titles";

export default function AccountSummary() {
  const navigate = useNavigate();
  const [account, setAccount] = useState(null);
  const [title, setTitle] = useState(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    let alive = true;
    let revision = 0;
    const refresh = async () => {
      const current = ++revision;
      try {
        const [user, titles] = await Promise.all([getMe(), listTitles()]);
        if (!alive || current !== revision) return;
        setAccount(user);
        setTitle(titles.find((item) => item.active)?.name || "칭호 없음");
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
    <button type="button" className="home-account-summary" onClick={() => navigate("/settings/profile")} aria-label={`${title}, ${account.nickname}, 보유 코인 ${account.role === "ADMIN" ? "무제한" : account.gameMoney}. 프로필 설정 열기`}>
      <span className="home-account-title" title={title}>{title}</span>
      <strong className="home-account-nickname" title={account.nickname}>{account.nickname}</strong>
      <span className="home-account-coins">{account.role === "ADMIN" ? "∞" : (account.gameMoney ?? 0).toLocaleString("ko-KR")} 코인</span>
    </button>
  );
}
