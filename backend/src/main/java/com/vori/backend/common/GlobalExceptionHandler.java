package com.vori.backend.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.time.format.DateTimeParseException;
import java.util.NoSuchElementException;

/**
 * 예외 → 사용자에게 보여줄 수 있는 에러 응답.
 *
 * 이게 없으면 서비스에 써 둔 문구("코인이 부족합니다")가 응답에 실리지 않는다.
 * Spring Boot 기본값이 server.error.include-message=never 라 status 와 error 만 나가고,
 * 그러면 화면은 "400 이면 아마 코인 부족이겠지" 하고 추측해서 문구를 하드코딩해야 한다.
 * 400 하나가 코인 부족·월 형식 오류·금액 0·좌표 음수를 전부 뜻하므로 그 추측은 반드시 틀린다.
 *
 * include-message=always 설정 한 줄로도 message 를 실을 수 있지만 쓰지 않았다.
 * 그 방식은 처리하지 못한 500 의 내부 예외 메시지까지 밖으로 내보낸다.
 *
 * **catch-all(Exception.class) 핸들러는 일부러 두지 않았다.** 그걸 두면 존재하지 않는
 * 경로에 대한 NoResourceFoundException 까지 붙잡아 404 를 500 으로 바꿔버린다.
 * 여기서 잡지 않은 예외는 지금까지처럼 Spring 기본 처리로 흘러가 message 없는 500 이 된다 —
 * 내부 사정이 새지 않는 쪽이 안전하다.
 *
 * Security 필터가 만드는 401·403(로그인 안 한 상태)은 DispatcherServlet 이전 단계라
 * 여기까지 오지 않는다. 그 응답에는 message 가 없다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 서비스가 의도적으로 던진 예외. 상태와 문구를 그대로 전달한다. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(
            ResponseStatusException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
        String message = e.getReason() == null ? status.getReasonPhrase() : e.getReason();

        // 5xx 는 서버 잘못이므로 흔적을 남긴다. 4xx 는 정상적인 거절이라 로그를 남기지 않는다.
        if (status.is5xxServerError()) {
            log.error("서버 오류 — {} {}", request.getMethod(), request.getRequestURI(), e);
        }
        return ResponseEntity.status(status).body(
                ErrorResponse.of(status, message, request.getRequestURI()));
    }

    /**
     * @Valid 실패. DTO 에 이미 한글 문구가 붙어 있으므로 그대로 쓴다.
     * 여러 개가 걸려도 첫 번째만 보낸다 — 화면은 한 번에 한 문장만 띄운다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(m -> m != null && !m.isBlank())
                .findFirst()
                .orElse("입력값을 확인해주세요.");
        return ResponseEntity.badRequest().body(
                ErrorResponse.of(HttpStatus.BAD_REQUEST, message, request.getRequestURI()));
    }

    /**
     * 업로드 용량 초과. 이 예외는 컨트롤러에 닿기 전에 터지므로 ReceiptService 의
     * 10MB 검사와 문구를 여기서 맞춰준다.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadSize(
            MaxUploadSizeExceededException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
                ErrorResponse.of(HttpStatus.PAYLOAD_TOO_LARGE,
                        "이미지는 10MB 이하만 업로드할 수 있습니다", request.getRequestURI()));
    }

    /**
     * 본문을 읽지 못함 — 숫자 칸에 문자열, int 범위를 넘는 값, 깨진 JSON 등.
     * 지금까지는 message 없는 400 이었다. 파싱 예외 문구는 내부 사정이라 내보내지 않는다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(
            HttpMessageNotReadableException e, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(
                ErrorResponse.of(HttpStatus.BAD_REQUEST,
                        "입력 형식이 올바르지 않아요. 입력값을 확인해 주세요.",
                        request.getRequestURI()));
    }

    /** 잘못된 인자(존재하지 않는 카테고리 등). 지금까지는 message 없는 500 이었다. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException e, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(
                ErrorResponse.of(HttpStatus.BAD_REQUEST,
                        e.getMessage() == null ? "잘못된 요청입니다." : e.getMessage(),
                        request.getRequestURI()));
    }

    /** 월·날짜 파라미터 파싱 실패(YearMonth.parse, LocalDate.parse). 지금까지는 message 없는 500 이었다. */
    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<ErrorResponse> handleDateParse(
            DateTimeParseException e, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(
                ErrorResponse.of(HttpStatus.BAD_REQUEST, "날짜 형식이 올바르지 않아요.", request.getRequestURI()));
    }

    /**
     * 인자 없는 orElseThrow()·Optional.get() — 찾는 행이 없다("No value present"). 지금까지는 message 없는 500 이었다.
     * 그 밖의 NoSuchElementException(빈 iterator.next() 등)은 코드 버그라 500 으로 두고 로그를 남긴다.
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNoSuchElement(
            NoSuchElementException e, HttpServletRequest request) {
        if (!OPTIONAL_EMPTY.equals(e.getMessage())) {
            return serverError(request, e);
        }
        StackTraceElement at = e.getStackTrace().length > 1 ? e.getStackTrace()[1] : null;
        log.warn("찾는 행 없음 — {} {} at {}", request.getMethod(), request.getRequestURI(), at);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ErrorResponse.of(HttpStatus.NOT_FOUND, "요청한 정보를 찾을 수 없어요.", request.getRequestURI()));
    }

    /**
     * 유니크 제약 위반 — 같은 요청이 동시에 두 번 와서 한쪽이 먼저 행을 만든 경우다. 409 로 알린다.
     * 길이 초과·NOT NULL·FK 위반은 중복이 아니라 서버 쪽 문제라 500 으로 두고 로그를 남긴다.
     * 제약 이름·값이 담긴 DB 문구는 어느 쪽이든 내보내지 않는다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException e, HttpServletRequest request) {
        if (!isDuplicateKey(e)) {
            return serverError(request, e);
        }
        log.warn("중복 요청 — {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ErrorResponse.of(HttpStatus.CONFLICT, "이미 처리된 요청이에요. 새로고침 후 다시 확인해 주세요.",
                        request.getRequestURI()));
    }

    private static final String OPTIONAL_EMPTY = "No value present";
    /** MySQL ER_DUP_ENTRY. */
    private static final int MYSQL_DUPLICATE_ENTRY = 1062;

    private static boolean isDuplicateKey(DataIntegrityViolationException e) {
        if (e instanceof DuplicateKeyException) return true;
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getErrorCode() == MYSQL_DUPLICATE_ENTRY) return true;
            if (t.getCause() == t) break;
        }
        return false;
    }

    private ResponseEntity<ErrorResponse> serverError(HttpServletRequest request, Exception e) {
        log.error("서버 오류 — {} {}", request.getMethod(), request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.",
                        request.getRequestURI()));
    }
}
