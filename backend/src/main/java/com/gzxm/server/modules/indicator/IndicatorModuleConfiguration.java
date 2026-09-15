package com.gzxm.server.modules.indicator;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan("com.gzxm.server.modules.indicator.repository")
public class IndicatorModuleConfiguration {}
