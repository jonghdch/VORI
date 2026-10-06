import { del, get, post } from "./http";

export const getSpendingPlan = (yearMonth) =>
  get(`/spending-plan${yearMonth ? `?yearMonth=${encodeURIComponent(yearMonth)}` : ""}`);
export const getFixedExpenses = () => get("/spending-plan/fixed-expenses");
export const addFixedExpense = (name, amount) =>
  post("/spending-plan/fixed-expenses", { name, amount });
export const deleteFixedExpense = (id) => del(`/spending-plan/fixed-expenses/${id}`);
