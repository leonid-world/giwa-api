package com.leonid.giwaapi.wallet;

import com.leonid.giwaapi.auth.User;
import com.leonid.giwaapi.auth.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

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
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Wallet is already mapped to another company");
        }

        Wallet currentWallet = walletMapper.findByCompanyId(user.companyId()).orElse(null);
        if (currentWallet == null) {
            walletMapper.insert(new Wallet(null, user.companyId(), walletAddress, request.chainId()));
        } else if (!currentWallet.walletAddress().equals(walletAddress)) {
            walletMapper.update(new Wallet(currentWallet.companyWalletId(), user.companyId(), walletAddress, request.chainId()));
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
}
