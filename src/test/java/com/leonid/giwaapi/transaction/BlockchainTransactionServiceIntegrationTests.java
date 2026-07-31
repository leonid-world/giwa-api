package com.leonid.giwaapi.transaction;

import com.leonid.giwaapi.auth.AuthResponse;
import com.leonid.giwaapi.auth.AuthService;
import com.leonid.giwaapi.auth.SignupRequest;
import com.leonid.giwaapi.common.error.ApiException;
import com.leonid.giwaapi.receivable.ReceivableChainCreatedRequest;
import com.leonid.giwaapi.receivable.ReceivableCreateRequest;
import com.leonid.giwaapi.receivable.ReceivableResponse;
import com.leonid.giwaapi.receivable.ReceivableService;
import com.leonid.giwaapi.wallet.WalletConnectRequest;
import com.leonid.giwaapi.wallet.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BlockchainTransactionServiceIntegrationTests {

    private static final String SELLER_EMAIL = "journal-seller@example.com";
    private static final String BUYER_EMAIL = "journal-buyer@example.com";
    private static final String OUTSIDER_EMAIL = "journal-outsider@example.com";
    private static final String SELLER_WALLET = "0x1111111111111111111111111111111111111111";
    private static final String BUYER_WALLET = "0x2222222222222222222222222222222222222222";
    private static final String CONTRACT = "0x" + "A".repeat(40);
    private static final String OTHER_CONTRACT = "0x" + "B".repeat(40);
    private static final String CREATE_TX = "0x" + "A".repeat(64);
    private static final String VERIFY_TX = "0x" + "B".repeat(64);
    private static final String TOKENIZE_TX = "0x" + "C".repeat(64);
    private static final String SECOND_TX = "0x" + "D".repeat(64);

    @Autowired
    private AuthService authService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private ReceivableService receivableService;

    @Autowired
    private BlockchainTransactionService transactionService;

    @Autowired
    private BlockchainTransactionMapper transactionMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BlockchainTransactionVerifier transactionVerifier;

    @BeforeEach
    void stubRpcVerification() {
        when(transactionVerifier.verify(any(), any(), any()))
                .thenAnswer(invocation -> {
                    BlockchainTransactionResponse transaction =
                            invocation.getArgument(0);
                    Long expectedReceivableId = invocation.getArgument(2);
                    Long eventReceivableId = expectedReceivableId != null
                            ? expectedReceivableId
                            : 1L;
                    Long eventTokenId = "TOKENIZE_RECEIVABLE".equals(
                            transaction.getTransactionType()
                    ) ? 1L : null;
                    return new VerifiedBlockchainTransaction(
                            91342L,
                            123L,
                            "0x" + "1".repeat(64),
                            21000L,
                            new BigDecimal("1000000000"),
                            eventReceivableId,
                            eventTokenId
                    );
                });
    }

    @Test
    void createsPendingJournalFromServerDerivedMetadataIdempotently() {
        Fixture fixture = createFixture();
        BlockchainTransactionCreateRequest request = createRequest(
                fixture.receivable().getReceivableId(),
                "create_receivable",
                CONTRACT,
                CREATE_TX
        );

        BlockchainTransactionResponse created = transactionService.create(SELLER_EMAIL, request);
        BlockchainTransactionResponse retried = transactionService.create(SELLER_EMAIL, request);

        assertThat(created.getBlockchainTransactionId()).isNotNull();
        assertThat(retried.getBlockchainTransactionId())
                .isEqualTo(created.getBlockchainTransactionId());
        assertThat(created.getReceivableId()).isEqualTo(fixture.receivable().getReceivableId());
        assertThat(created.getCompanyId()).isEqualTo(fixture.receivable().getSellerCompanyId());
        assertThat(created.getWalletAddress()).isEqualTo(SELLER_WALLET);
        assertThat(created.getTransactionType()).isEqualTo("CREATE_RECEIVABLE");
        assertThat(created.getChainId()).isEqualTo(1337L);
        assertThat(created.getContractAddress()).isEqualTo(CONTRACT.toLowerCase());
        assertThat(created.getFunctionName()).isEqualTo("createReceivable");
        assertThat(created.getTxHash()).isEqualTo(CREATE_TX.toLowerCase());
        assertThat(created.getTxStatus()).isEqualTo("PENDING");
        assertThat(created.getSubmittedAt()).isNotNull();

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM blockchain_transactions WHERE tx_hash = ?",
                Integer.class,
                CREATE_TX.toLowerCase()
        );
        assertThat(count).isEqualTo(1);

        assertApiError(
                () -> transactionService.create(
                        SELLER_EMAIL,
                        createRequest(
                                fixture.receivable().getReceivableId(),
                                "CREATE_RECEIVABLE",
                                OTHER_CONTRACT,
                                CREATE_TX
                        )
                ),
                HttpStatus.CONFLICT,
                "BLOCKCHAIN_TRANSACTION_CONFLICT"
        );
    }

    @Test
    void enforcesActorStateContractAndStoredWalletForSupportedTypes() {
        Fixture fixture = createFixture();
        Long receivableId = fixture.receivable().getReceivableId();

        assertApiError(
                () -> transactionService.create(
                        BUYER_EMAIL,
                        createRequest(receivableId, "CREATE_RECEIVABLE", CONTRACT, CREATE_TX)
                ),
                HttpStatus.FORBIDDEN,
                "ONLY_SELLER"
        );
        assertApiError(
                () -> transactionService.create(
                        BUYER_EMAIL,
                        createRequest(receivableId, "VERIFY_RECEIVABLE", CONTRACT, VERIFY_TX)
                ),
                HttpStatus.CONFLICT,
                "RECEIVABLE_NOT_ONCHAIN"
        );

        markReceivableChainCreated(receivableId);

        assertApiError(
                () -> transactionService.create(
                        SELLER_EMAIL,
                        createRequest(receivableId, "VERIFY_RECEIVABLE", CONTRACT, VERIFY_TX)
                ),
                HttpStatus.FORBIDDEN,
                "ONLY_BUYER"
        );
        assertApiError(
                () -> transactionService.create(
                        BUYER_EMAIL,
                        createRequest(receivableId, "VERIFY_RECEIVABLE", OTHER_CONTRACT, VERIFY_TX)
                ),
                HttpStatus.CONFLICT,
                "CONTRACT_ADDRESS_MISMATCH"
        );

        BlockchainTransactionResponse verification = transactionService.create(
                BUYER_EMAIL,
                createRequest(receivableId, "VERIFY_RECEIVABLE", CONTRACT, VERIFY_TX)
        );
        assertThat(verification.getWalletAddress()).isEqualTo(BUYER_WALLET);
        assertThat(verification.getFunctionName()).isEqualTo("verifyReceivable");

        assertApiError(
                () -> transactionService.create(
                        SELLER_EMAIL,
                        createRequest(receivableId, "TOKENIZE_RECEIVABLE", CONTRACT, TOKENIZE_TX)
                ),
                HttpStatus.CONFLICT,
                "INVALID_RECEIVABLE_STATUS"
        );

        jdbcTemplate.update(
                """
                UPDATE receivables
                   SET status = 'VERIFIED',
                       verify_tx_hash = ?
                 WHERE receivable_id = ?
                """,
                "0x" + "e".repeat(64),
                receivableId
        );
        BlockchainTransactionResponse tokenization = transactionService.create(
                SELLER_EMAIL,
                createRequest(receivableId, "TOKENIZE_RECEIVABLE", CONTRACT, TOKENIZE_TX)
        );
        assertThat(tokenization.getTransactionType()).isEqualTo("TOKENIZE_RECEIVABLE");
        assertThat(tokenization.getFunctionName()).isEqualTo("tokenizeReceivable");

        assertApiError(
                () -> transactionService.create(
                        SELLER_EMAIL,
                        createRequest(receivableId, "UNSUPPORTED", CONTRACT, SECOND_TX)
                ),
                HttpStatus.BAD_REQUEST,
                "INVALID_TRANSACTION_TYPE"
        );

        walletService.connect(
                SELLER_EMAIL,
                new WalletConnectRequest("0x9999999999999999999999999999999999999999", 9999L)
        );
        assertApiError(
                () -> transactionService.create(
                        SELLER_EMAIL,
                        createRequest(receivableId, "TOKENIZE_RECEIVABLE", CONTRACT, SECOND_TX)
                ),
                HttpStatus.CONFLICT,
                "RECEIVABLE_WALLET_NOT_MAPPED"
        );
    }

    @Test
    void transitionsPendingToConfirmedOrFailedAndKeepsRetriesIdempotent() {
        Fixture fixture = createFixture();
        Long receivableId = fixture.receivable().getReceivableId();
        transactionService.create(
                SELLER_EMAIL,
                createRequest(receivableId, "CREATE_RECEIVABLE", CONTRACT, CREATE_TX)
        );
        transactionService.create(
                SELLER_EMAIL,
                createRequest(receivableId, "CREATE_RECEIVABLE", CONTRACT, SECOND_TX)
        );

        BlockchainTransactionConfirmedRequest confirmedRequest =
                new BlockchainTransactionConfirmedRequest("123", "21000", "1000000000");
        BlockchainTransactionResponse confirmed = transactionService.markConfirmed(
                SELLER_EMAIL,
                CREATE_TX,
                confirmedRequest
        );
        BlockchainTransactionResponse confirmedRetry = transactionService.markConfirmed(
                SELLER_EMAIL,
                CREATE_TX.toLowerCase(),
                confirmedRequest
        );

        assertThat(confirmedRetry.getBlockchainTransactionId())
                .isEqualTo(confirmed.getBlockchainTransactionId());
        assertThat(confirmed.getTxStatus()).isEqualTo("CONFIRMED");
        assertThat(confirmed.getChainId()).isEqualTo(91342L);
        assertThat(confirmed.getBlockNumber()).isEqualTo(123L);
        assertThat(confirmed.getBlockHash()).isEqualTo("0x" + "1".repeat(64));
        assertThat(confirmed.getGasUsed()).isEqualTo(21000L);
        assertThat(confirmed.getEffectiveGasPrice()).isEqualByComparingTo("1000000000");
        assertThat(confirmed.getEventReceivableId()).isEqualTo(1L);
        assertThat(confirmed.getRpcVerifiedAt()).isNotNull();
        assertThat(confirmed.getVerificationVersion()).isEqualTo(1L);
        assertThat(confirmed.getConfirmedAt()).isNotNull();
        assertThat(transactionService.create(
                SELLER_EMAIL,
                createRequest(receivableId, "CREATE_RECEIVABLE", CONTRACT, CREATE_TX)
        ).getTxStatus()).isEqualTo("CONFIRMED");

        BlockchainTransactionResponse advisoryRetry =
                transactionService.markConfirmed(
                        SELLER_EMAIL,
                        CREATE_TX,
                        new BlockchainTransactionConfirmedRequest(
                                "124",
                                "22000",
                                "2000000000"
                        )
                );
        assertThat(advisoryRetry.getBlockNumber()).isEqualTo(123L);
        assertThat(advisoryRetry.getGasUsed()).isEqualTo(21000L);
        assertApiError(
                () -> transactionService.markFailed(
                        SELLER_EMAIL,
                        CREATE_TX,
                        new BlockchainTransactionFailedRequest("REVERTED", "execution reverted")
                ),
                HttpStatus.CONFLICT,
                "INVALID_BLOCKCHAIN_TRANSACTION_STATUS"
        );

        BlockchainTransactionFailedRequest failedRequest =
                new BlockchainTransactionFailedRequest("TRANSACTION_REPLACED", "replacement transaction used");
        BlockchainTransactionResponse failed = transactionService.markFailed(
                SELLER_EMAIL,
                SECOND_TX,
                failedRequest
        );
        BlockchainTransactionResponse failedRetry = transactionService.markFailed(
                SELLER_EMAIL,
                SECOND_TX.toLowerCase(),
                failedRequest
        );

        assertThat(failedRetry.getBlockchainTransactionId())
                .isEqualTo(failed.getBlockchainTransactionId());
        assertThat(failed.getTxStatus()).isEqualTo("FAILED");
        assertThat(failed.getErrorCode()).isEqualTo("TRANSACTION_REPLACED");
        assertThat(failed.getErrorMessage()).isEqualTo("replacement transaction used");
        assertThat(failed.getConfirmedAt()).isNull();
        assertThat(transactionService.create(
                SELLER_EMAIL,
                createRequest(receivableId, "CREATE_RECEIVABLE", CONTRACT, SECOND_TX)
        ).getTxStatus()).isEqualTo("FAILED");

        assertApiError(
                () -> transactionService.markFailed(
                        SELLER_EMAIL,
                        SECOND_TX,
                        new BlockchainTransactionFailedRequest("REVERTED", "different failure")
                ),
                HttpStatus.CONFLICT,
                "BLOCKCHAIN_TRANSACTION_CONFLICT"
        );
        assertApiError(
                () -> transactionService.markConfirmed(
                        SELLER_EMAIL,
                        SECOND_TX,
                        confirmedRequest
                ),
                HttpStatus.CONFLICT,
                "INVALID_BLOCKCHAIN_TRANSACTION_STATUS"
        );
    }

    @Test
    void rejectsInvalidReceiptNumbersWithoutChangingPendingStatus() {
        Fixture fixture = createFixture();
        transactionService.create(
                SELLER_EMAIL,
                createRequest(
                        fixture.receivable().getReceivableId(),
                        "CREATE_RECEIVABLE",
                        CONTRACT,
                        CREATE_TX
                )
        );

        assertApiError(
                () -> transactionService.markConfirmed(
                        SELLER_EMAIL,
                        CREATE_TX,
                        new BlockchainTransactionConfirmedRequest(
                                "9223372036854775808",
                                "21000",
                                "1000000000"
                        )
                ),
                HttpStatus.BAD_REQUEST,
                "INVALID_RECEIPT_METADATA"
        );
        assertThat(transactionService.getAllByReceivable(
                SELLER_EMAIL,
                fixture.receivable().getReceivableId()
        ).get(0).getTxStatus()).isEqualTo("PENDING");
    }

    @Test
    void recordsOnlyDeterministicRpcVerificationFailuresAsFailed() {
        Fixture fixture = createFixture();
        Long receivableId = fixture.receivable().getReceivableId();
        transactionService.create(
                SELLER_EMAIL,
                createRequest(
                        receivableId,
                        "CREATE_RECEIVABLE",
                        CONTRACT,
                        CREATE_TX
                )
        );
        transactionService.create(
                SELLER_EMAIL,
                createRequest(
                        receivableId,
                        "CREATE_RECEIVABLE",
                        CONTRACT,
                        SECOND_TX
                )
        );

        doThrow(new BlockchainVerificationException(
                        HttpStatus.CONFLICT,
                        "BLOCKCHAIN_TRANSACTION_REVERTED",
                        "블록체인에서 트랜잭션 실행이 실패했습니다.",
                        true
                ))
                .when(transactionVerifier)
                .verify(any(), any(), any());
        assertApiError(
                () -> transactionService.markConfirmed(
                        SELLER_EMAIL,
                        CREATE_TX,
                        new BlockchainTransactionConfirmedRequest(
                                "123",
                                "21000",
                                "1000000000"
                        )
                ),
                HttpStatus.CONFLICT,
                "BLOCKCHAIN_TRANSACTION_REVERTED"
        );
        BlockchainTransactionResponse failed =
                transactionService.getAllByReceivable(
                        SELLER_EMAIL,
                        receivableId
                ).stream()
                        .filter(transaction ->
                                CREATE_TX.equalsIgnoreCase(
                                        transaction.getTxHash()
                                )
                        )
                        .findFirst()
                        .orElseThrow();
        assertThat(failed.getTxStatus()).isEqualTo("FAILED");
        assertThat(failed.getErrorCode())
                .isEqualTo("BLOCKCHAIN_TRANSACTION_REVERTED");

        doThrow(new BlockchainVerificationException(
                        HttpStatus.CONFLICT,
                        "BLOCKCHAIN_TRANSACTION_PENDING",
                        "블록체인 트랜잭션이 아직 채굴되지 않았습니다.",
                        false
                ))
                .when(transactionVerifier)
                .verify(any(), any(), any());
        assertApiError(
                () -> transactionService.markConfirmed(
                        SELLER_EMAIL,
                        SECOND_TX,
                        new BlockchainTransactionConfirmedRequest(
                                "123",
                                "21000",
                                "1000000000"
                        )
                ),
                HttpStatus.CONFLICT,
                "BLOCKCHAIN_TRANSACTION_PENDING"
        );
        BlockchainTransactionResponse pending =
                transactionService.getAllByReceivable(
                        SELLER_EMAIL,
                        receivableId
                ).stream()
                        .filter(transaction ->
                                SECOND_TX.equalsIgnoreCase(
                                        transaction.getTxHash()
                                )
                        )
                        .findFirst()
                        .orElseThrow();
        assertThat(pending.getTxStatus()).isEqualTo("PENDING");
        assertThat(pending.getErrorCode()).isNull();
    }

    @Test
    void revalidatesConfirmedProofImmediatelyBeforeLifecycleSynchronization() {
        Fixture fixture = createFixture();
        Long receivableId = fixture.receivable().getReceivableId();
        transactionService.create(
                SELLER_EMAIL,
                createRequest(
                        receivableId,
                        "CREATE_RECEIVABLE",
                        CONTRACT,
                        CREATE_TX
                )
        );
        transactionService.markConfirmed(
                SELLER_EMAIL,
                CREATE_TX,
                new BlockchainTransactionConfirmedRequest(
                        "123",
                        "21000",
                        "1000000000"
                )
        );
        clearInvocations(transactionVerifier);

        ReceivableResponse synchronizedReceivable =
                receivableService.markChainCreated(
                        SELLER_EMAIL,
                        receivableId,
                        new ReceivableChainCreatedRequest(
                                "1",
                                CREATE_TX,
                                CONTRACT
                        )
                );

        verify(transactionVerifier, times(1))
                .verify(any(), any(), any());
        BlockchainTransactionResponse refreshed =
                transactionService.getAllByReceivable(
                        SELLER_EMAIL,
                        receivableId
                ).get(0);
        assertThat(refreshed.getVerificationVersion()).isEqualTo(2L);
        assertThat(transactionMapper.markVerificationFailed(
                CREATE_TX.toLowerCase(),
                synchronizedReceivable.getSellerCompanyId(),
                "BLOCKCHAIN_TRANSACTION_REVERTED",
                "stale terminal result",
                refreshed.getVerificationVersion()
        )).isZero();
        assertThat(transactionMapper.markRpcConfirmed(
                CREATE_TX.toLowerCase(),
                synchronizedReceivable.getSellerCompanyId(),
                91342L,
                124L,
                "0x" + "2".repeat(64),
                22000L,
                new BigDecimal("2000000000"),
                1L,
                null,
                1L
        )).isZero();
    }

    @Test
    void keepsVerifiedJournalWhenSynchronizationPayloadClaimsWrongEventId() {
        Fixture fixture = createFixture();
        Long receivableId = fixture.receivable().getReceivableId();
        transactionService.create(
                SELLER_EMAIL,
                createRequest(
                        receivableId,
                        "CREATE_RECEIVABLE",
                        CONTRACT,
                        CREATE_TX
                )
        );
        transactionService.markConfirmed(
                SELLER_EMAIL,
                CREATE_TX,
                new BlockchainTransactionConfirmedRequest(
                        "123",
                        "21000",
                        "1000000000"
                )
        );

        assertApiError(
                () -> receivableService.markChainCreated(
                        SELLER_EMAIL,
                        receivableId,
                        new ReceivableChainCreatedRequest(
                                "2",
                                CREATE_TX,
                                CONTRACT
                        )
                ),
                HttpStatus.CONFLICT,
                "BLOCKCHAIN_SYNCHRONIZATION_EVENT_MISMATCH"
        );

        BlockchainTransactionResponse journal =
                transactionService.getAllByReceivable(
                        SELLER_EMAIL,
                        receivableId
                ).get(0);
        assertThat(journal.getTxStatus()).isEqualTo("CONFIRMED");
        assertThat(journal.getErrorCode()).isNull();
    }

    @Test
    void marksLegacyUnverifiedConfirmationFailedOnTerminalProofError() {
        Fixture fixture = createFixture();
        Long receivableId = fixture.receivable().getReceivableId();
        transactionService.create(
                SELLER_EMAIL,
                createRequest(
                        receivableId,
                        "CREATE_RECEIVABLE",
                        CONTRACT,
                        CREATE_TX
                )
        );
        jdbcTemplate.update(
                """
                UPDATE blockchain_transactions
                   SET tx_status = 'CONFIRMED',
                       block_number = 1,
                       gas_used = 1,
                       effective_gas_price = 1,
                       confirmed_at = CURRENT_TIMESTAMP
                 WHERE tx_hash = ?
                """,
                CREATE_TX.toLowerCase()
        );
        doThrow(new BlockchainVerificationException(
                        HttpStatus.CONFLICT,
                        "BLOCKCHAIN_TRANSACTION_REVERTED",
                        "블록체인에서 트랜잭션 실행이 실패했습니다.",
                        true
                ))
                .when(transactionVerifier)
                .verify(any(), any(), any());

        assertApiError(
                () -> receivableService.markChainCreated(
                        SELLER_EMAIL,
                        receivableId,
                        new ReceivableChainCreatedRequest(
                                "1",
                                CREATE_TX,
                                CONTRACT
                        )
                ),
                HttpStatus.CONFLICT,
                "BLOCKCHAIN_TRANSACTION_REVERTED"
        );

        BlockchainTransactionResponse failed =
                transactionService.getAllByReceivable(
                        SELLER_EMAIL,
                        receivableId
                ).get(0);
        assertThat(failed.getTxStatus()).isEqualTo("FAILED");
        assertThat(failed.getRpcVerifiedAt()).isNull();
        assertThat(failed.getErrorCode())
                .isEqualTo("BLOCKCHAIN_TRANSACTION_REVERTED");
    }

    @Test
    void restrictsStatusUpdatesToSubmittingCompanyAndListsForRelatedCompanies() {
        Fixture fixture = createFixture();
        Long receivableId = fixture.receivable().getReceivableId();
        BlockchainTransactionResponse creation = transactionService.create(
                SELLER_EMAIL,
                createRequest(receivableId, "CREATE_RECEIVABLE", CONTRACT, CREATE_TX)
        );
        markReceivableChainCreated(receivableId);
        BlockchainTransactionResponse verification = transactionService.create(
                BUYER_EMAIL,
                createRequest(receivableId, "VERIFY_RECEIVABLE", CONTRACT, VERIFY_TX)
        );

        List<BlockchainTransactionResponse> buyerView =
                transactionService.getAllByReceivable(BUYER_EMAIL, receivableId);
        List<BlockchainTransactionResponse> sellerView =
                transactionService.getAllByReceivable(SELLER_EMAIL, receivableId);

        assertThat(buyerView).extracting(BlockchainTransactionResponse::getBlockchainTransactionId)
                .containsExactly(
                        verification.getBlockchainTransactionId(),
                        creation.getBlockchainTransactionId()
                );
        assertThat(sellerView).hasSize(2);

        assertApiError(
                () -> transactionService.markConfirmed(
                        BUYER_EMAIL,
                        CREATE_TX,
                        new BlockchainTransactionConfirmedRequest("123", "21000", "1")
                ),
                HttpStatus.NOT_FOUND,
                "BLOCKCHAIN_TRANSACTION_NOT_FOUND"
        );
        assertApiError(
                () -> transactionService.getAllByReceivable(OUTSIDER_EMAIL, receivableId),
                HttpStatus.NOT_FOUND,
                "RECEIVABLE_NOT_FOUND"
        );
    }

    @Test
    void exposesValidatedApiAndSerializesBlockchainNumbersAsStrings() throws Exception {
        Fixture fixture = createFixture();
        BlockchainTransactionCreateRequest request = createRequest(
                fixture.receivable().getReceivableId(),
                "CREATE_RECEIVABLE",
                CONTRACT,
                CREATE_TX
        );

        mockMvc.perform(post("/blockchain-transactions")
                        .header("Authorization", "Bearer " + fixture.sellerAuth().accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.blockchainTransactionId").isString())
                .andExpect(jsonPath("$.receivableId").isString())
                .andExpect(jsonPath("$.companyId").isString())
                .andExpect(jsonPath("$.chainId").value("1337"))
                .andExpect(jsonPath("$.verificationVersion").value("0"))
                .andExpect(jsonPath("$.transactionType").value("CREATE_RECEIVABLE"))
                .andExpect(jsonPath("$.functionName").value("createReceivable"))
                .andExpect(jsonPath("$.txStatus").value("PENDING"));

        mockMvc.perform(patch("/blockchain-transactions/{txHash}/confirmed", CREATE_TX)
                        .header("Authorization", "Bearer " + fixture.sellerAuth().accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new BlockchainTransactionConfirmedRequest(
                                        "123",
                                        "21000",
                                        "1000000000"
                                )
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chainId").value("91342"))
                .andExpect(jsonPath("$.blockNumber").value("123"))
                .andExpect(jsonPath("$.blockHash").value("0x" + "1".repeat(64)))
                .andExpect(jsonPath("$.gasUsed").value("21000"))
                .andExpect(jsonPath("$.effectiveGasPrice").value("1000000000"))
                .andExpect(jsonPath("$.eventReceivableId").value("1"))
                .andExpect(jsonPath("$.rpcVerifiedAt").exists())
                .andExpect(jsonPath("$.verificationVersion").value("1"))
                .andExpect(jsonPath("$.txStatus").value("CONFIRMED"));

        BlockchainTransactionCreateRequest failedRequest = createRequest(
                fixture.receivable().getReceivableId(),
                "CREATE_RECEIVABLE",
                CONTRACT,
                SECOND_TX
        );
        mockMvc.perform(post("/blockchain-transactions")
                        .header("Authorization", "Bearer " + fixture.sellerAuth().accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(failedRequest)))
                .andExpect(status().isCreated());
        mockMvc.perform(patch("/blockchain-transactions/{txHash}/failed", SECOND_TX)
                        .header("Authorization", "Bearer " + fixture.sellerAuth().accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new BlockchainTransactionFailedRequest(
                                        "TRANSACTION_REPLACED",
                                        "replacement transaction used"
                                )
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.txStatus").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("TRANSACTION_REPLACED"));

        mockMvc.perform(get("/receivables/{receivableId}/transactions", fixture.receivable().getReceivableId())
                        .header("Authorization", "Bearer " + fixture.sellerAuth().accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].txHash").value(SECOND_TX.toLowerCase()))
                .andExpect(jsonPath("$[1].txHash").value(CREATE_TX.toLowerCase()));
    }

    private Fixture createFixture() {
        AuthResponse sellerAuth = authService.signup(new SignupRequest(
                SELLER_EMAIL,
                "password123",
                "Journal Seller",
                "Journal Seller Company",
                "6666666666"
        ));
        AuthResponse buyerAuth = authService.signup(new SignupRequest(
                BUYER_EMAIL,
                "password123",
                "Journal Buyer",
                "Journal Buyer Company",
                "7777777777"
        ));
        AuthResponse outsiderAuth = authService.signup(new SignupRequest(
                OUTSIDER_EMAIL,
                "password123",
                "Journal Outsider",
                "Journal Outsider Company",
                "8888888888"
        ));
        walletService.connect(SELLER_EMAIL, new WalletConnectRequest(SELLER_WALLET, 1337L));
        walletService.connect(BUYER_EMAIL, new WalletConnectRequest(BUYER_WALLET, 1337L));

        ReceivableResponse receivable = receivableService.create(
                SELLER_EMAIL,
                new ReceivableCreateRequest(
                        "7777777777",
                        new BigDecimal("1000000"),
                        new BigDecimal("900000"),
                        LocalDate.of(2026, 7, 30),
                        LocalDate.of(2026, 8, 30),
                        null,
                        "Blockchain transaction journal test"
                )
        );
        return new Fixture(receivable, sellerAuth, buyerAuth, outsiderAuth);
    }

    private void markReceivableChainCreated(Long receivableId) {
        jdbcTemplate.update(
                """
                UPDATE receivables
                   SET onchain_receivable_id = 1,
                       contract_address = ?,
                       create_tx_hash = ?
                 WHERE receivable_id = ?
                """,
                CONTRACT.toLowerCase(),
                "0x" + "f".repeat(64),
                receivableId
        );
    }

    private BlockchainTransactionCreateRequest createRequest(
            Long receivableId,
            String transactionType,
            String contractAddress,
            String txHash
    ) {
        return new BlockchainTransactionCreateRequest(
                receivableId,
                transactionType,
                contractAddress,
                txHash
        );
    }

    private void assertApiError(Runnable invocation, HttpStatus status, String code) {
        assertThatThrownBy(invocation::run)
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException apiException = (ApiException) error;
                    assertThat(apiException.getStatus()).isEqualTo(status);
                    assertThat(apiException.getCode()).isEqualTo(code);
                });
    }

    private record Fixture(
            ReceivableResponse receivable,
            AuthResponse sellerAuth,
            AuthResponse buyerAuth,
            AuthResponse outsiderAuth
    ) {
    }
}
