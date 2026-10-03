package com.rental.common.feign;

import feign.Request;
import feign.RetryableException;
import feign.Retryer;

/**
 * Chi retry phuong thuc GET, toi da 1 lan (docs muc 5.2: khong tu retry
 * POST/PUT/DELETE; GET retry toi da 1 lan). Moi request Feign clone mot ban sao
 * rieng (theo contract cua {@link Retryer}).
 */
public class GetOnlyRetryer implements Retryer {

    /** So lan retry toi da (khong tinh lan goi dau). */
    public static final int MAX_RETRIES = 1;

    private static final long BACKOFF_MS = 100;

    private int count;

    @Override
    public void continueOrPropagate(RetryableException e) {
        if (e.request() != null && e.request().httpMethod() == Request.HttpMethod.GET && count < MAX_RETRIES) {
            count++;
            try {
                Thread.sleep(BACKOFF_MS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw e;
            }
            return;
        }
        throw e;
    }

    @Override
    public Retryer clone() {
        return new GetOnlyRetryer();
    }
}
