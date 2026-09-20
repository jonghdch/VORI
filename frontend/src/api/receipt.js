// 영수증 OCR API 클라이언트.
//   POST /api/receipts        사진 업로드 → 상호·날짜·금액·품목 추출 (동기, 18~31초)
//   GET  /api/receipts        내 인식 이력 (최신순, 요약만)
//   GET  /api/receipts/{id}   단건 — 품목 목록까지
//
// 인식 결과를 서버가 지출로 만들어 주지는 않는다. 화면이 값을 채워 보여주고,
// 사용자가 확인·수정한 뒤 기존 지출 등록 API 를 호출하는 흐름이다.
import { get, upload } from "./http";

/** 업로드 상한 — 서버 multipart 설정(10MB)과 맞춘다. 넘으면 413 이고 안내 문구가 없다. */
export const MAX_RECEIPT_BYTES = 10 * 1024 * 1024;

/**
 * OCR 처리 결과 한 건.
 *
 * **`status` 가 `SUCCESS` 여도 개별 값은 null 일 수 있다** — 영수증이 흐려 일부만 읽힌 경우다.
 * 화면은 읽힌 값만 채우고 나머지는 사용자가 입력하게 두면 된다. null 을 오류로 다루지 말 것.
 *
 * @typedef {{
 *   id: number,
 *   status: "PENDING"|"SUCCESS"|"FAILED",
 *   amount: number|null,          // 총 결제금액
 *   date: string|null,            // "YYYY-MM-DD"
 *   item: string|null,            // 가계부 항목명(대표 품목, 없으면 상호명)
 *   extracted: Extracted|null,    // 단건 조회·업로드 응답에만 있다. 목록에서는 null
 *   errorMessage: string|null,
 *   requestedAt: string,
 *   completedAt: string|null,
 *   expenseId: number|null        // 이 인식으로 만든 지출이 있으면
 * }} Receipt
 *
 * @typedef {{
 *   storeName: string|null,
 *   date: string|null,
 *   time: string|null,
 *   totalAmount: number|null,
 *   items: Array<{ name: string|null, quantity: number|null, amount: number|null }>|null,
 *   paymentMethod: string|null,   // CASH|CREDIT|DEBIT|TRANSFER|MOBILE_PAY|UNKNOWN
 *   representativeItem: string|null
 * }} Extracted
 */

/**
 * 영수증 사진 업로드. **응답까지 18~31초 걸린다**(4.4MB 휴대폰 사진 실측 30.5초).
 * 화면은 반드시 진행 상태를 보여줄 것 — 아무 표시 없이 30초를 기다리면 멈춘 것처럼 보인다.
 *
 * 영수증이 아니거나 판독이 안 되면 모델이 모든 값을 null 로 채운다. 그래도 200 이므로
 * `amount == null && item == null` 이면 "읽지 못했다" 로 안내해야 한다.
 *
 * @param {File} file
 * @returns {Promise<Receipt>} 413 = 10MB 초과, 400 = 파일 없음/형식 오류
 */
export const uploadReceipt = (file) => upload("/receipts", file);

/** 내 인식 이력(최신순). `extracted` 는 null 이고 요약 필드만 온다. @returns {Promise<Receipt[]>} */
export const listReceipts = () => get("/receipts");

/** 단건 — 품목 목록(`extracted.items`)까지. @returns {Promise<Receipt>} 404 = 없거나 남의 것 */
export const getReceipt = (id) => get(`/receipts/${id}`);
