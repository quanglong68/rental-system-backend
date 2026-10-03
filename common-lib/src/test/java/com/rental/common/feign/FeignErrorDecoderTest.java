package com.rental.common.feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rental.common.error.BusinessException;
import com.rental.common.error.DependencyUnavailableException;
import com.rental.common.error.NotFoundException;
import feign.Response;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class FeignErrorDecoderTest {

    private final FeignErrorDecoder decoder = new FeignErrorDecoder();

    @Test
    void status404_thanhNotFound() {
        Exception ex = decoder.decode("Client#get", response(404, "Not Found", null));

        assertThat(ex).isInstanceOf(NotFoundException.class);
        assertThat(((NotFoundException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void status422_giuNguyenCodeBenKia() {
        String body = "{\"code\":\"ROOM_CAPACITY_EXCEEDED\",\"message\":\"Phong da du\",\"status\":422}";

        Exception ex = decoder.decode("Client#add", response(422, "Unprocessable Entity", body));

        assertThat(ex).isInstanceOf(BusinessException.class);
        BusinessException business = (BusinessException) ex;
        assertThat(business.getCode()).isEqualTo("ROOM_CAPACITY_EXCEEDED");
        assertThat(business.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void status4xxBodyHong_thanhUpstreamError() {
        Exception ex = decoder.decode("Client#add", response(422, "Unprocessable Entity", "not-json"));

        assertThat(ex).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) ex).getCode()).isEqualTo("UPSTREAM_ERROR");
        assertThat(((BusinessException) ex).getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void status500_thanhDependencyUnavailable() {
        Exception ex = decoder.decode("Client#get", response(500, "Internal Server Error", null));

        assertThat(ex).isInstanceOf(DependencyUnavailableException.class);
        assertThat(((DependencyUnavailableException) ex).getStatusValue()).isEqualTo(503);
    }

    private static Response response(int status, String reason, String body) {
        Response response = mock(Response.class);
        when(response.status()).thenReturn(status);
        when(response.reason()).thenReturn(reason);
        if (body == null) {
            when(response.body()).thenReturn(null);
        } else {
            Response.Body responseBody = mock(Response.Body.class);
            try {
                when(responseBody.asInputStream())
                        .thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            when(response.body()).thenReturn(responseBody);
        }
        return response;
    }
}
