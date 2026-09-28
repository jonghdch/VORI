import { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { adminAddMyCoins } from "../../api/admin";
import { notifyMeChanged } from "../../api/user";

const DEFAULT_AMOUNT = 10000;
const MAX_AMOUNT = 1000000;

/**
 * 주소창 치트 — /coins 를 열면 관리자 본인 계정에 코인을 넣고 상점으로 보낸다.
 *   /coins              → +10,000
 *   /coins?amount=50000 → +50,000 (1~1,000,000)
 *
 * 관리자 라우트(AdminRoute) 뒤에 있어 일반 계정은 /home 으로 튕긴다. 서버도 ADMIN 만 받는다.
 * 브라우저 주소창은 GET 뿐이라 화면이 대신 POST 를 부른다.
 */
function CoinTopUpPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [state, setState] = useState({ status: "loading", text: "코인을 충전하는 중…" });
  const ran = useRef(false); // StrictMode 이중 실행으로 두 번 충전되지 않게

  useEffect(() => {
    if (ran.current) return;
    ran.current = true;

    const raw = Number(params.get("amount"));
    const amount = Number.isFinite(raw) && raw > 0 ? Math.min(Math.floor(raw), MAX_AMOUNT) : DEFAULT_AMOUNT;

    adminAddMyCoins(amount)
      .then((me) => {
        notifyMeChanged();
        setState({
          status: "ok",
          text: `+${amount.toLocaleString("ko-KR")} 충전 완료 · 보유 ${me.gameMoney.toLocaleString("ko-KR")} 코인`,
        });
        setTimeout(() => navigate("/shop", { replace: true }), 1200);
      })
      .catch((e) => setState({ status: "err", text: e.message || "충전에 실패했어요" }));
  }, [params, navigate]);

  return (
    <main className="coin-topup" role="status" aria-live="polite">
      <div className={`coin-topup-card ${state.status === "err" ? "is-err" : ""}`}>
        <span className="coin-topup-icon" aria-hidden>
          {state.status === "err" ? "⚠️" : "🪙"}
        </span>
        <p>{state.text}</p>
        {state.status === "err" && (
          <button type="button" className="home-btn" onClick={() => navigate("/home")}>
            홈으로
          </button>
        )}
      </div>
    </main>
  );
}

export default CoinTopUpPage;
