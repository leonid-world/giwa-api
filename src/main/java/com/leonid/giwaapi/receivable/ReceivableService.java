package com.leonid.giwaapi.receivable;

import com.leonid.giwaapi.auth.User;
import com.leonid.giwaapi.auth.UserMapper;
import com.leonid.giwaapi.company.Company;
import com.leonid.giwaapi.company.CompanyMapper;
import com.leonid.giwaapi.wallet.Wallet;
import com.leonid.giwaapi.wallet.WalletMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ReceivableService {

    private final UserMapper userMapper;
    private final CompanyMapper companyMapper;
    private final WalletMapper walletMapper;
    private final ReceivableMapper receivableMapper;

    public ReceivableService(
            UserMapper userMapper,
            CompanyMapper companyMapper,
            WalletMapper walletMapper,
            ReceivableMapper receivableMapper
    ) {
        this.userMapper = userMapper;
        this.companyMapper = companyMapper;
        this.walletMapper = walletMapper;
        this.receivableMapper = receivableMapper;
    }

    @Transactional
    public ReceivableResponse create(String email, ReceivableCreateRequest request) {
        User user = findUser(email);
        Company buyer = companyMapper.findByBusinessNumber(request.buyerBusinessNumber())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Buyer company was not found"));
        if (user.companyId().equals(buyer.companyId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seller and buyer must be different companies");
        }
        if (!request.maturityDate().isAfter(request.issueDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maturity date must be after issue date");
        }
        if (request.fundingAmount().compareTo(request.faceValue()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Funding amount cannot exceed face value");
        }

        Wallet sellerWallet = findPrimaryWallet(user.companyId(), "Connect the seller wallet first");
        Wallet buyerWallet = findPrimaryWallet(buyer.companyId(), "Buyer company has no connected wallet");

        Receivable receivable = new Receivable();
        receivable.setSellerCompanyId(user.companyId());
        receivable.setBuyerCompanyId(buyer.companyId());
        receivable.setSellerWalletAddress(sellerWallet.walletAddress());
        receivable.setBuyerWalletAddress(buyerWallet.walletAddress());
        receivable.setCurrencyCode("KRW");
        receivable.setFaceValue(request.faceValue());
        receivable.setFundingAmount(request.fundingAmount());
        receivable.setIssueDate(request.issueDate());
        receivable.setMaturityDate(request.maturityDate());
        receivable.setStatus("CREATED");
        receivable.setDocumentHash(normalizeDocumentHash(request.documentHash()));
        receivable.setDescription(request.description());
        receivable.setCreatedBy(user.userId());

        receivableMapper.insert(receivable);
        receivableMapper.insertCreatedHistory(
                receivable.getReceivableId(),
                user.companyId(),
                sellerWallet.walletAddress()
        );
        return getById(email, receivable.getReceivableId());
    }

    public List<ReceivableResponse> getAll(String email) {
        User user = findUser(email);
        return receivableMapper.findAllVisibleToCompany(user.companyId());
    }

    public ReceivableResponse getById(String email, Long receivableId) {
        User user = findUser(email);
        return receivableMapper.findVisibleById(receivableId, user.companyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Receivable was not found"));
    }

    private User findUser(String email) {
        return userMapper.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private Wallet findPrimaryWallet(Long companyId, String message) {
        return walletMapper.findByCompanyId(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, message));
    }

    private String normalizeDocumentHash(String documentHash) {
        if (documentHash == null || documentHash.isBlank()) return null;
        return documentHash.startsWith("0x") ? documentHash : "0x" + documentHash;
    }
}
