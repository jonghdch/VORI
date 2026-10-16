// 공통 fetch 래퍼 — 세션 쿠키 포함, 실패 시 status 를 실은 Error 를 던진다.
//
// silent failure 금지: 401/500 을 null 로 삼키면 "데이터 없음" 과 "조회 실패" 가
// 구분되지 않는다. 처리(401 → 로그인 유도, 400 → 안내 문구)는 호출부 책임.
import { API_BASE } from "./base";

// 백엔드가 GlobalExceptionHandler 로 사용자에게 보여줄 한글 message 를 본문에 실어 준다.
// 그러므로 message 가 있으면 그걸 쓰는 게 원칙이다 — 아래 표는 message 가 없는 경우
// (Security 필터가 만드는 401 등)에만 쓰는 최후 문구다.
//
// 호출부에서 상태 코드로 문구를 정하지 말 것. 같은 코드에 사유가 추가되면 틀린 안내가 나간다.
// 실제로 개봉 409 가 "이미 개봉한 알" 하나였다가 "키우던 펫이 있음" 이 추가되면서 그랬다.
const STATUS_MESSAGE = {
  400: "요청을 처리할 수 없어요",
  401: "로그인이 필요합니다",
  403: "권한이 없어요",
  404: "찾을 수 없어요",
  409: "이미 처리된 요청이에요",
};

// 로그인한 채 쓰던 중 세션이 끝났다는 신호(서버 재시작·세션 만료·탈퇴 대기). App 이 듣고 로그아웃 상태로
// 바꿔 로그인 화면으로 보낸다. 이게 없으면 화면은 로그인된 줄 알고 요청마다 실패를 보여 주고,
// 닫는 길이 없는 펫 이름 팝업 같은 화면에 갇힌다. 각 화면의 401 처리는 그대로 둔다(같이 돌아도 된다).
// 로그인·구글 로그인의 401(비밀번호 틀림)은 auth.js 가 따로 부르므로 여기로 오지 않는다.
export const SESSION_EXPIRED_EVENT = "vori:session-expired";

// 응답을 못 받았을 때의 문구. fetch 는 서버에 닿지 못하면 영어 오류("Failed to fetch")를 던지는데,
// 화면 여러 곳이 err.message 를 그대로 보여 줘서 영어가 나갔다. status 는 비워 둔다 — 응답이 없었다는 뜻이고,
// categorize.js 는 이걸 보고 한 번 더 시도한다.
export const NETWORK_MESSAGE = "서버에 연결하지 못했어요. 인터넷 연결을 확인하고 잠시 후 다시 시도해 주세요.";
export const TIMEOUT_MESSAGE = "서버 응답이 늦어요. 잠시 후 다시 시도해 주세요.";

// 조회(GET)만 시간 제한을 둔다. 저장 요청은 화면에서 끊어도 서버는 끝까지 처리해서, 「실패」를 보고 다시 누르면
// 두 번 저장될 수 있다. Gemini 를 기다리는 요청(펫 대화·사유 분류·카테고리 분류·영수증)은 모두 POST 라 여기 걸리지
// 않는다 — 서버는 재시도·대체 모델까지 최악 수 분을 기다린다. 조회는 모두 DB 만 읽는다.
export const GET_TIMEOUT_MS = 15000;

/**
 * fetch 를 보내고 read(res) 로 응답을 읽는다. 서버에 닿지 못했거나 GET 이 GET_TIMEOUT_MS 안에 끝나지 않으면
 * 한국어 문구의 Error 를 던진다. 본문을 읽는 동안에도 시간 제한이 걸리게 read 까지 함께 감싼다.
 * auth.js·admin.js 처럼 이 파일의 get/post 를 쓰지 않는 곳도 이걸로 보낸다.
 */
export async function send(url, init = {}, read = (res) => res) {
  const isGet = (init.method || "GET").toUpperCase() === "GET";
  const controller = isGet ? new AbortController() : null;
  const timer = controller && setTimeout(() => controller.abort(), GET_TIMEOUT_MS);
  try {
    let res;
    try {
      res = await fetch(url, controller ? { ...init, signal: controller.signal } : init);
    } catch {
      throw new Error(controller?.signal.aborted ? TIMEOUT_MESSAGE : NETWORK_MESSAGE);
    }
    try {
      return await read(res);
    } catch (e) {
      if (controller?.signal.aborted) throw new Error(TIMEOUT_MESSAGE);
      throw e;
    }
  } finally {
    clearTimeout(timer);
  }
}

// 응답 → 값, 또는 status 를 실은 Error. JSON 요청과 multipart 업로드가 함께 쓴다.
async function handle(res) {
  if (!res.ok) {
    if (res.status === 401) window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
    let msg = STATUS_MESSAGE[res.status] || `요청 실패 (${res.status})`;
    try {
      const data = await res.json();
      if (data?.message) msg = data.message;
    } catch {}
    const err = new Error(msg);
    err.status = res.status;
    throw err;
  }

  // 204 No Content 또는 본문 "null"(펫 없음 등) 은 null 로.
  if (res.status === 204) return null;
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

// notify: 끝난 뒤 vori:account-updated 를 보낼지. 기본은 GET 이 아닌 요청(서버 상태를 바꿈).
async function request(path, { method = "GET", body, notify = method !== "GET" } = {}) {
  const result = await send(
    `${API_BASE}${path}`,
    {
      method,
      credentials: "include",
      headers: body !== undefined ? { "Content-Type": "application/json" } : undefined,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    },
    handle,
  );
  if (notify) window.dispatchEvent(new Event("vori:account-updated"));
  return result;
}

export const get = (path) => request(path);
export const post = (path, body) => request(path, { method: "POST", body });
export const put = (path, body) => request(path, { method: "PUT", body });
// PATCH 는 본문 없이 부르는 경우가 있다(가구 회수). body 를 넘기지 않으면 Content-Type 도 안 붙는다.
export const patch = (path, body) => request(path, { method: "PATCH", body });
export const del = (path) => request(path, { method: "DELETE" });
// 본문이 필요해 POST 로 보내지만 서버 상태는 바꾸지 않는 조회(카테고리 분류 등).
// 계정 요약·알림 개수를 다시 읽게 하는 이벤트를 보내지 않는다.
export const query = (path, body) => request(path, { method: "POST", body, notify: false });

/**
 * multipart/form-data 파일 업로드 (영수증 OCR).
 *
 * Content-Type 을 직접 지정하지 않는다 — FormData 를 주면 브라우저가 boundary 를 포함해
 * 알아서 붙인다. 손으로 "multipart/form-data" 를 넣으면 boundary 가 빠져 서버가 못 읽는다.
 *
 * 서버 상한은 10MB(f5a78ac). 넘기면 413 이 오고 message 는 없을 수 있다 — 호출부에서
 * 미리 걸러 주는 편이 안내가 낫다.
 */
export const upload = (path, file, field = "file") => {
  const form = new FormData();
  form.append(field, file);
  return send(`${API_BASE}${path}`, { method: "POST", credentials: "include", body: form }, handle);
};
