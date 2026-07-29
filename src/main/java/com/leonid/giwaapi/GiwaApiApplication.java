package com.leonid.giwaapi;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.leonid.giwaapi")
public class GiwaApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(GiwaApiApplication.class, args);
    }

}
