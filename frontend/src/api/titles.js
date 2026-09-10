// 칭호 API 클라이언트.
//   GET /api/titles          획득·미획득 전부 (미획득은 진행률 포함, 달성 근접 순)
//   PUT /api/titles/active   장착 / 해제
import { get, put } from "./http";

/**
 * 칭호 한 개. 획득한 것과 못 한 것이 **같은 모양**으로 내려온다 —
 * 목록 하나만 그리면서 "다음에 딸 칭호" 까지 함께 보여줄 수 있다.
 *
 * @typedef {{
 *   id: number|null,         // 획득한 칭호만 값이 있다. setActiveTitle 에 넘기는 값.
 *   code: string,            // "RECORD_START" 같은 고정 코드. 표시 문구가 바뀌어도 이건 안 바뀌므로 key 로 쓸 것
 *   name: string,            // "기록의 시작"
 *   description: string,     // "지출 10건 기록"
 *   acquired: boolean,
 *   active: boolean,         // 지금 장착 중인지
 *   current: number,         // 현재 지표값 (예: 지출 7건 기록함 → 7)
 *   threshold: number,       // 목표치 (예: 10)
 *   progressPct: number,     // 0~100. 달성 후에도 100 을 넘지 않는다
 *   acquiredAt: string|null  // ISO-8601. 미획득이면 null
 * }} Title
 */

/**
 * 칭호 전체(획득 + 미획득). 미획득은 달성 근접 순으로 정렬돼 온다.
 *
 * 서버가 **조회 시점에 조건을 다시 평가**하므로, 이벤트를 놓쳐 지급되지 않았던 칭호도
 * 이 호출에서 지급된다. 즉 지출을 10건째 기록한 직후 이 목록을 다시 부르면
 * "기록의 시작" 이 acquired:true 로 바뀌어 있다 — 시연 각본 1번이 이걸로 돈다.
 *
 * @returns {Promise<Title[]>}
 */
export const listTitles = () => get("/titles");

/**
 * 홈 화면용 — 한 번 호출해서 필요한 세 가지를 한꺼번에 뽑는다.
 *
 * 홈은 "최근 업적" 카드와 펫 이름 옆 칭호 배지 두 곳이 같은 데이터를 쓴다.
 * 각각 listTitles() 를 부르면 같은 요청이 두 번 나가므로 이 헬퍼를 쓰는 게 낫다.
 *
 * @returns {Promise<{ all: Title[], acquired: Title[], active: Title|null }>}
 */
export const getTitleSummary = () =>
  listTitles().then((titles) => ({
    all: titles,
    acquired: titles.filter((t) => t.acquired),
    active: titles.find((t) => t.active) ?? null,
  }));

/**
 * 칭호 장착. titleId 에 null 을 주면 해제된다(해제용 엔드포인트는 따로 없다).
 *
 * 넘기는 값은 `Title.name` 이 아니라 **`Title.id`** 다. 미획득 칭호는 id 가 null 이라
 * 애초에 장착할 수 없다 — 버튼을 acquired 로 걸러 두면 404/403 을 볼 일이 없다.
 *
 * 204 응답이라 반환값은 없다. 장착 후 화면을 갱신하려면 listTitles() 를 다시 부를 것.
 *
 * @param {number|null} titleId
 * @returns {Promise<null>} 404 = 없는 칭호, 403 = 남의 칭호
 */
export const setActiveTitle = (titleId) => put("/titles/active", { titleId });
