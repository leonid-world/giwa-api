package com.leonid.giwaapi.receivable;

import com.leonid.giwaapi.auth.User;
import com.leonid.giwaapi.auth.UserMapper;
import com.leonid.giwaapi.common.error.ApiException;
import com.leonid.giwaapi.company.Company;
import com.leonid.giwaapi.company.CompanyMapper;
import com.leonid.giwaapi.transaction.BlockchainRpcProperties;
import com.leonid.giwaapi.transaction.BlockchainTransactionResponse;
import com.leonid.giwaapi.transaction.BlockchainTransactionService;
import com.leonid.giwaapi.wallet.Wallet;
import com.leonid.giwaapi.wallet.WalletMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class ReceivableService {

    private static final Pattern ADDRESS_PATTERN =
            Pattern.compile("^0x[a-fA-F0-9]{40}$");
    private static final String ZERO_ADDRESS =
            "0x0000000000000000000000000000000000000000";

    private final UserMapper userMapper;
    private final CompanyMapper companyMapper;
    private final WalletMapper walletMapper;
    private final ReceivableMapper receivableMapper;
    private final BlockchainTransactionService transactionService;
    private final BlockchainRpcProperties blockchainProperties;

    public ReceivableService(
            UserMapper userMapper,
            CompanyMapper companyMapper,
            WalletMapper walletMapper,
            ReceivableMapper receivableMapper,
            BlockchainTransactionService transactionService,
            BlockchainRpcProperties blockchainProperties
    ) {
        this.userMapper = userMapper;
        this.companyMapper = companyMapper;
        this.walletMapper = walletMapper;
        this.receivableMapper = receivableMapper;
        this.transactionService = transactionService;
        this.blockchainProperties = blockchainProperties;
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

    public List<ReceivableResponse> getFundingOpportunities(String email) {
        User user = findUser(email);
        return receivableMapper.findAllFundingOpportunities(user.companyId());
    }

    public ReceivableResponse getById(String email, Long receivableId) {
        User user = findUser(email);
        return receivableMapper.findVisibleById(receivableId, user.companyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Receivable was not found"));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReceivableResponse markChainCreated(
            String email,
            Long receivableId,
            ReceivableChainCreatedRequest request
    ) {
        User user = findUser(email);
        ReceivableResponse receivable = findReceivable(receivableId);
        requireCompany(
                user.companyId(),
                receivable.getSellerCompanyId(),
                "ONLY_SELLER",
                "Seller 회사만 온체인 채권 생성을 동기화할 수 있습니다."
        );

        Long onchainReceivableId = parseOnchainReceivableId(request.onchainReceivableId());
        if (hasChainMetadata(receivable)) {
            if (sameChainMetadata(receivable, onchainReceivableId, request)) {
                return receivable;
            }
            throw blockchainMetadataConflict();
        }
        if (!"CREATED".equals(receivable.getStatus())) {
            throw invalidStatus("CREATED", receivable.getStatus());
        }
        if (isZeroAddress(request.contractAddress())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_CONTRACT_ADDRESS",
                    "0 주소는 컨트랙트 주소로 사용할 수 없습니다."
            );
        }
        transactionService.requireConfirmed(
                receivableId,
                user.companyId(),
                "CREATE_RECEIVABLE",
                receivable.getSellerWalletAddress(),
                request.contractAddress(),
                request.txHash(),
                onchainReceivableId
        );
        if (receivableMapper.countChainIdentityUsedByOther(
                receivableId,
                onchainReceivableId,
                request.contractAddress()
        ) > 0 || receivableMapper.countTransactionHashUsage(request.txHash()) > 0) {
            throw blockchainMetadataConflict();
        }

        int updated;
        try {
            updated = receivableMapper.markChainCreated(
                    receivableId,
                    user.companyId(),
                    user.userId(),
                    onchainReceivableId,
                    request.contractAddress(),
                    request.txHash()
            );
        } catch (DataIntegrityViolationException exception) {
            throw blockchainMetadataConflict();
        }
        if (updated == 1) {
            return findReceivable(receivableId);
        }

        ReceivableResponse latest = findReceivable(receivableId);
        if (sameChainMetadata(latest, onchainReceivableId, request)) {
            return latest;
        }
        throw blockchainMetadataConflict();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReceivableResponse markVerified(
            String email,
            Long receivableId,
            ReceivableVerifiedRequest request
    ) {
        User user = findUser(email);
        ReceivableResponse receivable = findReceivable(receivableId);
        requireCompany(
                user.companyId(),
                receivable.getBuyerCompanyId(),
                "ONLY_BUYER",
                "Buyer 회사만 매출채권을 검증할 수 있습니다."
        );

        if (receivable.getVerifyTxHash() != null) {
            if (equalsHex(receivable.getVerifyTxHash(), request.txHash())) {
                return receivable;
            }
            throw blockchainMetadataConflict();
        }
        if (!"CREATED".equals(receivable.getStatus())) {
            throw invalidStatus("CREATED", receivable.getStatus());
        }
        if (!hasCompleteChainMetadata(receivable)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "RECEIVABLE_NOT_ONCHAIN",
                    "Seller의 GIWA 온체인 채권 생성이 먼저 완료되어야 합니다."
            );
        }
        transactionService.requireConfirmed(
                receivableId,
                user.companyId(),
                "VERIFY_RECEIVABLE",
                receivable.getBuyerWalletAddress(),
                receivable.getContractAddress(),
                request.txHash(),
                receivable.getOnchainReceivableId()
        );
        if (receivableMapper.countTransactionHashUsage(request.txHash()) > 0) {
            throw blockchainMetadataConflict();
        }

        int updated;
        try {
            updated = receivableMapper.markVerified(
                    receivableId,
                    user.companyId(),
                    user.userId(),
                    request.txHash()
            );
        } catch (DataIntegrityViolationException exception) {
            throw blockchainMetadataConflict();
        }
        if (updated == 1) {
            receivableMapper.insertVerifiedHistory(
                    receivableId,
                    user.companyId(),
                    receivable.getBuyerWalletAddress(),
                    request.txHash()
            );
            return findReceivable(receivableId);
        }

        ReceivableResponse latest = findReceivable(receivableId);
        if (equalsHex(latest.getVerifyTxHash(), request.txHash())) {
            return latest;
        }
        throw new ApiException(
                HttpStatus.CONFLICT,
                "RECEIVABLE_STATE_CONFLICT",
                "채권 상태가 다른 요청에 의해 변경되었습니다. 새로고침 후 다시 확인해 주세요."
        );
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReceivableResponse markTokenized(
            String email,
            Long receivableId,
            ReceivableTokenizedRequest request
    ) {
        User user = findUser(email);
        ReceivableResponse receivable = findReceivable(receivableId);
        requireCompany(
                user.companyId(),
                receivable.getSellerCompanyId(),
                "ONLY_SELLER",
                "Seller 회사만 매출채권을 토큰화할 수 있습니다."
        );

        if (hasTokenizationMetadata(receivable)) {
            if (receivable.getTokenId() != null
                    && equalsHex(receivable.getTokenizeTxHash(), request.txHash())) {
                return receivable;
            }
            throw blockchainMetadataConflict();
        }
        if (!"VERIFIED".equals(receivable.getStatus())) {
            throw invalidStatus("VERIFIED", receivable.getStatus());
        }
        if (!hasCompleteVerifiedChainMetadata(receivable)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "RECEIVABLE_NOT_VERIFIED_ONCHAIN",
                    "Buyer의 GIWA 온체인 검증이 먼저 완료되어야 합니다."
            );
        }

        BlockchainTransactionResponse confirmedTransaction =
                transactionService.requireConfirmed(
                        receivableId,
                        user.companyId(),
                        "TOKENIZE_RECEIVABLE",
                        receivable.getSellerWalletAddress(),
                        receivable.getContractAddress(),
                        request.txHash(),
                        receivable.getOnchainReceivableId()
                );
        Long tokenId = confirmedTransaction.getEventTokenId();
        if (tokenId == null || tokenId <= 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "BLOCKCHAIN_SYNCHRONIZATION_EVENT_MISMATCH",
                    "검증된 토큰화 이벤트의 토큰 ID를 확인할 수 없습니다."
            );
        }
        String txHash = confirmedTransaction.getTxHash();
        if (receivableMapper.countTokenIdentityUsedByOther(
                receivableId,
                receivable.getContractAddress(),
                tokenId
        ) > 0 || receivableMapper.countTransactionHashUsage(txHash) > 0) {
            throw blockchainMetadataConflict();
        }

        int updated;
        try {
            updated = receivableMapper.markTokenized(
                    receivableId,
                    user.companyId(),
                    user.userId(),
                    tokenId,
                    txHash
            );
        } catch (DataIntegrityViolationException exception) {
            throw blockchainMetadataConflict();
        }
        if (updated == 1) {
            receivableMapper.insertTokenizedHistory(
                    receivableId,
                    user.companyId(),
                    receivable.getSellerWalletAddress(),
                    txHash
            );
            return findReceivable(receivableId);
        }

        ReceivableResponse latest = findReceivable(receivableId);
        if (sameTokenizationMetadata(latest, tokenId, txHash)) {
            return latest;
        }
        if (hasTokenizationMetadata(latest)) {
            throw blockchainMetadataConflict();
        }
        throw new ApiException(
                HttpStatus.CONFLICT,
                "RECEIVABLE_STATE_CONFLICT",
                "채권 상태가 다른 요청에 의해 변경되었습니다. 새로고침 후 다시 확인해 주세요."
        );
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReceivableResponse markFunded(
            String email,
            Long receivableId,
            ReceivableFundedRequest request
    ) {
        User user = findUser(email);
        ReceivableResponse receivable = findReceivable(receivableId);
        requireThirdPartyFunder(user.companyId(), receivable);

        if (hasFundingMetadata(receivable)) {
            if (!Objects.equals(user.companyId(), receivable.getFunderCompanyId())) {
                throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "ONLY_FUNDER",
                        "채권을 공급한 Funder 회사만 펀딩 동기화를 재시도할 수 있습니다."
                );
            }
            if (hasCompleteFundingMetadata(receivable)
                    && equalsHex(receivable.getFundingTxHash(), request.txHash())) {
                return receivable;
            }
            throw blockchainMetadataConflict();
        }
        if (!"TOKENIZED".equals(receivable.getStatus())) {
            throw invalidStatus("TOKENIZED", receivable.getStatus());
        }
        if (!hasCompleteTokenizationMetadata(receivable)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "RECEIVABLE_NOT_TOKENIZED_ONCHAIN",
                    "Seller의 GIWA 채권 토큰화가 먼저 완료되어야 합니다."
            );
        }

        Wallet funderWallet = findPrimaryWallet(
                user.companyId(),
                "Connect the funder wallet first"
        );
        String mockTokenAddress = configuredMockTokenAddress();
        BlockchainTransactionResponse confirmedTransaction =
                transactionService.requireConfirmed(
                        receivableId,
                        user.companyId(),
                        "FUND_RECEIVABLE",
                        funderWallet.walletAddress(),
                        receivable.getContractAddress(),
                        request.txHash(),
                        receivable.getOnchainReceivableId()
                );
        if (!Objects.equals(
                receivable.getTokenId(),
                confirmedTransaction.getEventTokenId()
        )) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "BLOCKCHAIN_SYNCHRONIZATION_EVENT_MISMATCH",
                    "검증된 펀딩 이벤트의 토큰 ID가 DB 채권과 일치하지 않습니다."
            );
        }
        String txHash = confirmedTransaction.getTxHash();
        if (receivableMapper.countTransactionHashUsage(txHash) > 0) {
            throw blockchainMetadataConflict();
        }

        int updated;
        try {
            updated = receivableMapper.markFunded(
                    receivableId,
                    user.companyId(),
                    user.userId(),
                    funderWallet.walletAddress(),
                    mockTokenAddress,
                    txHash
            );
        } catch (DataIntegrityViolationException exception) {
            throw blockchainMetadataConflict();
        }
        if (updated == 1) {
            receivableMapper.insertFundedHistory(
                    receivableId,
                    user.companyId(),
                    funderWallet.walletAddress(),
                    txHash
            );
            return findReceivable(receivableId);
        }

        ReceivableResponse latest = findReceivable(receivableId);
        if (sameFundingMetadata(
                latest,
                user.companyId(),
                funderWallet.walletAddress(),
                mockTokenAddress,
                txHash
        )) {
            return latest;
        }
        if (hasFundingMetadata(latest)) {
            throw blockchainMetadataConflict();
        }
        throw new ApiException(
                HttpStatus.CONFLICT,
                "RECEIVABLE_STATE_CONFLICT",
                "채권 상태가 다른 요청에 의해 변경되었습니다. 새로고침 후 다시 확인해 주세요."
        );
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReceivableResponse markRepaid(
            String email,
            Long receivableId,
            ReceivableRepaidRequest request
    ) {
        User user = findUser(email);
        ReceivableResponse receivable = findReceivable(receivableId);
        requireCompany(
                user.companyId(),
                receivable.getBuyerCompanyId(),
                "ONLY_BUYER",
                "Buyer 회사만 매출채권 상환을 동기화할 수 있습니다."
        );

        if (receivable.getRepayTxHash() != null) {
            if (equalsHex(receivable.getRepayTxHash(), request.txHash())) {
                return receivable;
            }
            throw blockchainMetadataConflict();
        }
        if (!"FUNDED".equals(receivable.getStatus())) {
            throw invalidStatus("FUNDED", receivable.getStatus());
        }
        if (!hasCompleteFundedMetadata(receivable)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "RECEIVABLE_NOT_FUNDED_ONCHAIN",
                    "Funder의 GIWA 채권 펀딩이 먼저 완료되어야 합니다."
            );
        }

        BlockchainTransactionResponse confirmedTransaction =
                transactionService.requireConfirmed(
                        receivableId,
                        user.companyId(),
                        "REPAY_RECEIVABLE",
                        receivable.getBuyerWalletAddress(),
                        receivable.getContractAddress(),
                        request.txHash(),
                        receivable.getOnchainReceivableId()
                );
        if (!Objects.equals(
                receivable.getTokenId(),
                confirmedTransaction.getEventTokenId()
        )) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "BLOCKCHAIN_SYNCHRONIZATION_EVENT_MISMATCH",
                    "검증된 상환 이벤트의 토큰 ID가 DB 채권과 일치하지 않습니다."
            );
        }
        String txHash = confirmedTransaction.getTxHash();
        if (receivableMapper.countTransactionHashUsage(txHash) > 0) {
            throw blockchainMetadataConflict();
        }

        int updated;
        try {
            updated = receivableMapper.markRepaid(
                    receivableId,
                    user.companyId(),
                    user.userId(),
                    txHash
            );
        } catch (DataIntegrityViolationException exception) {
            throw blockchainMetadataConflict();
        }
        if (updated == 1) {
            receivableMapper.insertRepaidHistory(
                    receivableId,
                    user.companyId(),
                    receivable.getBuyerWalletAddress(),
                    txHash
            );
            return findReceivable(receivableId);
        }

        ReceivableResponse latest = findReceivable(receivableId);
        if (equalsHex(latest.getRepayTxHash(), txHash)) {
            return latest;
        }
        if (latest.getRepayTxHash() != null) {
            throw blockchainMetadataConflict();
        }
        throw new ApiException(
                HttpStatus.CONFLICT,
                "RECEIVABLE_STATE_CONFLICT",
                "채권 상태가 다른 요청에 의해 변경되었습니다. 새로고침 후 다시 확인해 주세요."
        );
    }

    private User findUser(String email) {
        return userMapper.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private Wallet findPrimaryWallet(Long companyId, String message) {
        return walletMapper.findByCompanyId(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, message));
    }

    private ReceivableResponse findReceivable(Long receivableId) {
        return receivableMapper.findById(receivableId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "RECEIVABLE_NOT_FOUND",
                        "매출채권을 찾을 수 없습니다."
                ));
    }

    private void requireCompany(Long actualCompanyId, Long expectedCompanyId, String code, String message) {
        if (!Objects.equals(actualCompanyId, expectedCompanyId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, code, message);
        }
    }

    private Long parseOnchainReceivableId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_ONCHAIN_RECEIVABLE_ID",
                    "온체인 채권 ID 범위를 확인해 주세요."
            );
        }
    }

    private boolean hasChainMetadata(ReceivableResponse receivable) {
        return receivable.getOnchainReceivableId() != null
                || receivable.getContractAddress() != null
                || receivable.getCreateTxHash() != null;
    }

    private boolean hasCompleteChainMetadata(ReceivableResponse receivable) {
        return receivable.getOnchainReceivableId() != null
                && receivable.getContractAddress() != null
                && receivable.getCreateTxHash() != null;
    }

    private boolean hasCompleteVerifiedChainMetadata(ReceivableResponse receivable) {
        return hasCompleteChainMetadata(receivable)
                && receivable.getVerifyTxHash() != null;
    }

    private boolean hasTokenizationMetadata(ReceivableResponse receivable) {
        return receivable.getTokenId() != null
                || receivable.getTokenizeTxHash() != null;
    }

    private boolean hasCompleteTokenizationMetadata(ReceivableResponse receivable) {
        return hasCompleteVerifiedChainMetadata(receivable)
                && receivable.getTokenId() != null
                && receivable.getTokenizeTxHash() != null;
    }

    private boolean hasFundingMetadata(ReceivableResponse receivable) {
        return receivable.getFunderCompanyId() != null
                || receivable.getFunderWalletAddress() != null
                || receivable.getMockTokenAddress() != null
                || receivable.getFundingTxHash() != null;
    }

    private boolean hasCompleteFundingMetadata(ReceivableResponse receivable) {
        return receivable.getFunderCompanyId() != null
                && receivable.getFunderWalletAddress() != null
                && receivable.getMockTokenAddress() != null
                && receivable.getFundingTxHash() != null;
    }

    private boolean hasCompleteFundedMetadata(ReceivableResponse receivable) {
        return hasCompleteTokenizationMetadata(receivable)
                && hasCompleteFundingMetadata(receivable);
    }

    private boolean sameChainMetadata(
            ReceivableResponse receivable,
            Long onchainReceivableId,
            ReceivableChainCreatedRequest request
    ) {
        return Objects.equals(receivable.getOnchainReceivableId(), onchainReceivableId)
                && equalsHex(receivable.getContractAddress(), request.contractAddress())
                && equalsHex(receivable.getCreateTxHash(), request.txHash());
    }

    private boolean equalsHex(String first, String second) {
        return first != null && second != null && first.equalsIgnoreCase(second);
    }

    private boolean sameTokenizationMetadata(
            ReceivableResponse receivable,
            Long tokenId,
            String txHash
    ) {
        return Objects.equals(receivable.getTokenId(), tokenId)
                && equalsHex(receivable.getTokenizeTxHash(), txHash);
    }

    private boolean sameFundingMetadata(
            ReceivableResponse receivable,
            Long companyId,
            String walletAddress,
            String mockTokenAddress,
            String txHash
    ) {
        return Objects.equals(receivable.getFunderCompanyId(), companyId)
                && equalsHex(receivable.getFunderWalletAddress(), walletAddress)
                && equalsHex(receivable.getMockTokenAddress(), mockTokenAddress)
                && equalsHex(receivable.getFundingTxHash(), txHash);
    }

    private void requireThirdPartyFunder(
            Long companyId,
            ReceivableResponse receivable
    ) {
        if (Objects.equals(companyId, receivable.getSellerCompanyId())
                || Objects.equals(companyId, receivable.getBuyerCompanyId())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "RELATED_PARTY_CANNOT_FUND",
                    "Seller와 Buyer 회사는 해당 채권에 자금을 공급할 수 없습니다."
            );
        }
    }

    private String configuredMockTokenAddress() {
        String value = blockchainProperties.getMockKrwAddress();
        if (value == null
                || !ADDRESS_PATTERN.matcher(value).matches()
                || ZERO_ADDRESS.equalsIgnoreCase(value)) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "BLOCKCHAIN_RPC_NOT_CONFIGURED",
                    "GIWA_MOCK_KRW_ADDRESS 환경변수를 설정해 주세요."
            );
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private boolean isZeroAddress(String address) {
        return "0x0000000000000000000000000000000000000000".equalsIgnoreCase(address);
    }

    private ApiException blockchainMetadataConflict() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "BLOCKCHAIN_METADATA_CONFLICT",
                "이미 저장된 블록체인 정보와 요청 정보가 일치하지 않습니다."
        );
    }

    private ApiException invalidStatus(String expected, String actual) {
        return new ApiException(
                HttpStatus.CONFLICT,
                "INVALID_RECEIVABLE_STATUS",
                "채권 상태가 올바르지 않습니다. 필요 상태: " + expected + ", 현재 상태: " + actual
        );
    }

    private String normalizeDocumentHash(String documentHash) {
        if (documentHash == null || documentHash.isBlank()) return null;
        return documentHash.startsWith("0x") ? documentHash : "0x" + documentHash;
    }
}
