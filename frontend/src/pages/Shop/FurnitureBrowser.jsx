import { useState } from "react";
import { FurnitureArt, STAT_LABEL } from "../../components/furnitureVisual";

// 가구 상점 — 전체 가구를 한 화면에 펼쳐 보는 그리드.
// 3개씩 넘겨 보던 진열대는 가구가 30종을 넘기면서 끝까지 보려면 화살표를 열 번 눌러야 했다.
// 여기서는 전부 펼쳐 놓고, 테마·스탯으로 걸러 보게 한다.

const ALL = "ALL";
// themeName 이 null 인 가구를 묶는 칩의 키 — 실제 테마 이름과 겹치지 않는 값
const NO_THEME = "__none__";

const coin = (n) => `${(n ?? 0).toLocaleString("ko-KR")} 코인`;
const themeKeyOf = (item) => item.themeName ?? NO_THEME;

/**
 * @param {{
 *   products: import("../../api/furniture").FurnitureProduct[], // 벽지·바닥을 뺀 진열 대상
 *   loading: boolean,
 *   error: string|null,
 *   ownedCountByName: Record<string, number>,
 *   gameMoney: number,
 *   unlimitedCoins: boolean,
 *   busy: string|null,          // ShopPage 의 busy — "furniture:CODE" 면 그 가구를 사는 중
 *   onBuy: (product) => void,
 * }} props
 */
function FurnitureBrowser({
  id,
  labelledBy,
  products,
  loading,
  error,
  ownedCountByName,
  gameMoney,
  unlimitedCoins,
  busy,
  onBuy,
}) {
  const [theme, setTheme] = useState(ALL);
  const [stat, setStat] = useState(ALL);

  // 스탯 선택지 — 상품에 실제로 있는 스탯만, STAT_LABEL 순서대로
  const statOptions = Object.keys(STAT_LABEL).filter((key) =>
    products.some((item) => item.statTarget === key),
  );
  const activeStat = statOptions.includes(stat) ? stat : ALL;
  const byStat = products.filter((item) => activeStat === ALL || item.statTarget === activeStat);

  // 테마 칩 — 상품에 실제로 있는 테마만, 처음 나온(가장 싼) 순서대로.
  // 개수는 스탯 필터를 거친 뒤의 값이라 칩 숫자와 눌렀을 때 나오는 카드 수가 같다.
  const themeOptions = [];
  for (const item of products) {
    const key = themeKeyOf(item);
    if (!themeOptions.some((option) => option.key === key)) {
      themeOptions.push({ key, label: item.themeName ?? "테마 없음" });
    }
  }
  const countOf = (key) => byStat.filter((item) => themeKeyOf(item) === key).length;
  // 상품 목록이 바뀌어 고른 테마가 사라졌으면 전체로 되돌린다
  const activeTheme = themeOptions.some((option) => option.key === theme) ? theme : ALL;

  const visible = byStat.filter((item) => activeTheme === ALL || themeKeyOf(item) === activeTheme);
  const filtered = activeTheme !== ALL || activeStat !== ALL;

  const resetFilters = () => {
    setTheme(ALL);
    setStat(ALL);
  };

  return (
    <div className="shop-furniture-browser" id={id} role="tabpanel" aria-labelledby={labelledBy}>
      {error ? (
        <p className="shop-shelf-state shop-shelf-state--error">{error}</p>
      ) : loading ? (
        <p className="shop-shelf-state">가구를 불러오는 중…</p>
      ) : (
        <>
          <div className="shop-furniture-chips" role="group" aria-label="테마로 골라 보기">
            <button
              type="button"
              className={`shop-chip ${activeTheme === ALL ? "is-active" : ""}`}
              aria-pressed={activeTheme === ALL}
              onClick={() => setTheme(ALL)}
            >
              전체<span className="shop-chip-count">{byStat.length}</span>
            </button>
            {themeOptions.map((option) => (
              <button
                key={option.key}
                type="button"
                className={`shop-chip ${activeTheme === option.key ? "is-active" : ""}`}
                aria-pressed={activeTheme === option.key}
                onClick={() => setTheme(option.key)}
              >
                {option.label}
                <span className="shop-chip-count">{countOf(option.key)}</span>
              </button>
            ))}
          </div>

          <div className="shop-furniture-meta">
            <p className="shop-furniture-count" role="status">
              가구 {visible.length}개
            </p>
            {statOptions.length > 1 && (
              <select
                className="shop-furniture-stat"
                aria-label="올려 주는 스탯으로 골라 보기"
                value={activeStat}
                onChange={(e) => setStat(e.target.value)}
              >
                <option value={ALL}>스탯 전체</option>
                {statOptions.map((key) => (
                  <option key={key} value={key}>
                    {STAT_LABEL[key]}
                  </option>
                ))}
              </select>
            )}
          </div>

          {visible.length === 0 ? (
            <div className="shop-furniture-empty">
              <p>조건에 맞는 가구가 없어요.</p>
              {filtered && (
                <button type="button" className="home-link-btn" onClick={resetFilters}>
                  전체 가구 보기
                </button>
              )}
            </div>
          ) : (
            // 카드가 많으면 이 안에서만 세로로 넘긴다 — 가게 배경이 늘어나 깨지지 않게.
            // tabIndex 는 구매 버튼이 전부 비활성일 때도 키보드로 스크롤할 수 있게 하려는 것.
            <ul className="shop-furniture-grid" tabIndex={0} aria-label="가구 목록">
              {visible.map((item) => {
                const affordable = unlimitedCoins || gameMoney >= item.price;
                const isBusy = busy === `furniture:${item.code}`;
                const owned = ownedCountByName[item.name] || 0;
                return (
                  <li
                    key={item.code}
                    className={`shop-furniture-card ${item.locked ? "is-locked" : ""}`}
                  >
                    <div className="shop-furniture-card-art">
                      <FurnitureArt
                        category={item.category}
                        name={item.name}
                        className="shop-furniture-card-image"
                        emojiClassName="shop-furniture-card-emoji"
                      />
                    </div>
                    <h2>
                      {item.name}
                      {owned > 0 && <em className="shop-furniture-card-owned">보유 {owned}</em>}
                    </h2>
                    <p>
                      {STAT_LABEL[item.statTarget] ?? item.statTarget} · 배웅 선물 +{item.releaseBonusPct}%
                    </p>
                    {item.themeName && (
                      <small className="shop-furniture-theme">
                        {item.themeName} 테마
                        {/* 분류만 하는 테마(세트 보너스 0%)는 "세트 +0%" 를 붙이지 않는다 */}
                        {Number(item.themeSetBonusPct) > 0 && ` · 세트 +${item.themeSetBonusPct}%`}
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
                      onClick={() => onBuy(item)}
                    >
                      {isBusy ? "구매 중…" : item.locked ? "잠김" : affordable ? "구매하기" : "코인 부족"}
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </>
      )}
    </div>
  );
}

export default FurnitureBrowser;
