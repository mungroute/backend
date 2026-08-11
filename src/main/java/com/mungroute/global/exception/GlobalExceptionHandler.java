package com.mungroute.global.exception;

import com.mungroute.global.response.FieldErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 모든 REST API 오류를 RFC Problem Details 기반 형식으로 변환한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 서비스에서 명시적으로 발생시킨 도메인 업무 예외를 처리한다.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request
    ) {
        return createResponse(
                exception.getErrorCode(),
                exception.getMessage(),
                request,
                List.of()
        );
    }

    /**
     * @Valid 요청 본문의 필드 검증 실패를 필드별 오류 목록으로 변환한다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<FieldErrorResponse> errors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new FieldErrorResponse(
                        error.getField(),
                        error.getDefaultMessage() == null
                                ? "올바르지 않은 값입니다."
                                : error.getDefaultMessage()
                ))
                .toList();

        return createResponse(
                GlobalErrorCode.VALIDATION_ERROR,
                GlobalErrorCode.VALIDATION_ERROR.getMessage(),
                request,
                errors
        );
    }

    /**
     * 폼 바인딩 검증 실패를 요청 본문 검증과 같은 형식으로 처리한다.
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ProblemDetail> handleBindException(
            BindException exception,
            HttpServletRequest request
    ) {
        List<FieldErrorResponse> errors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new FieldErrorResponse(
                        error.getField(),
                        error.getDefaultMessage() == null
                                ? "올바르지 않은 값입니다."
                                : error.getDefaultMessage()
                ))
                .toList();

        return createResponse(
                GlobalErrorCode.VALIDATION_ERROR,
                GlobalErrorCode.VALIDATION_ERROR.getMessage(),
                request,
                errors
        );
    }

    /**
     * PathVariable 및 RequestParam 제약조건 위반을 처리한다.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        List<FieldErrorResponse> errors = exception.getConstraintViolations()
                .stream()
                .map(violation -> new FieldErrorResponse(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()
                ))
                .toList();

        return createResponse(
                GlobalErrorCode.VALIDATION_ERROR,
                GlobalErrorCode.VALIDATION_ERROR.getMessage(),
                request,
                errors
        );
    }

    /**
     * Spring MVC 메서드 파라미터 검증 실패를 공통 검증 오류로 처리한다.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleHandlerMethodValidation(
            HandlerMethodValidationException exception,
            HttpServletRequest request
    ) {
        return createResponse(
                GlobalErrorCode.VALIDATION_ERROR,
                GlobalErrorCode.VALIDATION_ERROR.getMessage(),
                request,
                List.of()
        );
    }

    /**
     * JSON 문법, enum, 날짜 형식 및 파라미터 타입 변환 오류를 처리한다.
     */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ProblemDetail> handleInvalidInput(
            Exception exception,
            HttpServletRequest request
    ) {
        return createResponse(
                GlobalErrorCode.VALIDATION_ERROR,
                GlobalErrorCode.VALIDATION_ERROR.getMessage(),
                request,
                List.of()
        );
    }

    /**
     * 존재하지 않는 API 경로를 404 Problem Detail로 변환한다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResourceFound(
            NoResourceFoundException exception,
            HttpServletRequest request
    ) {
        return createResponse(
                GlobalErrorCode.RESOURCE_NOT_FOUND,
                GlobalErrorCode.RESOURCE_NOT_FOUND.getMessage(),
                request,
                List.of()
        );
    }

    /**
     * 지원하지 않는 HTTP 메서드 요청을 405 Problem Detail로 변환한다.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        return createResponse(
                GlobalErrorCode.METHOD_NOT_ALLOWED,
                GlobalErrorCode.METHOD_NOT_ALLOWED.getMessage(),
                request,
                List.of()
        );
    }

    /**
     * 분류되지 않은 예외는 내부 내용을 노출하지 않고 서버 로그에만 기록한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error("Unhandled exception while processing {}", request.getRequestURI(), exception);

        return createResponse(
                GlobalErrorCode.INTERNAL_SERVER_ERROR,
                GlobalErrorCode.INTERNAL_SERVER_ERROR.getMessage(),
                request,
                List.of()
        );
    }

    /**
     * 멍루트 API 계약의 application/problem+json 응답을 생성한다.
     */
    private ResponseEntity<ProblemDetail> createResponse(
            ErrorCode errorCode,
            String detail,
            HttpServletRequest request,
            List<FieldErrorResponse> errors
    ) {
        HttpStatus status = errorCode.getStatus();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);

        problem.setType(URI.create("about:blank"));
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", errorCode.name());
        problem.setProperty("timestamp", OffsetDateTime.now());
        problem.setProperty("errors", errors);

        return ResponseEntity
                .status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
