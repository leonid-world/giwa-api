package com.leonid.giwaapi.company;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface CompanyMapper {

    @Insert("INSERT INTO companies (company_name, business_number) VALUES (#{companyName}, #{businessNumber})")
    @Options(useGeneratedKeys = true, keyProperty = "companyId", keyColumn = "company_id")
    void insert(Company company);

    @Select("SELECT company_id, company_name, business_number FROM companies "
            + "WHERE business_number = #{businessNumber} AND company_status = 'ACTIVE'")
    Optional<Company> findByBusinessNumber(String businessNumber);
}
