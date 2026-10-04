package com.rental.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rental.auth.config.SecurityConfig;
import com.rental.auth.dto.ChangePasswordRequest;
import com.rental.auth.dto.LoginRequest;
import com.rental.auth.dto.LoginResponse;
import com.rental.auth.dto.MeResponse;
import com.rental.auth.dto.RefreshRequest;
import com.rental.auth.entity.AccountType;
import com.rental.auth.service.AccountService;
import com.rental.auth.service.AuthSessionService;
import com.rental.common.constant.Headers;
import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import com.rental.common.error.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Test web slice 5 endpoint phien (Task C5) voi SecurityConfig THAT:
 * login/refresh public, logout/me/password can header, principal la HeaderUser.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthSessionWebTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AccountService accountService;

    @MockBean
    private AuthSessionService sessionService;

    @Test
    void login_khongCanHeader_200() throws Exception {
        when(sessionService.login(any())).thenReturn(new LoginResponse("access.jwt", "refresh-raw", "Bearer", 900,
                new LoginResponse.AccountInfo(7L, "khach@mail.com", AccountType.CUSTOMER, List.of("CUSTOMER"))));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"khach@mail.com\",\"password\":\"matkhau123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.account.roles[0]").value("CUSTOMER"));
    }

    @Test
    void login_thieuField_400() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void login_saiMatKhauLan5_423() throws Exception {
        when(sessionService.login(any()))
                .thenThrow(new BusinessException(ErrorCode.ACCOUNT_LOCKED, "Tai khoan bi khoa 15 phut"));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"khach@mail.com\",\"password\":\"sai\"}"))
                .andExpect(status().isLocked()).andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void refresh_tokenDaThuHoi_401() throws Exception {
        when(sessionService.refresh(any()))
                .thenThrow(new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID, "Refresh token da bi thu hoi"));

        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"raw-old\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    void logout_thieuHeader_401() throws Exception {
        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"raw\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void logout_coHeader_204() throws Exception {
        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .header(Headers.X_USER_ID, "7").header(Headers.X_USER_TYPE, "CUSTOMER")
                .header(Headers.X_USER_ROLES, "CUSTOMER").content("{\"refreshToken\":\"raw\"}"))
                .andExpect(status().isNoContent());
        verify(sessionService).logout(any());
    }

    @Test
    void me_thieuHeader_401() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void me_coHeader_200() throws Exception {
        when(sessionService.me(any())).thenReturn(new MeResponse(7L, "khach@mail.com", AccountType.CUSTOMER,
                List.of("CUSTOMER")));

        mvc.perform(get("/api/v1/auth/me").header(Headers.X_USER_ID, "7")
                .header(Headers.X_USER_TYPE, "CUSTOMER").header(Headers.X_USER_ROLES, "CUSTOMER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("khach@mail.com"));
    }

    @Test
    void doiMatKhau_saiMatKhauCu_400() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.WRONG_OLD_PASSWORD, "Mat khau cu khong dung"))
                .when(sessionService).changePassword(eq(7L), any(ChangePasswordRequest.class));

        mvc.perform(put("/api/v1/auth/password").contentType(MediaType.APPLICATION_JSON)
                .header(Headers.X_USER_ID, "7").header(Headers.X_USER_TYPE, "CUSTOMER")
                .header(Headers.X_USER_ROLES, "CUSTOMER")
                .content("{\"oldPassword\":\"sai\",\"newPassword\":\"matkhaunew\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WRONG_OLD_PASSWORD"));
    }

    @Test
    void doiMatKhau_thanhCong_204() throws Exception {
        mvc.perform(put("/api/v1/auth/password").contentType(MediaType.APPLICATION_JSON)
                .header(Headers.X_USER_ID, "7").header(Headers.X_USER_TYPE, "CUSTOMER")
                .header(Headers.X_USER_ROLES, "CUSTOMER")
                .content("{\"oldPassword\":\"matkhau123\",\"newPassword\":\"matkhaunew\"}"))
                .andExpect(status().isNoContent());
    }
}