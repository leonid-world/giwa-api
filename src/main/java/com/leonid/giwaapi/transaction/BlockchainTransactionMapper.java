package com.leonid.giwaapi.transaction;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Mapper
public interface BlockchainTransactionMapper {

    String RESPONSE_SELECT = """
            SELECT blockchain_transaction_id,
                   receivable_id,
                   company_id,
                   wallet_address,
                   transaction_type,
                   chain_id,
                   contract_address,
                   function_name,
                   tx_hash,
                   block_number,
                   block_hash,
                   tx_status,
                   gas_used,
                   effective_gas_price,
                   event_receivable_id,
                   event_token_id,
                   rpc_verified_at,
                   verification_version,
                   error_code,
                   error_message,
                   submitted_at,
                   confirmed_at,
                   created_at,
                   updated_at
              FROM blockchain_transactions
            """;

    @Insert("""
            INSERT INTO blockchain_transactions (
                receivable_id,
                company_id,
                wallet_address,
                transaction_type,
                chain_id,
                contract_address,
                function_name,
                tx_hash,
                tx_status
            ) VALUES (
                #{receivableId},
                #{companyId},
                #{walletAddress},
                #{transactionType},
                #{chainId},
                #{contractAddress},
                #{functionName},
                #{txHash},
                #{txStatus}
            )
            """)
    @Options(
            useGeneratedKeys = true,
            keyProperty = "blockchainTransactionId",
            keyColumn = "blockchain_transaction_id"
    )
    void insert(BlockchainTransaction transaction);

    @Select(RESPONSE_SELECT + """
            WHERE tx_hash = #{txHash}
            """)
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    Optional<BlockchainTransactionResponse> findByTxHash(String txHash);

    @Select(RESPONSE_SELECT + """
            WHERE receivable_id = #{receivableId}
            ORDER BY submitted_at DESC, blockchain_transaction_id DESC
            """)
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<BlockchainTransactionResponse> findAllByReceivableId(Long receivableId);

    @Select(RESPONSE_SELECT + """
            WHERE receivable_id = #{receivableId}
              AND company_id = #{companyId}
              AND transaction_type = 'FUND_RECEIVABLE'
            ORDER BY submitted_at DESC, blockchain_transaction_id DESC
            """)
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<BlockchainTransactionResponse> findFundingByReceivableIdAndCompany(
            @Param("receivableId") Long receivableId,
            @Param("companyId") Long companyId
    );

    @Update("""
            UPDATE blockchain_transactions
               SET chain_id = #{chainId},
                   tx_status = 'CONFIRMED',
                   block_number = #{blockNumber},
                   block_hash = #{blockHash},
                   gas_used = #{gasUsed},
                   effective_gas_price = #{effectiveGasPrice},
                   event_receivable_id = #{eventReceivableId},
                   event_token_id = #{eventTokenId},
                   rpc_verified_at = CURRENT_TIMESTAMP,
                   verification_version = verification_version + 1,
                   error_code = NULL,
                   error_message = NULL,
                   confirmed_at = COALESCE(confirmed_at, CURRENT_TIMESTAMP),
                   updated_at = CURRENT_TIMESTAMP
             WHERE tx_hash = #{txHash}
               AND company_id = #{companyId}
               AND tx_status IN ('PENDING', 'CONFIRMED')
               AND verification_version = #{expectedVerificationVersion}
            """)
    int markRpcConfirmed(
            @Param("txHash") String txHash,
            @Param("companyId") Long companyId,
            @Param("chainId") Long chainId,
            @Param("blockNumber") Long blockNumber,
            @Param("blockHash") String blockHash,
            @Param("gasUsed") Long gasUsed,
            @Param("effectiveGasPrice") BigDecimal effectiveGasPrice,
            @Param("eventReceivableId") Long eventReceivableId,
            @Param("eventTokenId") Long eventTokenId,
            @Param("expectedVerificationVersion") Long expectedVerificationVersion
    );

    @Update("""
            UPDATE blockchain_transactions
               SET tx_status = 'FAILED',
                   error_code = #{errorCode},
                   error_message = #{errorMessage},
                   confirmed_at = NULL,
                   updated_at = CURRENT_TIMESTAMP
             WHERE tx_hash = #{txHash}
               AND company_id = #{companyId}
               AND tx_status = 'PENDING'
            """)
    int markFailed(
            @Param("txHash") String txHash,
            @Param("companyId") Long companyId,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage
    );

    @Update("""
            UPDATE blockchain_transactions
               SET tx_status = 'FAILED',
                   rpc_verified_at = NULL,
                   verification_version = verification_version + 1,
                   error_code = #{errorCode},
                   error_message = #{errorMessage},
                   confirmed_at = NULL,
                   updated_at = CURRENT_TIMESTAMP
             WHERE tx_hash = #{txHash}
               AND company_id = #{companyId}
               AND tx_status IN ('PENDING', 'CONFIRMED')
               AND verification_version = #{expectedVerificationVersion}
               AND NOT EXISTS (
                    SELECT 1
                      FROM receivables synchronized_receivable
                     WHERE synchronized_receivable.receivable_id =
                           blockchain_transactions.receivable_id
                       AND (
                            (
                                blockchain_transactions.transaction_type =
                                'CREATE_RECEIVABLE'
                                AND LOWER(synchronized_receivable.create_tx_hash) =
                                    blockchain_transactions.tx_hash
                            )
                            OR (
                                blockchain_transactions.transaction_type =
                                'VERIFY_RECEIVABLE'
                                AND LOWER(synchronized_receivable.verify_tx_hash) =
                                    blockchain_transactions.tx_hash
                            )
                            OR (
                                blockchain_transactions.transaction_type =
                                'TOKENIZE_RECEIVABLE'
                                AND LOWER(synchronized_receivable.tokenize_tx_hash) =
                                    blockchain_transactions.tx_hash
                            )
                            OR (
                                blockchain_transactions.transaction_type =
                                'FUND_RECEIVABLE'
                                AND LOWER(synchronized_receivable.funding_tx_hash) =
                                    blockchain_transactions.tx_hash
                            )
                            OR (
                                blockchain_transactions.transaction_type =
                                'REPAY_RECEIVABLE'
                                AND LOWER(synchronized_receivable.repay_tx_hash) =
                                    blockchain_transactions.tx_hash
                            )
                       )
               )
            """)
    int markVerificationFailed(
            @Param("txHash") String txHash,
            @Param("companyId") Long companyId,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("expectedVerificationVersion") Long expectedVerificationVersion
    );
}
