package com.rental.billingutilityservice.vnpay;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Component
public class VnpaySigner {

    private static final String HMAC_SHA512 = "HmacSHA512";

    /** Dựng chuỗi hash từ params đã sort key (loại trừ chữ ký), theo chuẩn VNPay. */
    public String buildHashData(Map<String, String> params) {
        return new TreeMap<>(params)
                .entrySet().stream()
                .filter(e -> e.getKey() != null && e.getKey().startsWith("vnp_")
                        && !"vnp_SecureHash".equals(e.getKey())
                        && !"vnp_SecureHashType".equals(e.getKey())
                        && e.getValue() != null && !e.getValue().isEmpty())
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    public String sign(Map<String, String> params, String secret) {
        return hmacSha512(secret, buildHashData(params));
    }

    /** Verify checksum IPN/Return: loại bỏ vnp_SecureHash ra rồi tính lại trên phần còn lại. */
    public boolean verify(Map<String, String> params, String secret) {
        String receivedHash = params.get("vnp_SecureHash");
        if (receivedHash == null || receivedHash.isEmpty()) {
            return false;
        }
        String computed = sign(params, secret);
        return computed.equalsIgnoreCase(receivedHash);
    }

    public String hmacSha512(String secret, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA512);
            SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA512);
            mac.init(key);
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign VNPay data", e);
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
