package com.guochang.chanpicturebackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.guochang.chanpicturebackend.mapper")
@EnableScheduling
//@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
public class ChanPictureBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChanPictureBackendApplication.class, args);
    }

}
