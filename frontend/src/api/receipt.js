// 영수증 OCR API 클라이언트.
//   POST /api/receipts        사진 업로드 → 상호·날짜·금액·품목 추출 (동기. 원본 9~31초, 축소 후 6~13초)
//   GET  /api/receipts        내 인식 이력 (최신순, 요약만)
//   GET  /api/receipts/{id}   단건 — 품목 목록까지
//
// 인식 결과를 서버가 지출로 만들어 주지는 않는다. 화면이 값을 채워 보여주고,
// 사용자가 확인·수정한 뒤 기존 지출 등록 API 를 호출하는 흐름이다.
import { get, upload } from "./http";

/** 업로드 상한 — 서버 multipart 설정(10MB)과 맞춘다. 넘으면 413 이고 안내 문구가 없다. */
export const MAX_RECEIPT_BYTES = 10 * 1024 * 1024;

/**
 * 업로드 전 축소 기준 — 긴 변(px).
 *
 * 실측(2026-09-17, 각 2회):
 * - 노이즈가 많은 합성 5.2MB(3024×4032) — 원본 평균 25.5초, 2048px(0.32MB) 10.7초, 1280px(0.07MB) 9.0초
 * - 그림자·구김·EXIF 방향을 재현한 합성 3.72MB 휴대폰 사진 — 원본 13.8초, 축소 후(0.40MB) 11.6초
 * - 실물 영수증(iPhone, 1.99MB) — 원본 평균 12.2초, 2048px(0.47MB) 6.0초, 인식 결과 동일
 * 인식 결과는 모든 크기에서 같았다. 단축 폭은 사진에 따라 크게 달라서, 두 번째 사진의 차이는
 * 측정 편차 수준이다. 확실한 효과는 전송 용량이다(3.72MB → 0.40MB, 89% 감소).
 *
 * 1280px 이 조금 더 빨랐지만 글자가 선명한 합성 이미지 기준이라, 흔들리거나 그림자 진
 * 실제 사진의 판독 여유를 두고 2048px 을 골랐다.
 */
export const RECEIPT_MAX_SIDE = 2048;
const RECEIPT_JPEG_QUALITY = 0.85;

/**
 * 영수증 사진을 업로드하기 전에 긴 변 {@link RECEIPT_MAX_SIDE}px 로 줄인다.
 *
 * - 이미 기준 이하면 원본을 그대로 쓴다. 다시 압축해 봐야 화질만 잃는다.
 *   줄였는데 오히려 커지는 경우(이미 강하게 압축된 사진)도 원본을 쓴다.
 * - 휴대폰 사진은 픽셀을 눕혀 저장하고 EXIF 방향값으로 세워 보여준다. `<img>` 로 디코딩하면
 *   브라우저가 방향을 반영해 주므로 캔버스에 그린 결과도 똑바로 선다.
 * - 디코딩이나 인코딩이 실패하면(예: 브라우저가 열지 못하는 HEIC) 원본을 돌려준다.
 *   축소는 최적화일 뿐이라, 그 실패가 업로드 실패로 이어지면 안 된다.
 *
 * @param {File} file
 * @returns {Promise<File>} 축소한 JPEG, 또는 원본 그대로
 */
export async function prepareReceiptImage(file) {
  let img;
  try {
    img = await loadImage(file);
  } catch {
    return file;
  }

  const scale = RECEIPT_MAX_SIDE / Math.max(img.naturalWidth, img.naturalHeight);
  if (!(scale < 1)) return file;

  const width = Math.round(img.naturalWidth * scale);
  const height = Math.round(img.naturalHeight * scale);
  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext("2d");
  // PNG 의 투명 영역은 JPEG 로 바꾸면 검게 칠해진다. 영수증 종이에 가까운 흰색을 먼저 깐다.
  ctx.fillStyle = "#fff";
  ctx.fillRect(0, 0, width, height);
  ctx.drawImage(img, 0, 0, width, height);

  const blob = await new Promise((resolve) =>
    canvas.toBlob(resolve, "image/jpeg", RECEIPT_JPEG_QUALITY),
  );
  if (!blob || blob.size >= file.size) return file;

  const name = file.name.replace(/\.[^.]*$/, "") + ".jpg";
  return new File([blob], name, { type: "image/jpeg", lastModified: file.lastModified });
}

function loadImage(file) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      URL.revokeObjectURL(url);
      resolve(img);
    };
    img.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error("이미지를 열 수 없어요"));
    };
    img.src = url;
  });
}

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
 * 영수증 사진 업로드. **원본을 그대로 보내면 9~31초**(4.4MB 휴대폰 사진 30.5초),
 * {@link prepareReceiptImage} 로 줄여 보내면 6~13초 걸렸다. 호출 전에 줄이는 것을 권장한다.
 * 화면은 반드시 진행 상태를 보여줄 것 — 아무 표시 없이 기다리게 하면 멈춘 것처럼 보인다.
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
