package com.example.oaticket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 项目启动类
 *
 * @SpringBootApplication 这一个注解干了三件事：
 * 1. 开启自动配置（帮你把 Tomcat、Jackson 这些配好，不用自己写）
 * 2. 开启组件扫描（扫描本包及子包下所有 @RestController / @Service 等）
 * 3. 声明这是一个配置类
 *
 * 所以启动类必须放在最外层的包（com.example.oaticket），
 * 其他类都放在它的子包里，不然扫描不到。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class OaTicketAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(OaTicketAgentApplication.class, args);
    }
}
