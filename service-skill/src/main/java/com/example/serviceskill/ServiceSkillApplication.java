package com.example.serviceskill;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableFeignClients
@EnableJpaAuditing
public class ServiceSkillApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceSkillApplication.class, args);
    }

}
