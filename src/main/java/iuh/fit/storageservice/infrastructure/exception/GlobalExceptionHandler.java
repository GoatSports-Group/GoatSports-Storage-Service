package iuh.fit.storageservice.infrastructure.exception;

import iuh.fit.storageservice.application.dto.response.base.BaseResponse;
import iuh.fit.storageservice.application.exception.ForbiddenException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {
    @ExceptionHandler(Exception.class)
    ResponseEntity<BaseResponse<Object>> handleAllExceptions(Exception e){
        BaseResponse<Object> response = new BaseResponse<>();
        response.setStatusCode(HttpStatus.BAD_REQUEST.value());
        response.setMessage(e.getMessage());
        response.setError("Có lỗi xảy ra");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<BaseResponse<Object>> handleForbidden(ForbiddenException e) {
        BaseResponse<Object> response = new BaseResponse<>();
        response.setStatusCode(HttpStatus.FORBIDDEN.value());
        response.setMessage(e.getMessage());
        response.setError("Forbidden");

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(S3Exception.class)
    public ResponseEntity<BaseResponse<Void>> handleS3Exception(S3Exception e) {
        BaseResponse<Void> response = new BaseResponse<>();
        response.setStatusCode(e.statusCode());
        response.setMessage("Lỗi thao tác lưu trữ Cloud: " + e.awsErrorDetails().errorMessage());
        response.setError("Có lỗi xảy ra");

        return ResponseEntity.status(e.statusCode()).body(response);
    }
}
