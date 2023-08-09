package com.dev;

import com.dev.util.StarterAOP;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AOP implements ApplicationRunner {
    private StarterAOP starter;

    @Autowired
    public AOP(StarterAOP starter){
        this.starter = starter;
    }

    public static void main(String[] args){
        SpringApplication.run(AOP.class,args);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        starter.start();
    }
}
