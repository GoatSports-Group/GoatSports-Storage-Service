package iuh.fit.storageservice.application.dto.response.base;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BaseResponse<T> {
    private Integer statusCode;
    private T data;
    private Object message;
    private String error;
}
