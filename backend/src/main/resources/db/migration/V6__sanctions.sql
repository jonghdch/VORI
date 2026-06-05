-- 유저 제재 기록. 경고/정지/영구정지. 기록·관리용 (로그인 차단은 미연동).
CREATE TABLE sanctions (
  id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT       NOT NULL,
  type        ENUM('WARNING','SUSPENSION','BAN') NOT NULL,
  reason      VARCHAR(255) NOT NULL,
  created_at  DATETIME     NOT NULL,
  expires_at  DATETIME     NULL,   -- 정지(SUSPENSION) 만료 시각. 경고·영구정지는 NULL
  lifted_at   DATETIME     NULL,   -- 조기 해제 시각. NULL 이면 미해제
  INDEX idx_sanctions_user (user_id),
  CONSTRAINT fk_sanctions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
