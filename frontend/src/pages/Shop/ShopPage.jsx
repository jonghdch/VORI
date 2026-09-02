import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import AppShell from "../../components/AppShell";
import { PetArt, STAGE_LABEL, TIER_LABEL, VARIANT_LABEL } from "../../components/petVisual";
import { buyEgg, listEggProducts, listMyEggs, openEgg } from "../../api/pet";
import { getMe } from "../../api/user";
import eggImage from "../../assets/shop/egg.png";
import shopBackgroundImage from "../../assets/shop/shop-background.png";
import "../Home/HomeDashboard.css";
import "./ShopPage.css";

// 등급별 소개 문구 — 가격·확률은 백엔드(EggGrade)가 단일 출처, 문구만 프론트.
const GRADE_COPY = {
  BASIC: "어떤 펫이 태어날지 모르는 특별한 알이에요.",
  PREMIUM: "희귀한 친구를 만날 확률이 높아진 알이에요.",
  SUPREME: "S등급 펫이 가장 잘 나오는 최고급 알이에요.",
};

const TIER_ORDER = ["S", "A", "B", "C"];

const coin = (n) => `${(n ?? 0).toLocaleString("ko-KR")} 코인`;

function ShopPage({ user, onLogout }) {
  const navigate = useNavigate();
  const nickname = user?.nickname || "사용자";

  const [products, setProducts] = useState([]);
  const [me, setMe] = useState(null); // gameMoney 는 세션 user 가 아니라 여기서
  const [eggs, setEggs] = useState([]); // 미개봉 보유 알
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(null); // "buy:BASIC" | "open:12"
  const [notice, setNotice] = useState(null); // { kind: "ok"|"err", text }
  const [result, setResult] = useState(null); // 개봉 결과 { pet, remainGameMoney }

  const reload = useCallback(async () => {
    const [meRes, eggRes] = await Promise.all([getMe(), listMyEggs(true)]);
    setMe(meRes);
    setEggs(eggRes);
  }, []);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    Promise.all([listEggProducts(), getMe(), listMyEggs(true)])
      .then(([prodRes, meRes, eggRes]) => {
        if (!alive) return;
        setProducts(prodRes);
        setMe(meRes);
        setEggs(eggRes);
      })
      .catch((e) => {
        if (!alive) return;
        if (e.status === 401) {
          navigate("/login", { replace: true });
          return;
        }
        setError(e.message || "상점을 불러오지 못했어요");
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [navigate]);

  const gameMoney = me?.gameMoney ?? 0;

  const handleBuy = async (product) => {
    setNotice(null);
    setBusy(`buy:${product.grade}`);
    try {
      await buyEgg(product.grade);
      await reload();
      setNotice({ kind: "ok", text: `${product.name}을 데려왔어요. 아래 보유 알에서 개봉해 보세요!` });
    } catch (e) {
      setNotice({
        kind: "err",
        text: e.status === 400 ? "코인이 부족해요. 절약하면 10원당 1코인이 쌓여요." : e.message,
      });
    } finally {
      setBusy(null);
    }
  };

  const handleOpen = async (egg) => {
    setNotice(null);
    setBusy(`open:${egg.id}`);
    try {
      const res = await openEgg(egg.id);
      setResult(res);
      await reload();
    } catch (e) {
      setNotice({
        kind: "err",
        text: e.status === 409 ? "이미 개봉한 알이에요." : e.message,
      });
      // 409 면 목록이 낡은 것 — 다시 맞춘다
      if (e.status === 409) reload().catch(() => {});
    } finally {
      setBusy(null);
    }
  };

  return (
    <AppShell
      activeTop="shop"
      activeSide="shop"
      user={user}
      onLogout={onLogout}
    >
      <main className="home-main shop-main">
        <section
          className="shop-hero"
          style={{ backgroundImage: `url(${shopBackgroundImage})` }}
          aria-label="VORI 상점"
        >
          <div className="shop-hero-panel">
            <p className="shop-eyebrow">VORI SHOP</p>
            <h1>{nickname}님, 어떤 알을 데려갈까요?</h1>
            <p>
              절약한 돈이 코인이 되고, 코인으로 새 친구가 태어날 알을 살 수 있어요.
            </p>
          </div>

          <div className="shop-coin-badge" aria-live="polite">
            보유 코인 {loading ? "…" : gameMoney.toLocaleString("ko-KR")}
          </div>

          <div className="shop-display-shelf" aria-label="판매 상품">
            {loading && products.length === 0 && (
              <p className="shop-shelf-state">상품을 불러오는 중…</p>
            )}
            {error && <p className="shop-shelf-state shop-shelf-state--error">{error}</p>}
            {products.map((item) => {
              const affordable = gameMoney >= item.price;
              const isBusy = busy === `buy:${item.grade}`;
              return (
                <article key={item.grade} className="shop-display-item">
                  <div className="shop-display-image-wrap">
                    <img src={eggImage} alt={item.name} className="shop-display-image" />
                  </div>
                  <div className="shop-display-info">
                    <h2>{item.name}</h2>
                    <p>{GRADE_COPY[item.grade] ?? "새 친구가 태어날 알이에요."}</p>
                    <ul className="shop-prob-list" aria-label="등급별 확률">
                      {TIER_ORDER.filter((t) => item.probabilities?.[t] > 0).map((t) => (
                        <li key={t} className={`shop-prob shop-prob--${t.toLowerCase()}`}>
                          {t} {item.probabilities[t]}%
                        </li>
                      ))}
                    </ul>
                    <strong>{coin(item.price)}</strong>
                    <button
                      type="button"
                      className="home-btn home-btn-primary shop-buy-btn"
                      disabled={!affordable || isBusy || busy !== null}
                      onClick={() => handleBuy(item)}
                      title={affordable ? undefined : "코인이 부족해요"}
                    >
                      {isBusy ? "구매 중…" : affordable ? "구매하기" : "코인 부족"}
                    </button>
                  </div>
                </article>
              );
            })}
          </div>
        </section>

        {notice && (
          <p
            className={`shop-notice ${notice.kind === "err" ? "shop-notice--err" : ""}`}
            role={notice.kind === "err" ? "alert" : "status"}
          >
            {notice.text}
          </p>
        )}

        <div className="shop-lower">
          {/* 보유 알 — 개봉하면 가챠가 돌아 펫이 태어난다 */}
          <section className="home-card shop-inventory">
            <div className="shop-section-head">
              <h2 className="home-card-title home-card-title--sm">보유 알</h2>
              <span>{eggs.length}개 미개봉</span>
            </div>
            {!loading && eggs.length === 0 ? (
              <p className="shop-empty">아직 개봉할 알이 없어요. 위에서 알을 데려와 보세요.</p>
            ) : (
              <ul className="shop-egg-list">
                {eggs.map((egg) => {
                  const isBusy = busy === `open:${egg.id}`;
                  return (
                    <li key={egg.id} className="shop-egg-item">
                      <img src={eggImage} alt="" className="shop-egg-thumb" />
                      <div className="shop-egg-info">
                        <strong>{egg.gradeName}</strong>
                        <small>{coin(egg.price)} · {egg.purchasedAt?.slice(0, 10)} 구매</small>
                      </div>
                      <button
                        type="button"
                        className="home-btn home-btn-primary shop-open-btn"
                        disabled={busy !== null}
                        onClick={() => handleOpen(egg)}
                      >
                        {isBusy ? "개봉 중…" : "개봉하기"}
                      </button>
                    </li>
                  );
                })}
              </ul>
            )}
          </section>

          {/* 개봉 결과 — 마지막으로 태어난 펫 */}
          <section className="home-card shop-result" aria-live="polite">
            <div className="shop-section-head">
              <h2 className="home-card-title home-card-title--sm">새로 태어난 친구</h2>
              {result && <span>잔여 {coin(result.remainGameMoney)}</span>}
            </div>
            {result ? (
              <div className="shop-result-body">
                <span className="shop-result-art">
                  <PetArt
                    appearanceKey={result.pet.appearanceKey}
                    name={result.pet.speciesName}
                    className="shop-result-image"
                    emojiClassName="shop-result-emoji"
                  />
                </span>
                <div className="shop-result-text">
                  <strong>{result.pet.speciesName}</strong>
                  <div className="shop-result-tags">
                    <span className={`shop-tag shop-tag--${(result.pet.tier || "c").toLowerCase()}`}>
                      {TIER_LABEL[result.pet.tier] ?? result.pet.tier}
                    </span>
                    <span className="shop-tag">{STAGE_LABEL[result.pet.stage] ?? result.pet.stage}</span>
                    {VARIANT_LABEL[result.pet.variant] && (
                      <span className="shop-tag shop-tag--variant">
                        ✨ {VARIANT_LABEL[result.pet.variant]}
                      </span>
                    )}
                  </div>
                  <p>알에서 {result.pet.speciesName}이(가) 태어났어요! 마이룸에서 키워 보세요.</p>
                  <button
                    type="button"
                    className="home-link-btn"
                    onClick={() => navigate("/raise")}
                  >
                    마이룸 가기 →
                  </button>
                </div>
              </div>
            ) : (
              <p className="shop-empty">알을 개봉하면 여기에 새 친구가 나타나요.</p>
            )}
          </section>
        </div>
      </main>
    </AppShell>
  );
}

export default ShopPage;
