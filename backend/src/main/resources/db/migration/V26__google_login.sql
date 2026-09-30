-- 구글 로그인(Google Identity Services) 도입.
--
-- 구글 계정의 고유 식별자(ID 토큰의 sub)를 users 에 보관한다. 이메일이 아니라 sub 로 매칭하는
-- 이유: 구글 계정의 이메일은 바뀔 수 있지만 sub 는 계정이 살아 있는 한 고정이다.
--
-- password_hash 를 NULL 허용으로 바꾼다. 구글로만 가입한 계정은 비밀번호가 없다.
-- 기존 이메일 계정에 구글을 연결하면 두 컬럼이 같이 채워져 두 방식 모두로 로그인할 수 있다.
ALTER TABLE users
  ADD COLUMN google_sub VARCHAR(64) NULL AFTER password_hash,
  MODIFY COLUMN password_hash VARCHAR(255) NULL,
  ADD UNIQUE KEY uq_users_google_sub (google_sub);
