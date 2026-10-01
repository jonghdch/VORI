import { useCallback, useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import AppShell from "../../components/AppShell";
import PurchaseHistoryModal from "../../components/PurchaseHistoryModal";
import { PetArt, STAGE_LABEL, TIER_LABEL, VARIANT_LABEL } from "../../components/petVisual";
import { buyEgg, listEggProducts, listMyEggs, openEgg } from "../../api/pet";
import { buyFurniture, listFurnitureProducts, listMyFurniture } from "../../api/furniture";
import { getMe, notifyPetChanged } from "../../api/user";
import { CATEGORY_LABEL, FurnitureArt, STAT_LABEL, isSurface } from "../../components/furnitureVisual";
import { EGG_IMAGE, eggImageFor } from "../../components/eggVisual";
import shopBackgroundImage from "../../assets/shop/shop-background.png";
import furnitureIconImage from "../../assets/shop/furniture-icon.png";
import "../Home/HomeDashboard.css";
import "./ShopPage.css";

// 등급별 소개 문구 — 가격·확률은 백엔드(EggGrade)가 단일 출처, 문구만 프론트.
const GRADE_COPY = {
  BASIC: "어떤 친구가 태어날지 두근두근한 기본 알이에요.",
  PREMIUM: "희귀한 친구를 만날 확률이 높아진 고급 알이에요.",
  LEGENDARY: "S등급 친구가 가장 잘 나오는 최고급 알이에요.",
};

const TIER_ORDER = ["S", "A", "B", "C"];

// 가구 진열대는 알 진열대처럼 한 번에 3개씩 보여주고, 화살표로 넘긴다.
const FURNITURE_PER_PAGE = 3;

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

  // 가구 상점 — 상품(잠긴 것 포함)과 내 보유 가구. 보유 수를 상품 카드에 표시한다.
  const [furnitureProducts, setFurnitureProducts] = useState([]);
  const [myFurniture, setMyFurniture] = useState([]);
  const [furnitureError, setFurnitureError] = useState(null);

  // 상점 이미지 안 진열대에 무엇을 보여줄지 — "egg"(알 상점) | "furniture"(가구 상점)
  // /shop?tab=furniture 로 들어오면 가구 상점부터 연다 (마이룸의 "가구 상점 가기").
  const [searchParams] = useSearchParams();
  const [shopTab, setShopTab] = useState(() =>
    searchParams.get("tab") === "furniture" ? "furniture" : "egg",
  );
  const [furniturePage, setFurniturePage] = useState(0);

  // 구매 내역 팝업 — 닫힐 때 포커스 복귀는 모달이 맡는다(열기 전 포커스로 되돌림)
  const [historyOpen, setHistoryOpen] = useState(false);

  const reload = useCallback(async () => {
    const [meRes, eggRes] = await Promise.all([getMe(), listMyEggs(true)]);
    setMe(meRes);
    setEggs(eggRes);
  }, []);

  const reloadFurniture = useCallback(async () => {
    const [prodRes, mineRes] = await Promise.all([listFurnitureProducts(), listMyFurniture()]);
    setFurnitureProducts(prodRes);
    setMyFurniture(mineRes);
  }, []);

  // 가구는 알과 별도로 불러 한쪽이 실패해도 다른 쪽은 뜨게 한다.
  useEffect(() => {
    let alive = true;
    reloadFurniture().catch((e) => {
      if (alive && e.status !== 401) setFurnitureError(e.message || "가구를 불러오지 못했어요");
    });
    return () => {
      alive = false;
    };
  }, [reloadFurniture]);

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
  const unlimitedCoins = me?.role === "ADMIN";

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
        // 사유는 서버 문구를 그대로 쓰고, 코인 부족일 때만 모으는 방법을 덧붙인다.
        text: e.status === 400 ? `${e.message} 절약하면 10원당 1코인이 쌓여요.` : e.message,
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
      // 새 펫이 생겼다 — 이름 짓기 팝업(PetNameGate)이 이 신호를 받고 뜬다
      notifyPetChanged();
      await reload();
    } catch (e) {
      // 서버가 보여줄 문구를 message 로 준다(GlobalExceptionHandler). 상태 코드로 문구를
      // 정하면 같은 코드에 사유가 추가될 때 틀린 안내가 나간다 — 실제로 그랬다.
      // 개봉 409 는 "이미 개봉한 알" 뿐이었는데 "키우던 펫이 있음" 이 추가되면서,
      // 펫 보유 중 개봉을 눌러도 "이미 개봉한 알이에요" 가 떴다.
      setNotice({ kind: "err", text: e.message });
      // 409 면 목록이 낡았을 수 있으므로 다시 맞춘다
      if (e.status === 409) reload().catch(() => {});
    } finally {
      setBusy(null);
    }
  };

  const handleBuyFurniture = async (product) => {
    setNotice(null);
    setBusy(`furniture:${product.code}`);
    try {
      await buyFurniture(product.code);
      await Promise.all([reload(), reloadFurniture()]);
      setNotice({
        kind: "ok",
        text: `${product.name}을(를) 샀어요. 마이룸의 보유 가구에서 배치해야 효과가 생겨요.`,
      });
    } catch (e) {
      // 403 = 칭호로 잠긴 테마, 400 = 코인 부족 — 문구는 서버가 준다
      setNotice({ kind: "err", text: e.message });
    } finally {
      setBusy(null);
    }
  };

  // 벽지·바닥(방 전체에 깔리는 면)은 상점 진열대에 올리지 않는다
  const shelfFurniture = furnitureProducts.filter((item) => !isSurface(item.category));
  const furniturePageCount = Math.max(1, Math.ceil(shelfFurniture.length / FURNITURE_PER_PAGE));
  // 상품 수가 줄어 현재 쪽이 사라져도 마지막 쪽을 보여준다
  const currentFurniturePage = Math.min(furniturePage, furniturePageCount - 1);
  const visibleFurniture = shelfFurniture.slice(
    currentFurniturePage * FURNITURE_PER_PAGE,
    (currentFurniturePage + 1) * FURNITURE_PER_PAGE
  );

  const ownedCountByName = myFurniture.reduce((acc, f) => {
    acc[f.name] = (acc[f.name] || 0) + 1;
    return acc;
  }, {});

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
            {shopTab === "egg" ? (
              <>
                <h1>{nickname}님, 어떤 알을 데려갈까요?</h1>
                <p>
                  절약한 돈이 코인이 되고, 코인으로 새 친구가 태어날 알을 살 수 있어요.
                </p>
              </>
            ) : (
              <>
                <h1>{nickname}님, 어떤 가구를 들여볼까요?</h1>
                <p>
                  마이룸에 배치해야 효과가 생겨요 · 보유 가구 {myFurniture.length}개
                </p>
              </>
            )}
          </div>

          <div className="shop-hero-corner">
            <button
              type="button"
              className="shop-history-btn"
              onClick={() => setHistoryOpen(true)}
              aria-haspopup="dialog"
            >
              <svg viewBox="0 0 16 16" width="13" height="13" aria-hidden="true">
                <path
                  d="M3.5 1.5h9v13l-2.2-1.4L8 14.5l-2.3-1.4-2.2 1.4zM6 5.5h4M6 8.5h4"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="1.4"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
              구매 내역
            </button>
            <div className="shop-coin-badge" aria-live="polite">
              보유 코인 {loading ? "…" : unlimitedCoins ? "∞" : gameMoney.toLocaleString("ko-KR")}
            </div>
          </div>

          {/* 알 / 가구 상점 전환 — 아이콘을 누르면 진열대가 바뀐다 */}
          <div className="shop-tabs" role="tablist" aria-label="상점 종류">
            <button
              type="button"
              role="tab"
              id="shop-tab-egg"
              aria-selected={shopTab === "egg"}
              aria-controls="shop-panel-egg"
              className={`shop-tab ${shopTab === "egg" ? "is-active" : ""}`}
              onClick={() => setShopTab("egg")}
            >
              <img src={EGG_IMAGE.BASIC} alt="" />
              <span>알</span>
            </button>
            <button
              type="button"
              role="tab"
              id="shop-tab-furniture"
              aria-selected={shopTab === "furniture"}
              aria-controls="shop-panel-furniture"
              className={`shop-tab ${shopTab === "furniture" ? "is-active" : ""}`}
              onClick={() => setShopTab("furniture")}
            >
              <img src={furnitureIconImage} alt="" />
              <span>가구</span>
            </button>
          </div>

          {shopTab === "egg" && (
          <div
            className="shop-display-shelf"
            id="shop-panel-egg"
            role="tabpanel"
            aria-labelledby="shop-tab-egg"
          >
            {loading && products.length === 0 && (
              <p className="shop-shelf-state">상품을 불러오는 중…</p>
            )}
            {error && <p className="shop-shelf-state shop-shelf-state--error">{error}</p>}
            {products.map((item) => {
              const affordable = unlimitedCoins || gameMoney >= item.price;
              const isBusy = busy === `buy:${item.grade}`;
              return (
                <article key={item.grade} className="shop-display-item">
                  <div className="shop-display-image-wrap">
                    <img src={eggImageFor(item.grade, item.name)} alt={item.name} className="shop-display-image" />
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
          )}

          {shopTab === "furniture" && (
            <div
              className="shop-furniture-stage"
              id="shop-panel-furniture"
              role="tabpanel"
              aria-labelledby="shop-tab-furniture"
            >
              <button
                type="button"
                className="shop-page-arrow"
                aria-label="이전 가구 보기"
                disabled={currentFurniturePage === 0}
                onClick={() => setFurniturePage(currentFurniturePage - 1)}
              >
                <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
                  <path d="M15 5l-7 7 7 7" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              </button>
              <div className="shop-furniture-shelf" aria-live="polite">
              {furnitureError && (
                <p className="shop-shelf-state shop-shelf-state--error">{furnitureError}</p>
              )}
              {!furnitureError && furnitureProducts.length === 0 && (
                <p className="shop-shelf-state">가구를 불러오는 중…</p>
              )}
              {visibleFurniture.map((item) => {
                const affordable = unlimitedCoins || gameMoney >= item.price;
                const isBusy = busy === `furniture:${item.code}`;
                const owned = ownedCountByName[item.name] || 0;
                return (
                  <article
                    key={item.code}
                    className={`shop-display-item shop-furniture-item ${item.locked ? "is-locked" : ""}`}
                  >
                    <div className="shop-display-image-wrap">
                      <FurnitureArt
                        category={item.category}
                        name={item.name}
                        className="shop-display-image"
                        emojiClassName="shop-furniture-emoji"
                      />
                    </div>
                    <div className="shop-display-info">
                      <h2>{item.name}</h2>
                      {owned > 0 && <em className="shop-furniture-owned">보유 {owned}</em>}
                      <p>
                        {CATEGORY_LABEL[item.category] ?? item.category} ·{" "}
                        {STAT_LABEL[item.statTarget] ?? item.statTarget} · 분양가 +{item.releaseBonusPct}%
                      </p>
                      {item.themeName && (
                        <small className="shop-furniture-theme">
                          {item.themeName} 테마
                          {item.themeSetBonusPct != null && ` · 세트 +${item.themeSetBonusPct}%`}
                        </small>
                      )}
                      {item.locked && (
                        <small className="shop-furniture-lock">
                          🔒 칭호 "{item.unlockTitleName ?? "?"}" 획득 시 해금
                        </small>
                      )}
                      <strong>{coin(item.price)}</strong>
                      <button
                        type="button"
                        className="home-btn home-btn-primary shop-buy-btn"
                        disabled={item.locked || !affordable || busy !== null}
                        onClick={() => handleBuyFurniture(item)}
                      >
                        {isBusy ? "구매 중…" : item.locked ? "잠김" : affordable ? "구매하기" : "코인 부족"}
                      </button>
                    </div>
                  </article>
                );
              })}
              </div>
              <button
                type="button"
                className="shop-page-arrow"
                aria-label="다음 가구 보기"
                disabled={currentFurniturePage >= furniturePageCount - 1}
                onClick={() => setFurniturePage(currentFurniturePage + 1)}
              >
                <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
                  <path d="M9 5l7 7-7 7" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              </button>
              {shelfFurniture.length > 0 && (
                <p className="shop-page-indicator">
                  {currentFurniturePage + 1} / {furniturePageCount}
                </p>
              )}
            </div>
          )}
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
                      <img src={eggImageFor(egg.grade, egg.gradeName)} alt="" className="shop-egg-thumb" />
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
              {result && <span>잔여 {unlimitedCoins ? "∞ 코인" : coin(result.remainGameMoney)}</span>}
            </div>
            {result ? (
              <div className="shop-result-body">
                <span className="shop-result-art">
                  <PetArt
                    appearanceKey={result.pet.appearanceKey}
                    stage={result.pet.stage}
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

        {historyOpen && (
          <PurchaseHistoryModal onClose={() => setHistoryOpen(false)} />
        )}
      </main>
    </AppShell>
  );
}

export default ShopPage;
