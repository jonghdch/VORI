// 백엔드 API 베이스 URL — 단일 정의.
// 다른 주소가 필요하면 .env 의 REACT_APP_API_BASE 로 바꾼다 (CRA 는 REACT_APP_ 접두사만 노출).
// 예: REACT_APP_API_BASE=https://api.vori.example/api
//
// 값이 없을 때: 개발(npm start)은 백엔드 8080 포트, 배포 빌드(npm run build)는 같은 도메인의 /api.
// 배포는 Nginx 가 프론트와 /api 를 한 도메인에서 내보낸다. 배포 빌드 기본값이 localhost 면
// 빌드할 때 값을 빠뜨린 순간 게스트 휴대폰에서 모든 요청이 그 휴대폰 자신(localhost)으로 가 실패한다.
export const API_BASE =
  process.env.REACT_APP_API_BASE ||
  (process.env.NODE_ENV === "production" ? "/api" : "http://localhost:8080/api");
