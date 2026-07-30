package com.leonid.giwaapi.wallet;

import com.leonid.giwaapi.auth.AuthService;
import com.leonid.giwaapi.auth.SignupRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class WalletServiceIntegrationTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private WalletService walletService;

    @Test
    void mapsMetaMaskAddressToSignedInUsersCompany() {
        String email = "wallet-test@example.com";
        authService.signup(new SignupRequest(email, "password123", "Wallet Tester", "Wallet Test Company", "1234567890"));

        WalletResponse connected = walletService.connect(email,
                new WalletConnectRequest("0x1234567890123456789012345678901234567890", 1337L));

        assertThat(connected.walletAddress()).isEqualTo("0x1234567890123456789012345678901234567890");
        assertThat(walletService.me(email).walletAddress()).isEqualTo(connected.walletAddress());
    }
}
