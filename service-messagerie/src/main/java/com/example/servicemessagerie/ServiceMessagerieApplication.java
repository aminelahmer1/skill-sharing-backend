package com.example.servicemessagerie;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class ServiceMessagerieApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceMessagerieApplication.class, args);
    }

}
