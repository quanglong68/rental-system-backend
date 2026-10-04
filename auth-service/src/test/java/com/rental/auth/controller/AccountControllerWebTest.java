package com.rental.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rental.auth.config.SecurityConfig;
import com.rental.auth.dto.AccountResponse;
import com.rental.auth.dto.PageResponse;
import com.rental.auth.dto.RegisterResponse;
import com.rental.auth.entity.AccountType;
import com.rental.auth.service.AccountService;
import com.rental.common.constant.Headers;
import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import com.rental.common.error.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Test web slice voi SecurityConfig THAT (filter + permitAll + @PreAuthorize),
 * AccountService mock, khong can DB/Eureka (Task C4).
 */
@WebMvcTest({AuthController.class, AdminAccountController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AccountControllerWebTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AccountService accountService;

    @Test
    void register_khongCanAuth_201() throws Exception {
        when(accountService.register(any()))
                .thenReturn(new RegisterResponse(7L, "a@b.com", AccountType.CUSTOMER));

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"a@b.com\",\"password\":\"matkhau123\",\"fullName\":\"A B\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.username").value("a@b.com"))
                .andExpect(jsonPath("$.accountType").value("CUSTOMER"));
    }

    @Test
    void register_matKhauNgan_400() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"a@b.com\",\"password\":\"123\",\"fullName\":\"A B\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void register_trungUsername_409() throws Exception {
        when(accountService.register(any()))
                .thenThrow(new BusinessException(ErrorCode.USERNAME_EXISTS, "Username da ton tai"));

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"a@b.com\",\"password\":\"matkhau123\",\"fullName\":\"A B\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USERNAME_EXISTS"));
    }

    @Test
    void adminPost_customerBi403() throws Exception {
        mvc.perform(post("/api/v1/auth/accounts").contentType(MediaType.APPLICATION_JSON)
                .header(Headers.X_USER_ID, "9").header(Headers.X_USER_TYPE, "STAFF")
                .header(Headers.X_USER_ROLES, "QUAN_LY")
                .content("{\"username\":\"s@mail.com\",\"password\":\"matkhau123\","
                        + "\"fullName\":\"S\",\"accountType\":\"STAFF\",\"roles\":[\"SALE\"]}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminPost_adminDuoc201() throws Exception {
        when(accountService.createAccount(any())).thenReturn(
                new AccountResponse(8L, "s@mail.com", AccountType.STAFF, true, List.of("SALE")));

        mvc.perform(post("/api/v1/auth/accounts").contentType(MediaType.APPLICATION_JSON)
                .header(Headers.X_USER_ID, "1").header(Headers.X_USER_TYPE, "ADMIN")
                .header(Headers.X_USER_ROLES, "ADMIN")
                .content("{\"username\":\"s@mail.com\",\"password\":\"matkhau123\","
                        + "\"fullName\":\"S\",\"accountType\":\"STAFF\",\"roles\":[\"SALE\"]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.roles[0]").value("SALE"));
    }

    @Test
    void getAccounts_thieuHeader_401() throws Exception {
        mvc.perform(get("/api/v1/auth/accounts")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void getAccounts_admin_200phanTrang() throws Exception {
        AccountResponse item = new AccountResponse(8L, "s@mail.com", AccountType.STAFF, true, List.of("SALE"));
        when(accountService.listAccounts(anyString(), any(), anyInt(), anyInt())).thenReturn(PageResponse
                .of(new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1)));

        mvc.perform(get("/api/v1/auth/accounts").param("accountType", "STAFF").param("active", "true")
                .header(Headers.X_USER_ID, "1").header(Headers.X_USER_TYPE, "ADMIN")
                .header(Headers.X_USER_ROLES, "ADMIN")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value("s@mail.com"));
    }
}
