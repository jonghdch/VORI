// 공통 fetch 래퍼(http.js) — 서버 응답이 없을 때의 문구, 조회 시간 제한, 오류 처리.
// fetch 는 가짜로 바꿔 서버 없이 돈다.
import {
  get,
  post,
  upload,
  GET_TIMEOUT_MS,
  NETWORK_MESSAGE,
  SESSION_EXPIRED_EVENT,
  TIMEOUT_MESSAGE,
} from "./http";

// fetch 가 돌려주는 응답 흉내 — handle() 이 쓰는 ok·status·json·text 만.
const response = (status, body) => ({
  ok: status >= 200 && status < 300,
  status,
  json: async () => body,
  text: async () => (body === undefined ? "" : JSON.stringify(body)),
});

// 응답하지 않는 서버 — 시간 제한으로 끊기면(abort) fetch 처럼 AbortError 로 끝난다.
const hangingFetch = () =>
  jest.fn(
    (url, init = {}) =>
      new Promise((_, reject) => {
        init.signal?.addEventListener("abort", () =>
          reject(new DOMException("The user aborted a request.", "AbortError")),
        );
      }),
  );

afterEach(() => {
  jest.useRealTimers();
  delete global.fetch;
});

test("서버에 닿지 못하면 영어(Failed to fetch) 대신 한국어 문구, status 는 비운다", async () => {
  global.fetch = jest.fn().mockRejectedValue(new TypeError("Failed to fetch"));

  const err = await get("/home").catch((e) => e);

  expect(err.message).toBe(NETWORK_MESSAGE);
  expect(err.status).toBeUndefined();
});

test("업로드(영수증)도 서버에 닿지 못하면 같은 한국어 문구", async () => {
  global.fetch = jest.fn().mockRejectedValue(new TypeError("Failed to fetch"));

  await expect(upload("/receipts", new Blob(["x"]))).rejects.toThrow(NETWORK_MESSAGE);
});

test("조회(GET)는 GET_TIMEOUT_MS 가 지나면 끊고 「응답이 늦어요」", async () => {
  jest.useFakeTimers();
  global.fetch = hangingFetch();

  const pending = get("/home").catch((e) => e);
  jest.advanceTimersByTime(GET_TIMEOUT_MS);
  const err = await pending;

  expect(err.message).toBe(TIMEOUT_MESSAGE);
});

test("저장(POST)은 시간 제한을 걸지 않는다 — 화면에서 끊어도 서버는 저장해 두 번 저장될 수 있어서", async () => {
  jest.useFakeTimers();
  global.fetch = hangingFetch();

  let settled = false;
  post("/expenses", { amount: 1000 }).finally(() => {
    settled = true;
  });
  jest.advanceTimersByTime(GET_TIMEOUT_MS * 10);
  await Promise.resolve();

  expect(global.fetch.mock.calls[0][1].signal).toBeUndefined();
  expect(settled).toBe(false);
});

test("서버가 준 message 를 상태 코드 문구보다 먼저 쓰고 status 를 싣는다", async () => {
  global.fetch = jest.fn().mockResolvedValue(response(409, { message: "키우는 펫이 있어서 알을 열 수 없어요" }));

  const err = await post("/eggs/1/open").catch((e) => e);

  expect(err.message).toBe("키우는 펫이 있어서 알을 열 수 없어요");
  expect(err.status).toBe(409);
});

test("401 이면 세션 만료 신호를 보낸다 — App 이 로그인 화면으로 보낸다", async () => {
  global.fetch = jest.fn().mockResolvedValue(response(401));
  const onExpired = jest.fn();
  window.addEventListener(SESSION_EXPIRED_EVENT, onExpired);

  const err = await get("/home").catch((e) => e);

  window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired);
  expect(onExpired).toHaveBeenCalledTimes(1);
  expect(err.status).toBe(401);
  expect(err.message).toBe("로그인이 필요합니다");
});

test("정상 응답은 본문을 돌려주고, 빈 본문(204)은 null", async () => {
  global.fetch = jest.fn().mockResolvedValueOnce(response(200, { coins: 3 })).mockResolvedValueOnce(response(204));

  await expect(get("/me")).resolves.toEqual({ coins: 3 });
  await expect(get("/pets/active")).resolves.toBeNull();
});
