package com.example.serviceexchange;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class ServiceExchangeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceExchangeApplication.class, args);
    }

}
