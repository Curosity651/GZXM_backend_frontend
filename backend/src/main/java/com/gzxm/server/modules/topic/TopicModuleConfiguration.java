package com.gzxm.server.modules.topic;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** Register only this module's persistence interfaces, never its public services. */
@Configuration(proxyBeanMethods = false)
@MapperScan("com.gzxm.server.modules.topic.repository")
public class TopicModuleConfiguration {}
