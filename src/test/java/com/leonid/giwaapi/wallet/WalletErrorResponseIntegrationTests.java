package com.leonid.giwaapi.wallet;

import com.leonid.giwaapi.auth.AuthResponse;
import com.leonid.giwaapi.auth.AuthService;
import com.leonid.giwaapi.auth.SignupRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WalletErrorResponseIntegrationTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void returnsConflictCodeWhenWalletBelongsToAnotherCompany() throws Exception {
        String walletAddress = "0x4444444444444444444444444444444444444444";
        String ownerEmail = "wallet-owner@example.com";
        String otherEmail = "wallet-other@example.com";
        authService.signup(new SignupRequest(
                ownerEmail, "password123", "Wallet Owner", "Wallet Owner Company", "4444444444"
        ));
        walletService.connect(ownerEmail, new WalletConnectRequest(walletAddress, 1337L));
        AuthResponse otherAuth = authService.signup(new SignupRequest(
                otherEmail, "password123", "Other User", "Other Company", "5555555555"
        ));

        mockMvc.perform(post("/wallet/connect")
                        .header("Authorization", "Bearer " + otherAuth.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WalletConnectRequest(walletAddress, 1337L))))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("WALLET_ALREADY_MAPPED"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/wallet/connect"));
    }

    @Test
    void returnsUnauthorizedJsonInsteadOfForbiddenForMissingToken() throws Exception {
        mockMvc.perform(get("/wallet/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }
}
