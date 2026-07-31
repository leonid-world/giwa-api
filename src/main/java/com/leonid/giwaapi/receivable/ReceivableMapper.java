package com.leonid.giwaapi.receivable;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ReceivableMapper {

    String RESPONSE_SELECT = """
            SELECT r.receivable_id,
                   r.seller_company_id,
                   seller.company_name AS seller_company_name,
                   r.buyer_company_id,
                   buyer.company_name AS buyer_company_name,
                   r.funder_company_id,
                   funder.company_name AS funder_company_name,
                   r.seller_wallet_address,
                   r.buyer_wallet_address,
                   r.funder_wallet_address,
                   r.currency_code,
                   r.face_value,
                   r.funding_amount,
                   r.issue_date,
                   r.maturity_date,
                   r.status,
                   r.document_hash,
                   r.onchain_receivable_id,
                   r.token_id,
                   r.contract_address,
                   r.mock_token_address,
                   r.create_tx_hash,
                   r.verify_tx_hash,
                   r.tokenize_tx_hash,
                   r.funding_tx_hash,
                   r.repay_tx_hash,
                   r.description,
                   r.created_at,
                   r.updated_at
              FROM receivables r
              JOIN companies seller ON seller.company_id = r.seller_company_id
              JOIN companies buyer ON buyer.company_id = r.buyer_company_id
              LEFT JOIN companies funder ON funder.company_id = r.funder_company_id
            """;

    @Insert("""
            INSERT INTO receivables (
                seller_company_id, buyer_company_id,
                seller_wallet_address, buyer_wallet_address,
                currency_code, face_value, funding_amount,
                issue_date, maturity_date, status,
                document_hash, description, created_by
            ) VALUES (
                #{sellerCompanyId}, #{buyerCompanyId},
                #{sellerWalletAddress}, #{buyerWalletAddress},
                #{currencyCode}, #{faceValue}, #{fundingAmount},
                #{issueDate}, #{maturityDate}, #{status},
                #{documentHash}, #{description}, #{createdBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "receivableId", keyColumn = "receivable_id")
    void insert(Receivable receivable);

    @Select(RESPONSE_SELECT + """
            WHERE r.seller_company_id = #{companyId}
               OR r.buyer_company_id = #{companyId}
               OR r.funder_company_id = #{companyId}
            ORDER BY r.created_at DESC, r.receivable_id DESC
            """)
    List<ReceivableResponse> findAllVisibleToCompany(Long companyId);

    @Select(RESPONSE_SELECT + """
            WHERE r.status = 'TOKENIZED'
              AND r.funder_company_id IS NULL
              AND r.seller_company_id <> #{companyId}
              AND r.buyer_company_id <> #{companyId}
            ORDER BY r.created_at DESC, r.receivable_id DESC
            """)
    List<ReceivableResponse> findAllFundingOpportunities(Long companyId);

    @Select(RESPONSE_SELECT + """
            WHERE r.receivable_id = #{receivableId}
              AND (
                    r.seller_company_id = #{companyId}
                 OR r.buyer_company_id = #{companyId}
                 OR r.funder_company_id = #{companyId}
                 OR (
                        r.status = 'TOKENIZED'
                    AND r.funder_company_id IS NULL
                    AND r.seller_company_id <> #{companyId}
                    AND r.buyer_company_id <> #{companyId}
                 )
              )
            """)
    Optional<ReceivableResponse> findVisibleById(
            @Param("receivableId") Long receivableId,
            @Param("companyId") Long companyId
    );

    @Select(RESPONSE_SELECT + """
            WHERE r.receivable_id = #{receivableId}
              AND (
                    r.seller_company_id = #{companyId}
                 OR r.buyer_company_id = #{companyId}
                 OR r.funder_company_id = #{companyId}
              )
            """)
    Optional<ReceivableResponse> findRelatedById(
            @Param("receivableId") Long receivableId,
            @Param("companyId") Long companyId
    );

    @Select(RESPONSE_SELECT + """
            WHERE r.receivable_id = #{receivableId}
            """)
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    Optional<ReceivableResponse> findById(Long receivableId);

    @Update("""
            UPDATE receivables
               SET onchain_receivable_id = #{onchainReceivableId},
                   contract_address = #{contractAddress},
                   create_tx_hash = #{txHash},
                   updated_by = #{userId}
             WHERE receivable_id = #{receivableId}
               AND seller_company_id = #{companyId}
               AND status = 'CREATED'
               AND onchain_receivable_id IS NULL
               AND contract_address IS NULL
               AND create_tx_hash IS NULL
            """)
    int markChainCreated(
            @Param("receivableId") Long receivableId,
            @Param("companyId") Long companyId,
            @Param("userId") Long userId,
            @Param("onchainReceivableId") Long onchainReceivableId,
            @Param("contractAddress") String contractAddress,
            @Param("txHash") String txHash
    );

    @Select("""
            SELECT COUNT(*)
              FROM receivables
             WHERE receivable_id <> #{receivableId}
               AND contract_address = #{contractAddress}
               AND onchain_receivable_id = #{onchainReceivableId}
            """)
    int countChainIdentityUsedByOther(
            @Param("receivableId") Long receivableId,
            @Param("onchainReceivableId") Long onchainReceivableId,
            @Param("contractAddress") String contractAddress
    );

    @Update("""
            UPDATE receivables
               SET status = 'VERIFIED',
                   verify_tx_hash = #{txHash},
                   updated_by = #{userId}
             WHERE receivable_id = #{receivableId}
               AND buyer_company_id = #{companyId}
               AND status = 'CREATED'
               AND onchain_receivable_id IS NOT NULL
               AND contract_address IS NOT NULL
               AND create_tx_hash IS NOT NULL
               AND verify_tx_hash IS NULL
            """)
    int markVerified(
            @Param("receivableId") Long receivableId,
            @Param("companyId") Long companyId,
            @Param("userId") Long userId,
            @Param("txHash") String txHash
    );

    @Update("""
            UPDATE receivables
               SET status = 'TOKENIZED',
                   token_id = #{tokenId},
                   tokenize_tx_hash = #{txHash},
                   updated_by = #{userId}
             WHERE receivable_id = #{receivableId}
               AND seller_company_id = #{companyId}
               AND status = 'VERIFIED'
               AND onchain_receivable_id IS NOT NULL
               AND contract_address IS NOT NULL
               AND create_tx_hash IS NOT NULL
               AND verify_tx_hash IS NOT NULL
               AND token_id IS NULL
               AND tokenize_tx_hash IS NULL
            """)
    int markTokenized(
            @Param("receivableId") Long receivableId,
            @Param("companyId") Long companyId,
            @Param("userId") Long userId,
            @Param("tokenId") Long tokenId,
            @Param("txHash") String txHash
    );

    @Update("""
            UPDATE receivables
               SET status = 'FUNDED',
                   funder_company_id = #{companyId},
                   funder_wallet_address = #{walletAddress},
                   mock_token_address = #{mockTokenAddress},
                   funding_tx_hash = #{txHash},
                   updated_by = #{userId}
             WHERE receivable_id = #{receivableId}
               AND seller_company_id <> #{companyId}
               AND buyer_company_id <> #{companyId}
               AND status = 'TOKENIZED'
               AND onchain_receivable_id IS NOT NULL
               AND contract_address IS NOT NULL
               AND create_tx_hash IS NOT NULL
               AND verify_tx_hash IS NOT NULL
               AND token_id IS NOT NULL
               AND tokenize_tx_hash IS NOT NULL
               AND funder_company_id IS NULL
               AND funder_wallet_address IS NULL
               AND mock_token_address IS NULL
               AND funding_tx_hash IS NULL
            """)
    int markFunded(
            @Param("receivableId") Long receivableId,
            @Param("companyId") Long companyId,
            @Param("userId") Long userId,
            @Param("walletAddress") String walletAddress,
            @Param("mockTokenAddress") String mockTokenAddress,
            @Param("txHash") String txHash
    );

    @Update("""
            UPDATE receivables
               SET status = 'REPAID',
                   repay_tx_hash = #{txHash},
                   updated_by = #{userId}
             WHERE receivable_id = #{receivableId}
               AND buyer_company_id = #{companyId}
               AND status = 'FUNDED'
               AND onchain_receivable_id IS NOT NULL
               AND contract_address IS NOT NULL
               AND create_tx_hash IS NOT NULL
               AND verify_tx_hash IS NOT NULL
               AND token_id IS NOT NULL
               AND tokenize_tx_hash IS NOT NULL
               AND funder_company_id IS NOT NULL
               AND funder_wallet_address IS NOT NULL
               AND mock_token_address IS NOT NULL
               AND funding_tx_hash IS NOT NULL
               AND repay_tx_hash IS NULL
            """)
    int markRepaid(
            @Param("receivableId") Long receivableId,
            @Param("companyId") Long companyId,
            @Param("userId") Long userId,
            @Param("txHash") String txHash
    );

    @Select("""
            SELECT COUNT(*)
              FROM receivables
             WHERE receivable_id <> #{receivableId}
               AND contract_address = #{contractAddress}
               AND token_id = #{tokenId}
            """)
    int countTokenIdentityUsedByOther(
            @Param("receivableId") Long receivableId,
            @Param("contractAddress") String contractAddress,
            @Param("tokenId") Long tokenId
    );

    @Select("""
            SELECT COUNT(*)
              FROM receivables
             WHERE create_tx_hash = #{txHash}
                OR verify_tx_hash = #{txHash}
                OR tokenize_tx_hash = #{txHash}
                OR funding_tx_hash = #{txHash}
                OR repay_tx_hash = #{txHash}
            """)
    int countTransactionHashUsage(@Param("txHash") String txHash);

    @Insert("""
            INSERT INTO receivable_status_history (
                receivable_id, previous_status, current_status,
                changed_by_company_id, changed_by_wallet_address, change_reason
            ) VALUES (
                #{receivableId}, NULL, 'CREATED',
                #{companyId}, #{walletAddress}, 'Receivable created'
            )
            """)
    void insertCreatedHistory(
            @Param("receivableId") Long receivableId,
            @Param("companyId") Long companyId,
            @Param("walletAddress") String walletAddress
    );

    @Insert("""
            INSERT INTO receivable_status_history (
                receivable_id, previous_status, current_status,
                changed_by_company_id, changed_by_wallet_address,
                tx_hash, change_reason
            ) VALUES (
                #{receivableId}, 'CREATED', 'VERIFIED',
                #{companyId}, #{walletAddress},
                #{txHash}, 'Buyer verified receivable onchain'
            )
            """)
    void insertVerifiedHistory(
            @Param("receivableId") Long receivableId,
            @Param("companyId") Long companyId,
            @Param("walletAddress") String walletAddress,
            @Param("txHash") String txHash
    );

    @Insert("""
            INSERT INTO receivable_status_history (
                receivable_id, previous_status, current_status,
                changed_by_company_id, changed_by_wallet_address,
                tx_hash, change_reason
            ) VALUES (
                #{receivableId}, 'VERIFIED', 'TOKENIZED',
                #{companyId}, #{walletAddress},
                #{txHash}, 'Seller tokenized receivable onchain'
            )
            """)
    void insertTokenizedHistory(
            @Param("receivableId") Long receivableId,
            @Param("companyId") Long companyId,
            @Param("walletAddress") String walletAddress,
            @Param("txHash") String txHash
    );

    @Insert("""
            INSERT INTO receivable_status_history (
                receivable_id, previous_status, current_status,
                changed_by_company_id, changed_by_wallet_address,
                tx_hash, change_reason
            ) VALUES (
                #{receivableId}, 'TOKENIZED', 'FUNDED',
                #{companyId}, #{walletAddress},
                #{txHash}, 'Funder funded receivable onchain'
            )
            """)
    void insertFundedHistory(
            @Param("receivableId") Long receivableId,
            @Param("companyId") Long companyId,
            @Param("walletAddress") String walletAddress,
            @Param("txHash") String txHash
    );

    @Insert("""
            INSERT INTO receivable_status_history (
                receivable_id, previous_status, current_status,
                changed_by_company_id, changed_by_wallet_address,
                tx_hash, change_reason
            ) VALUES (
                #{receivableId}, 'FUNDED', 'REPAID',
                #{companyId}, #{walletAddress},
                #{txHash}, 'Buyer repaid receivable onchain'
            )
            """)
    void insertRepaidHistory(
            @Param("receivableId") Long receivableId,
            @Param("companyId") Long companyId,
            @Param("walletAddress") String walletAddress,
            @Param("txHash") String txHash
    );
}
