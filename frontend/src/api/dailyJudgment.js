import { API_BASE } from "./base";

async function handle(res) {
  if (!res.ok) {
    let message = `판정 요청 실패 (${res.status})`;
    try {
      const data = await res.json();
      if (data?.message) message = data.message;
    } catch {}
    throw new Error(message);
  }
  if (res.status === 204) return null;
  return res.json();
}

export const getTodayJudgment = () =>
  fetch(`${API_BASE}/daily-judgments/today`, { credentials: "include" }).then(handle);

export const startTodayJudgment = () =>
  fetch(`${API_BASE}/daily-judgments/today`, {
    method: "POST",
    credentials: "include",
  }).then(handle);
