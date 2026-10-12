import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import AppShell from "../../components/AppShell";
import Icon from "../../components/Icon";
import PurchaseHistoryModal from "../../components/PurchaseHistoryModal";
import CoinIcon from "../../components/CoinIcon";
import { STAGE_LABEL, TIER_LABEL, VARIANT_LABEL, petVisual } from "../../components/petVisual";
import { buyEgg, listEggProducts, listMyEggs, openEgg } from "../../api/pet";
import { buyFurniture, listFurnitureProducts, listMyFurniture } from "../../api/furniture";
import { getMe, notifyPetChanged } from "../../api/user";
import { furnitureVisual, isSurface } from "../../components/furnitureVisual";
import { eggImageFor } from "../../components/eggVisual";
import FurnitureBrowser from "./FurnitureBrowser";
import "../Home/HomeDashboard.css";
import "./ShopScreen.css";

// 상점 — Figma「상점 — 알 분양」「상점 — VORI(가구)」 배치.
// 머리(상점 · 보유 코인) → 알 분양/가구 전환 → 알: 안내 + 세로 카드 / 가구: 테마 칩 + 2열 카드.
// 구매는 바로 사지 않고 「○○을 살까요?」 확인 창(가격 · 지금 코인 · 구매 후)을 거친다.
// 픽셀 그림은 알·가구·펫 그림에만 — 화면 배경 그림은 두지 않는다.

// 등급별 소개 문구 — 가격·확률은 백엔드(EggGrade)가 단일 출처, 문구만 프론트.
const GRADE_COPY = {
  BASIC: "어떤 친구가 태어날지 두근두근한 기본 알이에요.",
  PREMIUM: "희귀한 친구를 만날 확률이 높아진 고급 알이에요.",
  LEGENDARY: "S등급 친구가 가장 잘 나오는 최고급 알이에요.",
};

const TIER_ORDER = ["S", "A", "B", "C"];

const num = (n) => (n ?? 0).toLocaleString("ko-KR");
const coin = (n) => `${num(n)} 코인`;

function ShopPage({ user, onLogout }) {
  const navigate = useNavigate();

  const [products, setProducts] = useState([]);
  const [me, setMe] = useState(null); // gameMoney 는 세션 user 가 아니라 여기서
  const [eggs, setEggs] = useState([]); // 미개봉 보유 알
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(null); // "buy:BASIC" | "open:12" | "furniture:CODE"
  const [notice, setNotice] = useState(null); // { kind: "ok"|"err", text }
  const [result, setResult] = useState(null); // 개봉 결과 { pet, remainGameMoney }

  // 가구 상점 — 상품(잠긴 것 포함)과 내 보유 가구. 보유 수를 상품 카드에 표시한다.
  const [furnitureProducts, setFurnitureProducts] = useState([]);
  const [myFurniture, setMyFurniture] = useState([]);
  const [furnitureError, setFurnitureError] = useState(null);
  const [furnitureLoading, setFurnitureLoading] = useState(true);

  // "egg"(알 분양) | "furniture"(가구). /shop?tab=furniture 로 들어오면 가구부터 연다 (마이룸의 "가구 상점 가기").
  const [searchParams] = useSearchParams();
  const [shopTab, setShopTab] = useState(() =>
    searchParams.get("tab") === "furniture" ? "furniture" : "egg",
  );

  // 구매 내역 팝업 — 닫힐 때 포커스 복귀는 모달이 맡는다(열기 전 포커스로 되돌림)
  const [historyOpen, setHistoryOpen] = useState(false);
  // 구매 확인 창 — { kind: "egg"|"furniture", product, image }
  const [confirm, setConfirm] = useState(null);

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
    reloadFurniture()
      .catch((e) => {
        if (alive && e.status !== 401) setFurnitureError(e.message || "가구를 불러오지 못했어요");
      })
      .finally(() => {
        if (alive) setFurnitureLoading(false);
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

  const confirmBuy = async () => {
    if (!confirm) return;
    const { kind, product } = confirm;
    if (kind === "egg") await handleBuy(product);
    else await handleBuyFurniture(product);
    setConfirm(null);
  };

  // 벽지·바닥(방 전체에 깔리는 면)은 상점 진열대에 올리지 않는다
  const shelfFurniture = furnitureProducts.filter((item) => !isSurface(item.category));
  const ownedCountByName = myFurniture.reduce((acc, f) => {
    acc[f.name] = (acc[f.name] || 0) + 1;
    return acc;
  }, {});

  const coinLabel = loading ? "…" : unlimitedCoins ? "∞" : num(gameMoney);
  const resultPet = result?.pet;
  const resultImage = resultPet ? petVisual(resultPet.appearanceKey, resultPet.stage).image : null;

  return (
    <AppShell activeTop="shop" activeSide="shop" user={user} onLogout={onLogout}>
      <main className="home-main sp">
        <div className="sp-layout">
          {/* 머리 — 이 화면의 핵심 숫자는 보유 코인 */}
          <div className="sp-head">
            <h1 className="sp-title">상점</h1>
            <button
              type="button"
              className="sp-history-btn"
              onClick={() => setHistoryOpen(true)}
              aria-haspopup="dialog"
            >
              <Icon name="receipt" size={15} />
              구매 내역
            </button>
            <div
              className="sp-coins"
              role="status"
              aria-live="polite"
              aria-label={`보유 코인 ${loading ? "확인 중" : unlimitedCoins ? "무제한" : num(gameMoney)}`}
            >
              <CoinIcon size={18} />
              <strong>{coinLabel}</strong>
            </div>
          </div>

          {/* 알 분양 / 가구 */}
          <div className="sp-seg" role="tablist" aria-label="상점 종류">
            {[
              ["egg", "알 분양", "egg"],
              ["furniture", "가구", "sofa"],
            ].map(([id, label, icon]) => (
              <button
                key={id}
                type="button"
                role="tab"
                id={`shop-tab-${id}`}
                aria-selected={shopTab === id}
                aria-controls={`shop-panel-${id}`}
                className={shopTab === id ? "is-active" : ""}
                onClick={() => setShopTab(id)}
              >
                <Icon name={icon} size={16} />
                {label}
              </button>
            ))}
          </div>

          {notice && (
            <p
              className={`sp-notice ${notice.kind === "err" ? "sp-notice--err" : ""}`}
              role={notice.kind === "err" ? "alert" : "status"}
            >
              {notice.text}
            </p>
          )}

          {shopTab === "egg" ? (
            <div className="sp-panel" id="shop-panel-egg" role="tabpanel" aria-labelledby="shop-tab-egg">
              <p className="sp-card sp-guide">
                절약한 돈이 코인이 되고, 코인으로 새 친구가 태어날 알을 살 수 있어요.
              </p>

              {loading && products.length === 0 && <p className="sp-state">상품을 불러오는 중…</p>}
              {error && <p className="sp-state sp-state--error">{error}</p>}

              <ul className="sp-eggs">
                {products.map((item) => {
                  const affordable = unlimitedCoins || gameMoney >= item.price;
                  const isBusy = busy === `buy:${item.grade}`;
                  const image = eggImageFor(item.grade, item.name);
                  return (
                    <li key={item.grade} className="sp-card sp-egg" data-grade={item.grade}>
                      <div className="sp-egg-top">
                        <span className="sp-tile">
                          <img src={image} alt={item.name} className="sp-pixel" />
                        </span>
                        <div className="sp-egg-meta">
                          <h2>{item.name}</h2>
                          <p>{GRADE_COPY[item.grade] ?? "새 친구가 태어날 알이에요."}</p>
                          <span className="sp-price">
                            <CoinIcon size={15} />
                            <strong>{num(item.price)}</strong> 코인
                          </span>
                        </div>
                      </div>
                      <ul className="sp-probs" aria-label="등장 확률">
                        <li className="sp-probs-label" aria-hidden>
                          등장 확률
                        </li>
                        {TIER_ORDER.map((t) => {
                          const pct = item.probabilities?.[t] ?? 0;
                          return (
                            <li
                              key={t}
                              className={`sp-prob sp-prob--${t.toLowerCase()} ${pct > 0 ? "" : "is-zero"}`}
                            >
                              <b>{t}</b> {pct}%
                            </li>
                          );
                        })}
                      </ul>
                      <div className="sp-egg-foot">
                        {!affordable && (
                          <span className="sp-short">
                            <Icon name="info" size={13} />
                            <b>코인 {num(item.price - gameMoney)} 부족</b>
                            <span>(보유 {num(gameMoney)})</span>
                          </span>
                        )}
                        <button
                          type="button"
                          className="sp-buy"
                          disabled={!affordable || busy !== null}
                          onClick={() => setConfirm({ kind: "egg", product: item, image })}
                        >
                          {!affordable && <Icon name="lock" size={13} />}
                          {isBusy ? "구매 중…" : "구매"}
                        </button>
                      </div>
                    </li>
                  );
                })}
              </ul>

              <div className="sp-lower">
                {/* 보유 알 — 개봉하면 가챠가 돌아 펫이 태어난다 */}
                <section className="sp-card sp-section" aria-labelledby="sp-inv-title">
                  <div className="sp-section-head">
                    <h2 id="sp-inv-title">보유 알</h2>
                    <span>{eggs.length}개 미개봉</span>
                  </div>
                  {!loading && eggs.length === 0 ? (
                    <p className="sp-empty">아직 개봉할 알이 없어요. 위에서 알을 데려와 보세요.</p>
                  ) : (
                    <ul className="sp-inv">
                      {eggs.map((egg) => {
                        const isBusy = busy === `open:${egg.id}`;
                        return (
                          <li key={egg.id}>
                            <span className="sp-tile sp-tile--sm">
                              <img src={eggImageFor(egg.grade, egg.gradeName)} alt="" className="sp-pixel" />
                            </span>
                            <span className="sp-inv-mid">
                              <strong>{egg.gradeName}</strong>
                              <small>
                                {coin(egg.price)} · {egg.purchasedAt?.slice(0, 10)} 구매
                              </small>
                            </span>
                            <button
                              type="button"
                              className="sp-btn sp-btn--dark sp-btn--sm"
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
                <section className="sp-card sp-section" aria-labelledby="sp-result-title" aria-live="polite">
                  <div className="sp-section-head">
                    <h2 id="sp-result-title">새로 태어난 친구</h2>
                  </div>
                  {resultPet ? (
                    <div className="sp-result">
                      <span className="sp-tile sp-tile--lg">
                        {resultImage ? (
                          <img src={resultImage} alt={resultPet.speciesName} className="sp-pixel" />
                        ) : (
                          <Icon name="paw" size={32} />
                        )}
                      </span>
                      <div className="sp-result-text">
                        <strong>{resultPet.speciesName}</strong>
                        <div className="sp-tags">
                          <span className={`sp-tag sp-tag--${(resultPet.tier || "c").toLowerCase()}`}>
                            {TIER_LABEL[resultPet.tier] ?? resultPet.tier}
                          </span>
                          <span className="sp-tag">{STAGE_LABEL[resultPet.stage] ?? resultPet.stage}</span>
                          {VARIANT_LABEL[resultPet.variant] && (
                            <span className="sp-tag sp-tag--variant">{VARIANT_LABEL[resultPet.variant]}</span>
                          )}
                        </div>
                        <p>알에서 {resultPet.speciesName}이(가) 태어났어요! 마이룸에서 키워 보세요.</p>
                        <button type="button" className="sp-link" onClick={() => navigate("/myroom")}>
                          마이룸 가기 <Icon name="chevronRight" size={14} />
                        </button>
                      </div>
                    </div>
                  ) : (
                    <p className="sp-empty">알을 개봉하면 여기에 새 친구가 나타나요.</p>
                  )}
                </section>
              </div>

              <p className="sp-footnote">
                <Icon name="checkCircle" size={14} />
                부화한 반려동물은 마이룸에서 언제든 함께 지낼 수 있어요
              </p>
            </div>
          ) : (
            <FurnitureBrowser
              id="shop-panel-furniture"
              labelledBy="shop-tab-furniture"
              products={shelfFurniture}
              loading={furnitureLoading}
              error={furnitureError}
              ownedCountByName={ownedCountByName}
              ownedTotal={myFurniture.length}
              gameMoney={gameMoney}
              unlimitedCoins={unlimitedCoins}
              busy={busy}
              onBuy={(product) =>
                setConfirm({ kind: "furniture", product, image: furnitureVisual(product.category).image })
              }
            />
          )}
        </div>

        {confirm && (
          <BuySheet
            product={confirm.product}
            image={confirm.image}
            gameMoney={gameMoney}
            unlimitedCoins={unlimitedCoins}
            busy={busy !== null}
            onCancel={() => setConfirm(null)}
            onConfirm={confirmBuy}
          />
        )}

        {historyOpen && <PurchaseHistoryModal onClose={() => setHistoryOpen(false)} />}
      </main>
    </AppShell>
  );
}

// 받침 있는 이름이면 「을」, 없으면 「를」 — "책상을 살까요?" / "소파를 살까요?"
function objectParticle(word = "") {
  const last = word.trim().slice(-1);
  const code = last.charCodeAt(0) - 0xac00;
  if (code < 0 || code > 11171) return "을(를)";
  return code % 28 === 0 ? "를" : "을";
}

// 구매 확인 — 폰은 아래에서 올라오는 시트, PC 는 가운데 창. Esc·바깥 누르기·취소로 닫는다.
function BuySheet({ product, image, gameMoney, unlimitedCoins, busy, onCancel, onConfirm }) {
  const panelRef = useRef(null);
  useEffect(() => {
    const onKey = (e) => e.key === "Escape" && !busy && onCancel();
    document.addEventListener("keydown", onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    panelRef.current?.focus();
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = prev;
    };
  }, [onCancel, busy]);

  const after = gameMoney - product.price;
  return (
    <div className="sp-sheet-backdrop" onMouseDown={(e) => e.target === e.currentTarget && !busy && onCancel()}>
      <div
        className="sp-sheet"
        role="dialog"
        aria-modal="true"
        aria-labelledby="sp-sheet-title"
        tabIndex={-1}
        ref={panelRef}
      >
        <span className="sp-sheet-grip" aria-hidden />
        <h2 id="sp-sheet-title">
          {product.name}
          {objectParticle(product.name)} 살까요?
        </h2>
        <span className="sp-tile sp-tile--xl">
          {image ? <img src={image} alt="" className="sp-pixel" /> : <Icon name="sofa" size={40} />}
        </span>
        <dl className="sp-sheet-sum">
          <div className="sp-sheet-price">
            <dt>가격</dt>
            <dd>
              <CoinIcon size={15} />
              {coin(product.price)}
            </dd>
          </div>
          <div>
            <dt>지금 코인</dt>
            <dd>{unlimitedCoins ? "무제한" : num(gameMoney)}</dd>
          </div>
          <div>
            <dt>구매 후</dt>
            <dd>{unlimitedCoins ? "무제한" : num(after)}</dd>
          </div>
        </dl>
        <div className="sp-sheet-actions">
          <button type="button" className="sp-btn sp-btn--soft" onClick={onCancel} disabled={busy}>
            취소
          </button>
          <button type="button" className="sp-btn sp-btn--dark" onClick={onConfirm} disabled={busy}>
            {busy ? "구매 중…" : "구매하기"}
          </button>
        </div>
      </div>
    </div>
  );
}

export default ShopPage;
