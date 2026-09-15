SET NAMES utf8mb4;

-- 로그인 횟수 누적 — "최초 1회 로그인" 조건을 판정하려면 로그인 이벤트를 세어야 한다.
-- AuthController.login() 성공 시 UserService.recordLogin() 이 이 값을 올린다.
ALTER TABLE users
  ADD COLUMN login_count INT DEFAULT 0 AFTER total_saved;

-- titles.metric_type 에 LOGIN_COUNT 추가 (TitleMetricType 과 짝).
ALTER TABLE titles
  MODIFY COLUMN metric_type ENUM(
    'TOTAL_SAVED','EXPENSE_COUNT','GOALS_ACHIEVED','PETS_RELEASED',
    'S_TIER_PETS','AI_ANSWERS','RECEIPT_SCANS','LOGIN_COUNT'
  ) NOT NULL;

-- 신규 업적: 절약의 첫걸음 (최초 1회 로그인) → 칭호 「보리의 새친구」
-- sort_order 5 — 온보딩 성격이라 기존 목록(10~) 보다 앞에 노출.
INSERT INTO titles (code, name, description, metric_type, threshold, enabled, sort_order, created_at, updated_at) VALUES
  ('LOGIN_FIRST', '보리의 새친구', '절약의 첫걸음 · 최초 1회 로그인', 'LOGIN_COUNT', 1, TRUE, 5, NOW(), NOW());
