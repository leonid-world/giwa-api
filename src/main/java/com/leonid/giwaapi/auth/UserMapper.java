package com.leonid.giwaapi.auth;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface UserMapper {

    @Insert("INSERT INTO users (company_id, email, password_hash, user_name) VALUES (#{companyId}, #{email}, #{passwordHash}, #{userName})")
    @Options(useGeneratedKeys = true, keyProperty = "userId", keyColumn = "user_id")
    void insert(User user);

    @Select("SELECT user_id, company_id, email, password_hash, user_name, created_at FROM users WHERE email = #{email}")
    Optional<User> findByEmail(String email);
}
