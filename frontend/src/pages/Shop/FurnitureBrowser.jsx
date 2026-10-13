import { useState } from "react";
import Icon from "../../components/Icon";
import CoinIcon from "../../components/CoinIcon";
import { STAT_LABEL, furnitureVisual } from "../../components/furnitureVisual";

// 가구 상점 — Figma「상점 — VORI」: 테마 칩 줄 + 2열 카드(그림 · 이름 · 가격 · 구매).
// 가구가 30종을 넘어 넘겨 보는 진열대 대신 전부 펼쳐 놓고, 테마·스탯으로 걸러 보게 한다.
// 같은 가구를 여러 개 살 수 있어서, 가진 가구도 「보유 N」 표시만 붙이고 구매 버튼은 그대로 둔다.

const ALL = "ALL";
// themeName 이 null 인 가구를 묶는 칩의 키 — 실제 테마 이름과 겹치지 않는 값
const NO_THEME = "__none__";

const num = (n) => (n ?? 0).toLocaleString("ko-KR");
const themeKeyOf = (item) => item.themeName ?? NO_THEME;

/**
 * @param {{
 *   products: import("../../api/furniture").FurnitureProduct[], // 벽지·바닥을 뺀 진열 대상
 *   loading: boolean,
 *   error: string|null,
 *   ownedCountByName: Record<string, number>,
 *   ownedTotal: number,
 *   gameMoney: number,
 *   unlimitedCoins: boolean,
 *   busy: string|null,          // ShopPage 의 busy — "furniture:CODE" 면 그 가구를 사는 중
 *   onBuy: (product) => void,   // 구매 확인 창을 연다
 * }} props
 */
function FurnitureBrowser({
  id,
  labelledBy,
  products,
  loading,
  error,
  ownedCountByName,
  ownedTotal,
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
  const themeOptions = [];
  for (const item of products) {
    const key = themeKeyOf(item);
    if (!themeOptions.some((option) => option.key === key)) {
      themeOptions.push({ key, label: item.themeName ?? "테마 없음" });
    }
  }
  // 상품 목록이 바뀌어 고른 테마가 사라졌으면 전체로 되돌린다
  const activeTheme = themeOptions.some((option) => option.key === theme) ? theme : ALL;

  const visible = byStat.filter((item) => activeTheme === ALL || themeKeyOf(item) === activeTheme);
  const filtered = activeTheme !== ALL || activeStat !== ALL;

  const chip = (key, label) => {
    const on = activeTheme === key;
    return (
      <button
        key={key}
        type="button"
        className={`sp-chip ${on ? "is-active" : ""}`}
        aria-pressed={on}
        onClick={() => setTheme(key)}
      >
        {on && <Icon name="check" size={13} strokeWidth={2.4} />}
        {label}
      </button>
    );
  };

  return (
    <div className="sp-panel" id={id} role="tabpanel" aria-labelledby={labelledBy}>
      {error ? (
        <p className="sp-state sp-state--error">{error}</p>
      ) : loading ? (
        <p className="sp-state">가구를 불러오는 중…</p>
      ) : (
        <>
          {/* 테마 칩 — 폰에서는 옆으로 밀어 본다 */}
          <div className="sp-chips" role="group" aria-label="테마로 골라 보기">
            {chip(ALL, "전체")}
            {themeOptions.map((option) => chip(option.key, option.label))}
          </div>

          <div className="sp-fur-meta">
            <p role="status">
              가구 {visible.length}개 · 보유 {ownedTotal}개
            </p>
            {statOptions.length > 1 && (
              <select
                className="sp-select"
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
            <div className="sp-empty">
              <p>조건에 맞는 가구가 없어요.</p>
              {filtered && (
                <button
                  type="button"
                  className="sp-link"
                  onClick={() => {
                    setTheme(ALL);
                    setStat(ALL);
                  }}
                >
                  전체 가구 보기
                </button>
              )}
            </div>
          ) : (
            <ul className="sp-fur-grid" aria-label="가구 목록">
              {visible.map((item) => {
                const affordable = unlimitedCoins || gameMoney >= item.price;
                const isBusy = busy === `furniture:${item.code}`;
                const owned = ownedCountByName[item.name] || 0;
                const { image } = furnitureVisual(item.category);
                return (
                  <li key={item.code} className={`sp-card sp-fur ${item.locked ? "is-locked" : ""}`}>
                    <span className="sp-fur-art">
                      {image ? (
                        <img src={image} alt={item.name} className="sp-pixel" />
                      ) : (
                        <Icon name="sofa" size={36} />
                      )}
                      {owned > 0 && <span className="sp-fur-owned">보유 {owned}</span>}
                    </span>
                    <h2>{item.name}</h2>
                    <span className="sp-fur-price">
                      <CoinIcon size={13} />
                      {num(item.price)} 코인
                    </span>
                    <small className="sp-fur-sub">
                      {STAT_LABEL[item.statTarget] ?? item.statTarget} · 배웅 +{item.releaseBonusPct}%
                      {item.themeName && Number(item.themeSetBonusPct) > 0 && ` · 세트 +${item.themeSetBonusPct}%`}
                    </small>
                    {item.locked ? (
                      <small className="sp-fur-lock">
                        <Icon name="lock" size={12} /> 칭호 "{item.unlockTitleName ?? "?"}" 획득 시 해금
                      </small>
                    ) : (
                      !affordable && <small className="sp-fur-short">코인 {num(item.price - gameMoney)} 부족</small>
                    )}
                    <button
                      type="button"
                      className="sp-fur-buy"
                      disabled={item.locked || !affordable || busy !== null}
                      onClick={() => onBuy(item)}
                    >
                      {item.locked && <Icon name="lock" size={13} />}
                      {isBusy ? "구매 중…" : item.locked ? "잠김" : "구매"}
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
