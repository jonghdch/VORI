// 업적 API 클라이언트. (서버 코드·테이블 이름은 옛 "titles" 그대로다.)
// 칭호는 펫이 얻는 것으로 바뀌어 api/petTitles.js 가 따로 다룬다.
//   GET /api/achievements          업적 획득·미획득 전부 (미획득은 진행률 포함, 달성 근접 순)
//   PUT /api/achievements/equipped 업적 장착 (최대 3개, 내 정보 상자 3칸)
import { get, put } from "./http";

/**
 * 업적 한 개. 획득한 것과 못 한 것이 **같은 모양**으로 내려온다 —
 * 목록 하나만 그리면서 "다음에 딸 칭호" 까지 함께 보여줄 수 있다.
 *
 * @typedef {{
 *   id: number|null,         // 획득한 업적만 값이 있다.
 *   code: string,            // "RECORD_START" 같은 고정 코드. 표시 문구가 바뀌어도 이건 안 바뀌므로 key 로 쓸 것
 *   name: string,            // "기록의 시작"
 *   description: string,     // "지출 10건 기록"
 *   acquired: boolean,
 *   current: number,         // 현재 지표값 (예: 지출 7건 기록함 → 7)
 *   threshold: number,       // 목표치 (예: 10)
 *   progressPct: number,     // 0~100. 달성 후에도 100 을 넘지 않는다
 *   acquiredAt: string|null, // ISO-8601. 미획득이면 null
 *   hidden: boolean,         // 히든 업적. 못 딴 히든은 description "???", current·threshold 0, progressPct 만 의미 있다
 *   equipOrder: number|null  // 장착 순서(1~3). 장착 안 했으면 null
 * }} Title
 */

/**
 * 업적 전체(획득 + 미획득). 미획득은 달성 근접 순으로 정렬돼 온다.
 *
 * 서버가 **조회 시점에 조건을 다시 평가**하므로, 이벤트를 놓쳐 지급되지 않았던 업적도
 * 이 호출에서 지급된다. 즉 지출을 10건째 기록한 직후 이 목록을 다시 부르면
 * "기록의 시작" 이 acquired:true 로 바뀌어 있다 — 시연 각본 1번이 이걸로 돈다.
 *
 * @returns {Promise<Title[]>}
 */
export const listAchievements = () => get("/achievements");

/** 장착할 수 있는 업적 수(내 정보 상자 3칸). 서버(TitleService.EQUIP_LIMIT)와 같은 값. */
export const EQUIP_LIMIT = 3;

/**
 * 업적을 장착한다. ids 는 획득한 업적의 id, 순서가 칸 순서. 빈 배열이면 모두 장착 해제.
 * 응답은 갱신된 업적 목록.
 * @param {number[]} ids
 * @returns {Promise<Title[]>}
 */
export const equipAchievements = (ids) => put("/achievements/equipped", { ids });
