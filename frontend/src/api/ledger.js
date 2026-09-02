// 가계부 행 저장 API 클라이언트.
// 백엔드 3개 endpoint (POST /api/expenses, /api/incomes, /api/savings) 호출.

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

// 카테고리 트리 (대분류 + 소분류). 가계부 확인 화면에서 categoryId → 이름 매핑용.
export const listCategoryTree = () => get(`/categories`);

export function createExpense({ item, amount, categoryId, paymentMethod, spentAt }) {
  return post("/expenses", {
    item,
    amount: Number(amount),
    categoryId,
    paymentMethod,
    spentAt,
  });
}

export function createIncome({ item, amount, source, paymentMethod, receivedAt }) {
  return post("/incomes", {
    item,
    amount: Number(amount),
    source,
    paymentMethod,
    receivedAt,
  });
}

export function createSaving({ item, amount, savingType, savedAt }) {
  return post("/savings", {
    item,
    amount: Number(amount),
    savingType,
    savedAt,
  });
}
