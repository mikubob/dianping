package com.hmdp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * 黑马点评主应用程序类 - Spring Boot应用的启动类
 * 配置了MyBatis-Plus的Mapper扫描路径，启动整个点评系统
 */
@MapperScan(basePackages = "com.hmdp.mapper")
@SpringBootApplication
@EnableAspectJAutoProxy (exposeProxy = true)// 启动AOP功能,可以获取 AOP代理对象
public class HmDianPingApplication {

    public static void main(String[] args) {
        SpringApplication.run(HmDianPingApplication.class, args);
    }

}
