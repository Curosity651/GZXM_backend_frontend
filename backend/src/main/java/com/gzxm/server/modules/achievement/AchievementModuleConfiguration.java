package com.gzxm.server.modules.achievement;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
@Configuration(proxyBeanMethods=false)
@MapperScan("com.gzxm.server.modules.achievement.repository")
public class AchievementModuleConfiguration {}
