package com.leonid.giwaapi.wallet;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;

@Mapper
public interface WalletMapper {

    @Select("SELECT company_wallet_id, company_id, wallet_address, chain_id FROM company_wallets "
            + "WHERE company_id = #{companyId} AND is_primary = 1 AND disconnected_at IS NULL "
            + "ORDER BY connected_at DESC LIMIT 1")
    Optional<Wallet> findByCompanyId(Long companyId);

    @Select("SELECT company_wallet_id, company_id, wallet_address, chain_id FROM company_wallets WHERE wallet_address = #{walletAddress}")
    Optional<Wallet> findByWalletAddress(String walletAddress);

    @Insert("INSERT INTO company_wallets (company_id, wallet_address, chain_id) VALUES (#{companyId}, #{walletAddress}, #{chainId})")
    @Options(useGeneratedKeys = true, keyProperty = "companyWalletId", keyColumn = "company_wallet_id")
    void insert(Wallet wallet);

    @Update("UPDATE company_wallets SET wallet_address = #{walletAddress}, chain_id = #{chainId}, is_verified = 0, "
            + "verified_at = NULL, connected_at = CURRENT_TIMESTAMP, disconnected_at = NULL "
            + "WHERE company_wallet_id = #{companyWalletId}")
    void update(Wallet wallet);
}
