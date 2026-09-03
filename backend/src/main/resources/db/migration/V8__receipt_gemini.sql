-- 영수증 OCR 을 Google Cloud Vision 대신 Gemini 이미지 입력으로 처리하도록 전환.
--
-- 배경: Vision 은 텍스트 덩어리를 주므로 상호·날짜·금액·품목을 골라내는 KIE 단계를 직접
-- 구현해야 한다. Gemini 는 이미지에서 바로 구조화 JSON 을 반환해 그 단계가 통째로 없어지고,
-- 이미 쓰고 있는 API 키와 재시도 로직(GeminiClient.postWithRetry)을 그대로 재사용할 수 있다.
-- 기울임·흐림·저해상도·JPEG 열화를 겹친 이미지에서도 상호·날짜·총액·품목을 정확히 뽑는 것을
-- 사전 검증했다.

-- ① 제공자에 GEMINI 추가. GOOGLE_VISION 은 남겨둔다 —
--    나중에 다시 쓸 수도 있고, 지우면 과거 행의 값이 깨진다.
ALTER TABLE receipt_ocr_jobs
  MODIFY COLUMN provider ENUM('GOOGLE_VISION','GEMINI') NOT NULL DEFAULT 'GEMINI';

-- ② 영수증 이미지를 보관하지 않으므로 경로를 NULL 허용으로 바꾼다.
--    가계부에 필요한 건 추출된 데이터이고, 영수증에는 카드번호 뒷자리 같은 정보가 남아 있어
--    보관하지 않는 편이 낫다. 추출 원문은 extracted_text 에 남으므로 재확인은 가능하다.
ALTER TABLE receipt_ocr_jobs
  MODIFY COLUMN receipt_path VARCHAR(255) NULL;
