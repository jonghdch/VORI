import { API_BASE } from "./base";

async function request(path, options = {}) {
  const res = await fetch(`${API_BASE}${path}`, { credentials: "include", ...options });
  if (res.status === 204) return null;
  const data = await res.json().catch(() => null);
  if (!res.ok) throw new Error(data?.message || "요청을 처리하지 못했어요.");
  return data;
}
export const getSpendingPlan = (yearMonth) =>
  request(`/spending-plan${yearMonth ? `?yearMonth=${encodeURIComponent(yearMonth)}` : ""}`);
export const getFixedExpenses = () => request("/spending-plan/fixed-expenses");
export const addFixedExpense = (name, amount) => request("/spending-plan/fixed-expenses", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ name, amount }) });
export const deleteFixedExpense = (id) => request(`/spending-plan/fixed-expenses/${id}`, { method: "DELETE" });
