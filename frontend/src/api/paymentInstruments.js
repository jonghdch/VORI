// 내 카드·계좌(결제수단) API — 환경설정 > 카드·계좌 탭과 지출 기록 화면의 결제 수단 칩이 쓴다.
// 서버는 { id, kind, issuer, name, openingBalance } 를 준다. group(카드/계좌/예적금)은 kind 로 프론트에서 정한다.
import { del, get, post } from "./http";
import { INSTRUMENT_KINDS } from "../constants/issuers";

const withGroup = (i) => ({ ...i, group: INSTRUMENT_KINDS[i.kind]?.group || "CARD" });

/** 보관하지 않은 카드·계좌, 등록 순. */
export async function listInstruments() {
  const list = await get("/payment-instruments");
  return Array.isArray(list) ? list.map(withGroup) : [];
}

/** @param {{kind:string, issuer:string, name:string, openingBalance?:number}} input */
export async function createInstrument(input) {
  return withGroup(await post("/payment-instruments", input));
}

/** 삭제 = 보관. 이 카드로 적은 지난 지출은 연결을 그대로 둔다. */
export const archiveInstrument = (id) => del(`/payment-instruments/${id}`);
