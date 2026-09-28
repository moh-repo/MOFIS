package com.dilaw.mohe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
@EnableAsync
@EnableScheduling
@EnableCaching
@EnableAspectJAutoProxy
public class MoheApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(MoheApplication.class, args);
    }
    
    @GetMapping("/")
    public String home() {
        return "MOHE Application is running!";
    }
    
}