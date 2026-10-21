package com.procuremind.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableAsync
@EnableTransactionManagement
@MapperScan("com.procuremind.platform")
@SpringBootApplication
public class ProcureMindApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProcureMindApplication.class, args);
    }
}
