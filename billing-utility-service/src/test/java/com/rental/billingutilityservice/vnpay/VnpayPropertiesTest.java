package com.rental.billingutilityservice.vnpay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VnpayPropertiesTest {

    private VnpayProperties properties;

    @BeforeEach
    void setUp() {
        properties = new VnpayProperties();
        properties.setAllowedReturnUrls(List.of(
                "http://localhost:3000/payment/vnpay-return",
                "https://rental-system-frontend-ten.vercel.app/payment/vnpay-return"
        ));
    }

    @Test
    void allowsExactWhitelistedUrls() {
        assertTrue(properties.isReturnUrlAllowed("http://localhost:3000/payment/vnpay-return"));
        assertTrue(properties.isReturnUrlAllowed("https://rental-system-frontend-ten.vercel.app/payment/vnpay-return"));
        assertTrue(properties.isReturnUrlAllowed("http://localhost:8443/payment/vnpay-return"));
    }

    @Test
    void allowsSameOriginDifferentPath() {
        assertTrue(properties.isReturnUrlAllowed("http://localhost:3000/my-room"));
        assertTrue(properties.isReturnUrlAllowed("https://rental-system-frontend-ten.vercel.app/dashboard"));
    }

    @Test
    void allowsUrlsWithWhitespaceInConfig() {
        properties.setAllowedReturnUrls(List.of(
                " http://localhost:3000/payment/vnpay-return ",
                "  https://rental-system-frontend-ten.vercel.app/payment/vnpay-return  "
        ));
        assertTrue(properties.isReturnUrlAllowed("http://localhost:3000/payment/vnpay-return"));
        assertTrue(properties.isReturnUrlAllowed("https://rental-system-frontend-ten.vercel.app/payment/vnpay-return"));
    }

    @Test
    void rejectsExternalOrMaliciousUrls() {
        assertFalse(properties.isReturnUrlAllowed("https://evil.com/payment/vnpay-return"));
        assertFalse(properties.isReturnUrlAllowed("http://localhost:8080/hack"));
        assertFalse(properties.isReturnUrlAllowed(null));
        assertFalse(properties.isReturnUrlAllowed(""));
        assertFalse(properties.isReturnUrlAllowed("not-a-valid-url"));
    }
}
