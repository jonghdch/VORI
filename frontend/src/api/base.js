// 백엔드 API 베이스 URL — 단일 정의.
// 배포 시 .env 의 REACT_APP_API_BASE 로 교체 (CRA 는 REACT_APP_ 접두사만 노출).
// 예: REACT_APP_API_BASE=https://api.vori.example/api
export const API_BASE =
  process.env.REACT_APP_API_BASE || "http://localhost:8080/api";
