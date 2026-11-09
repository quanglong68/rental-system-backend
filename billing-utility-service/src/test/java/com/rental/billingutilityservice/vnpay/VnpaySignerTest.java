package com.rental.billingutilityservice.vnpay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VnpaySignerTest {

    private VnpaySigner signer;
    private static final String SECRET = "Q78M2SN95OZRMDVJPWIX5FEL37TXG9UL";

    @BeforeEach
    void setUp() {
        signer = new VnpaySigner();
    }

    @Test
    void signAndVerifyConsistent() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", "HNJAWPZ8");
        params.put("vnp_Amount", "10000000");
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", "INV12_1731170000");

        String hash = signer.sign(params, SECRET);
        assertNotNull(hash);
        assertFalse(hash.isBlank());

        params.put("vnp_SecureHash", hash);
        assertTrue(signer.verify(params, SECRET));
    }

    @Test
    void verifyRejectsTamperedParam() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "10000000");
        params.put("vnp_TxnRef", "INV12_1731170000");

        String hash = signer.sign(params, SECRET);
        params.put("vnp_SecureHash", hash);

        // Tamper amount
        params.put("vnp_Amount", "50000000");
        assertFalse(signer.verify(params, SECRET));
    }

    @Test
    void verifyRejectsMissingOrEmptyHash() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "10000000");
        assertFalse(signer.verify(params, SECRET));

        params.put("vnp_SecureHash", "");
        assertFalse(signer.verify(params, SECRET));
    }

    @Test
    void nonVnpayParamsDoNotAffectSignature() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "10000000");
        params.put("vnp_TxnRef", "INV12_1731170000");

        String hashBefore = signer.sign(params, SECRET);

        // Add non-vnp param
        params.put("otherParam", "shouldBeIgnored");
        String hashAfter = signer.sign(params, SECRET);

        assertEquals(hashBefore, hashAfter);
    }
}
