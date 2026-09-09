-- 신호등 임계값을 docs/domain.md 스펙값으로 되돌린다.
--
-- V4 가 심은 값(z_red=2.00, z_green=1.00)은 그 이전 코드 상수를 그대로 옮긴 것인데,
-- domain.md 의 정의와 어긋나 있었다.
--
--   domain.md :  GREEN = 절약,  GREEN if z_score <= Z_GREEN (예: -0.5)
--   V4 의 값  :  z_green = 1.00
--
-- 판정식이 `z <= zGreen ? GREEN : (z <= zRed ? GRAY : RED)` 이므로 z_green=1.00 은
-- "평균 + 1 표준편차까지 전부 GREEN" 을 뜻했다. 정규분포 가정 시 약 84% 의 지출이
-- 초록불로 뜨고, 그중 평균을 넘긴 건은 saved_amount 가 음수라 코인·스탯이 0 이다.
-- 즉 화면은 "절약했어요" 인데 보상은 없는 상태였다.
--
-- 스펙값으로 되돌리면 GREEN 은 "평소보다 0.5σ 이상 적게 쓴 경우" 라는 원래 정의를 회복한다.
--
-- 이 테이블은 어드민이 조정하는 값이라 UPDATE 로 넣는다. 이미 관리자가 다른 값으로
-- 바꿔둔 환경을 덮어쓰지 않도록, V4 가 심은 기본값 그대로인 행만 대상으로 한다.
UPDATE signal_config
   SET z_red = 1.50, z_green = -0.50
 WHERE id = 1
   AND z_red = 2.00
   AND z_green = 1.00;
