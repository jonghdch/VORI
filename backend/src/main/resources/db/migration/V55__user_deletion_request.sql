-- 회원 탈퇴 유예(2026-10-08).
-- 탈퇴하면 바로 지우지 않고 요청 시각만 남긴다. 유예 기간(기본 30일) 안에 다시 로그인하면 NULL 로 되돌려 복구하고,
-- 기간이 지나면 AccountDeletionService 의 스케줄러가 계정과 기록을 영구 삭제한다.
-- NULL = 정상 계정, 값 있음 = 탈퇴 대기.
ALTER TABLE users ADD COLUMN deletion_requested_at DATETIME NULL;

-- 스케줄러가 매일 "기간이 지난 탈퇴 대기 계정" 을 찾는다
CREATE INDEX idx_users_deletion_requested_at ON users (deletion_requested_at);
