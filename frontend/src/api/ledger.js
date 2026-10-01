// 가계부 행 저장 API 클라이언트.
// 작성 화면은 POST /api/ledger/entries 한 번으로 지출·수입·저축을 함께 저장한다.

import { API_BASE } from "./base";

async function post(path, body) {
  const res = await fetch(`${API_BASE}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    let msg = `${res.status}`;
    try {
      const data = await res.json();
      msg = data.message || msg;
    } catch {}
    throw new Error(msg);
  }
  return res.json();
}

async function get(path) {
  const res = await fetch(`${API_BASE}${path}`, {
    method: "GET",
    credentials: "include",
  });
  if (!res.ok) {
    // silent failure 금지 — 401/500 을 빈 배열로 바꾸면 "데이터 없음" 과
    // "조회 실패" 가 구분 불가능해진다. status 를 실어 throw, 처리(401→로그인 유도)는 호출부 책임.
    const err = new Error(
      res.status === 401 ? "로그인이 필요합니다" : `조회 실패 (${res.status})`,
    );
    err.status = res.status;
    throw err;
  }
  return res.json();
}

export const listExpensesByDate = (date) => get(`/expenses?date=${date}`);
export const listIncomesByDate = (date) => get(`/incomes?date=${date}`);
export const listSavingsByDate = (date) => get(`/savings?date=${date}`);

// 월별 가계부 통합 조회 — GET /api/ledger?yearMonth=2026-06.
// 그 달 지출+수입을 날짜 오름차순으로 반환 (LedgerResponse[]).
export const getMonthlyLedger = (yearMonth) =>
  get(`/ledger?yearMonth=${yearMonth}`);

export async function deleteExpense(id) {
  const res = await fetch(`${API_BASE}/ledger/expenses/${id}`, {
    method: "DELETE",
    credentials: "include",
  });
  if (!res.ok) throw new Error(`삭제 실패 (${res.status})`);
}

// 카테고리 트리 (대분류 + 소분류). 가계부 확인 화면에서 categoryId → 이름 매핑용.
export const listCategoryTree = () => get(`/categories`);

// 작성 화면의 지출·수입·저축을 한 트랜잭션으로 저장한다. 하나라도 실패하면 아무것도 저장되지 않는다.
// 지출 행에 id 가 있으면 수정, 없으면 등록.
export function saveLedgerEntries({ expenses, incomes, savings }) {
  return post("/ledger/entries", { expenses, incomes, savings });
}
