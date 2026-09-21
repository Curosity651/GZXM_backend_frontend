package com.gzxm.server;

import com.gzxm.server.config.AppProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan({
        "com.gzxm.server.common.audit",
        "com.gzxm.server.modules.systemlog.repository",
        "com.gzxm.server.modules.system.repository",
        "com.gzxm.server.modules.file.repository"
})
@EnableConfigurationProperties(AppProperties.class)
public class GzxmApplication {
    public static void main(String[] args) {
        SpringApplication.run(GzxmApplication.class, args);
    }
}
