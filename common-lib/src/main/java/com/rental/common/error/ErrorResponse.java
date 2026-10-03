package com.rental.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dinh dang loi chuan, moi service dung chung qua common-lib (docs muc 8).
 * Vi du: {"code":"ROOM_CAPACITY_EXCEEDED","message":"Phong da du so nguoi toi da",
 * "status":422,"traceId":"&lt;correlationId&gt;","details":[...]}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private String code;
    private String message;
    private int status;
    private String traceId;
    private List<FieldViolation> details;
}
