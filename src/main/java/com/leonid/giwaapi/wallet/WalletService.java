package com.leonid.giwaapi.wallet;

import com.leonid.giwaapi.auth.User;
import com.leonid.giwaapi.auth.UserMapper;
import com.leonid.giwaapi.common.error.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Objects;

@Service
public class WalletService {

    private final UserMapper userMapper;
    private final WalletMapper walletMapper;

    public WalletService(UserMapper userMapper, WalletMapper walletMapper) {
        this.userMapper = userMapper;
        this.walletMapper = walletMapper;
    }

    @Transactional
    public WalletResponse connect(String email, WalletConnectRequest request) {
        User user = findUser(email);
        String walletAddress = request.walletAddress().toLowerCase(Locale.ROOT);
        Wallet addressOwner = walletMapper.findByWalletAddress(walletAddress).orElse(null);
        if (addressOwner != null && !addressOwner.companyId().equals(user.companyId())) {
            throw walletAlreadyMapped();
        }

        Wallet currentWallet = walletMapper.findByCompanyId(user.companyId()).orElse(null);
        try {
            if (currentWallet == null) {
                walletMapper.insert(new Wallet(null, user.companyId(), walletAddress, request.chainId()));
            } else if (!currentWallet.walletAddress().equals(walletAddress)
                    || !Objects.equals(currentWallet.chainId(), request.chainId())) {
                walletMapper.update(new Wallet(
                        currentWallet.companyWalletId(),
                        user.companyId(),
                        walletAddress,
                        request.chainId()
                ));
            }
        } catch (DataIntegrityViolationException exception) {
            throw walletAlreadyMapped();
        }
        return new WalletResponse(walletAddress);
    }

    public WalletResponse me(String email) {
        User user = findUser(email);
        return walletMapper.findByCompanyId(user.companyId())
                .map(WalletResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No wallet is connected"));
    }

    private User findUser(String email) {
        return userMapper.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private ApiException walletAlreadyMapped() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "WALLET_ALREADY_MAPPED",
                "이미 다른 회사에 등록된 MetaMask 지갑입니다. 다른 MetaMask 계정을 선택해 주세요."
        );
    }
}
