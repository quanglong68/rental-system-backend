package com.rental.billingutilityservice.vnpay;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "vnpay")
@Getter
@Setter
public class VnpayProperties {

    private String tmnCode;
    private String hashSecret;
    private String apiUrl;
    private String apiQueryUrl;
    private List<String> allowedReturnUrls;
    private String ipnUrl;
    private String defaultClientIp = "127.0.0.1";

    public String getDefaultReturnUrl() {
        return (allowedReturnUrls != null && !allowedReturnUrls.isEmpty())
                ? allowedReturnUrls.get(0).trim()
                : null;
    }

    public boolean isReturnUrlAllowed(String url) {
        if (url == null || url.isBlank() || allowedReturnUrls == null) {
            return false;
        }
        String target = url.trim();
        boolean exact = allowedReturnUrls.stream()
                .filter(u -> u != null && !u.isBlank())
                .map(String::trim)
                .anyMatch(target::equalsIgnoreCase);
        if (exact) {
            return true;
        }
        String candidateOrigin = originOf(target);
        if (candidateOrigin == null) {
            return false;
        }
        if ((candidateOrigin.startsWith("http://localhost:") || candidateOrigin.startsWith("http://127.0.0.1:")
                || candidateOrigin.equals("http://localhost") || candidateOrigin.equals("http://127.0.0.1"))
                && (target.contains("/payment/vnpay-return") || target.contains("/vnpay-return"))) {
            return true;
        }
        return allowedReturnUrls.stream()
                .filter(u -> u != null && !u.isBlank())
                .map(String::trim)
                .map(this::originOf)
                .filter(o -> o != null)
                .anyMatch(candidateOrigin::equals);
    }

    private String originOf(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
            return uri.getScheme().toLowerCase() + "://" + uri.getHost().toLowerCase() + port;
        } catch (Exception e) {
            return null;
        }
    }
}
