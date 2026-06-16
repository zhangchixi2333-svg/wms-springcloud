/**
 * 本文件是 WmsDiscoveryApplication Spring Boot 启动入口。
 */
package com.example.wms.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@EnableEurekaServer
@SpringBootApplication
public class WmsDiscoveryApplication {
    public static void main(String[] args) {
        SpringApplication.run(WmsDiscoveryApplication.class, args);
    }
}
