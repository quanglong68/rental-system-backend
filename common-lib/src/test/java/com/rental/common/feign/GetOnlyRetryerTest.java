package com.rental.common.feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import feign.Request;
import feign.RetryableException;
import java.net.SocketTimeoutException;
import java.util.Date;
import org.junit.jupiter.api.Test;

class GetOnlyRetryerTest {

    @Test
    void get_duocRetry1Lan() {
        GetOnlyRetryer retryer = new GetOnlyRetryer();
        RetryableException timeout = timeout(Request.HttpMethod.GET);

        retryer.continueOrPropagate(timeout);

        assertThatThrownBy(() -> retryer.continueOrPropagate(timeout)).isSameAs(timeout);
    }

    @Test
    void post_khongRetry() {
        RetryableException timeout = timeout(Request.HttpMethod.POST);

        assertThatThrownBy(() -> new GetOnlyRetryer().continueOrPropagate(timeout)).isSameAs(timeout);
    }

    @Test
    void clone_taoBoDemMoi() {
        GetOnlyRetryer retryer = new GetOnlyRetryer();
        RetryableException timeout = timeout(Request.HttpMethod.GET);
        retryer.continueOrPropagate(timeout);

        GetOnlyRetryer fresh = (GetOnlyRetryer) retryer.clone();
        fresh.continueOrPropagate(timeout);

        assertThatThrownBy(() -> fresh.continueOrPropagate(timeout)).isSameAs(timeout);
    }

    private static RetryableException timeout(Request.HttpMethod method) {
        Request request = mock(Request.class);
        when(request.httpMethod()).thenReturn(method);
        return new RetryableException(-1, "read timed out", method, new SocketTimeoutException(), (Date) null,
                request);
    }
}
