// 본인 정보 API — GET /api/users/me.
// gameMoney·totalSaved 는 알 구매·지출 등록으로 계속 바뀌므로 세션의 user 객체가 아니라
// 이걸 다시 불러 화면을 갱신한다 (상점 코인 배지 등).
import { get, post, put } from "./http";

/**
 * @returns {Promise<{
 *   id:number, email:string, nickname:string, name:string|null, role:string,
 *   gameMoney:number, totalSaved:number, tutorialDone:boolean,
 *   hasPassword:boolean, accountRestored:boolean
 * }>}
 */
export const getMe = () => get("/users/me");

// 환경설정의 프로필 수정. 변경된 사용자 정보를 받아 헤더에도 바로 반영한다.
export const updateMe = (profile) => put("/users/me", profile);

/**
 * 회원 탈퇴 신청. 이메일 가입자는 { password }, 구글 가입자는 { confirmText: "탈퇴" }.
 * 성공하면 서버가 세션을 끊는다. deleteAt 이 지나면 영구 삭제되고, 그 전에 로그인하면 복구된다.
 * @returns {Promise<{ deleteAt:string }>} 400 = 본인 확인 실패, 403 = 관리자 계정, 409 = 이미 신청함
 */
export const withdraw = (payload) => post("/users/me/withdrawal", payload);


/** 펫(종족·단계·유무)이 바뀐 뒤 마이룸·도감·홈에 다시 읽으라고 알린다. 관리자 도구가 부른다. */
export const PET_CHANGED_EVENT = "vori:pet-changed";
export const notifyPetChanged = () => window.dispatchEvent(new Event(PET_CHANGED_EVENT));
