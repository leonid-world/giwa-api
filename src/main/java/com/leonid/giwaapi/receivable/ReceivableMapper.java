package com.leonid.giwaapi.receivable;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
            WHERE r.receivable_id = #{receivableId}
              AND (
                    r.seller_company_id = #{companyId}
                 OR r.buyer_company_id = #{companyId}
                 OR r.funder_company_id = #{companyId}
              )
            """)
    Optional<ReceivableResponse> findVisibleById(
            @Param("receivableId") Long receivableId,
            @Param("companyId") Long companyId
    );

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
}
