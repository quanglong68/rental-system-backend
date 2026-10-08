package com.rental.user.service;

import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import com.rental.common.security.HeaderUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {
    private CurrentUser() { }
    public static HeaderUser require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof HeaderUser user)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "Chua dang nhap");
        }
        return user;
    }
    public static Long id() {
        try { return Long.valueOf(require().getUserId()); }
        catch (NumberFormatException ex) { throw new BusinessException(ErrorCode.UNAUTHENTICATED, "User ID khong hop le"); }
    }
}
