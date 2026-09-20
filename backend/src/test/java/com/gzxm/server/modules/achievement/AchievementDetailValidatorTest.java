package com.gzxm.server.modules.achievement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzxm.server.modules.achievement.application.AchievementDetailValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AchievementDetailValidatorTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AchievementDetailValidator validator = new AchievementDetailValidator();

    @Test
    void keepsExistingPaperClassificationAndRejectsRemovedFields() throws Exception {
        var result = validator.validate("PAPER", json.readTree("""
                {"paperFormType":"期刊论文","paperType":"SCI","isChineseCoreJournal":true}
                """));
        assertThat(result.path("paperFormType").asText()).isEqualTo("期刊论文");
        assertThatThrownBy(() -> validator.validate("PAPER", json.readTree("{\"intendedJournal\":\"某期刊\"}")))
                .hasMessageContaining("不支持字段");
    }

    @Test
    void acceptsNewPatentAndCopyrightContracts() throws Exception {
        assertThat(validator.validate("PATENT", json.readTree("""
                {"firstInventor":"张三","applicantList":"广西电网","applicationNumber":"CN-1"}
                """)).path("firstInventor").asText()).isEqualTo("张三");
        var copyright = validator.validate("COPYRIGHT", json.readTree("""
                {"softwareFullName":"管理平台","isPowerGridFirstCopyrightOwner":true,
                 "copyrightStatus":"已予以发布","copyrightPublicationDate":"2026-09-21",
                 "hardwareEnvironment":"服务器","developmentOperatingSystem":"Linux",
                 "softwareDevelopmentEnvironment":"JDK 21","operatingPlatform":"Web",
                 "softwareSupportEnvironment":"MySQL","developmentLanguage":"Java",
                 "sourceCodeQuantity":"10000行","developmentPurpose":"项目管理",
                 "industryField":"电力","softwareMainFunctions":"成果管理","technicalFeatures":"安全可控"}
                """));
        assertThat(copyright.path("isPowerGridFirstCopyrightOwner").asBoolean()).isTrue();
    }
}
