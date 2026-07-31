package com.leonid.giwaapi;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan(
        basePackages = "com.leonid.giwaapi",
        annotationClass = Mapper.class
)
public class GiwaApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(GiwaApiApplication.class, args);
    }

}
