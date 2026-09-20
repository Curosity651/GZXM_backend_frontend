package com.gzxm.server.modules.achievement;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gzxm.server.GzxmApplication;
import com.gzxm.server.common.security.CurrentUser;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes=GzxmApplication.class)
@AutoConfigureMockMvc
class AchievementProgressIntegrationTest {
    private static MySQLContainer<?> container;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        String url=System.getenv("GZXM_TOPIC_TEST_MYSQL_URL");
        if(url==null || url.isBlank()) {
            container=new MySQLContainer<>("mysql:8.4").withDatabaseName("gzxm_topic_test_achievements");container.start();
            registry.add("spring.datasource.url",container::getJdbcUrl);registry.add("spring.datasource.username",container::getUsername);registry.add("spring.datasource.password",container::getPassword);
        } else {
            if(!url.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/gzxm_topic_test_[a-zA-Z0-9_]+(\\?.*)?")) throw new IllegalArgumentException("Use a local gzxm_topic_test_* database only");
            registry.add("spring.datasource.url",()->url);registry.add("spring.datasource.username",()->System.getenv().getOrDefault("GZXM_TOPIC_TEST_MYSQL_USER","root"));
            registry.add("spring.datasource.password",()->System.getenv().getOrDefault("GZXM_TOPIC_TEST_MYSQL_PASSWORD",""));
        }
        registry.add("app.bootstrap.enabled",()->false);registry.add("app.bootstrap.admin-password",()->UUID.randomUUID().toString());
        String secret=UUID.randomUUID().toString()+UUID.randomUUID();registry.add("app.security.jwt-secret",()->secret);registry.add("spring.data.redis.password",()->"");
    }
    @AfterAll static void stopContainer(){if(container!=null) container.stop();}
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;
    @Autowired com.gzxm.server.modules.achievement.application.AchievementProgressQuery progressQuery;
    @Autowired com.gzxm.server.modules.indicator.application.IndicatorProgressQuery indicatorQuery;
    @BeforeEach void fixtures() {
        cleanupHistory();
        for(String table:List.of("achievement_workflow_operation","achievement_material","achievement","unit_allocation_publication","unit_allocation_draft_item","unit_allocation_draft","unit_indicator_allocation",
                "topic_indicator_publication","topic_indicator_draft_target","topic_indicator_draft","topic_indicator","time_node","indicator_definition",
                "biz_topic_unit_membership","biz_topic","biz_project","sys_unit","audit_log")) jdbc.update("DELETE FROM "+table);
        jdbc.update("INSERT INTO biz_project(id,code,name) VALUES(1,'P','Synthetic project')");
        for(int i=1;i<=4;i++) jdbc.update("INSERT INTO sys_unit(id,code,name,internal_flag) VALUES(?,?,?,?)",i,"U"+i,"Synthetic unit "+i,i!=3);
        jdbc.update("INSERT INTO biz_topic(id,project_id,code,name,lead_unit_id,created_by,updated_by) VALUES(1,1,'T','Synthetic topic',1,101,101)");
        for(int i=1;i<=3;i++) jdbc.update("INSERT INTO biz_topic_unit_membership(id,topic_id,unit_id,membership_type,created_by,updated_by) VALUES(?,1,?,?,101,101)",i,i,i==1?"LEAD":"PARTICIPANT");
        jdbc.update("INSERT INTO time_node(id,project_id,code,name,deadline,sort_order) VALUES(1,1,'MID','Mid','2027-01-01',1),(2,1,'END','End','2028-01-01',2)");
        String[] types={"PAPER","PATENT","COPYRIGHT","STANDARD","TALENT"};
        for(int i=1;i<=5;i++) {
            jdbc.update("INSERT INTO indicator_definition(id,code,name,achievement_type,category,unit_name) VALUES(?,?,?,?,'BASE','count')",i,types[i-1],types[i-1],types[i-1]);
            jdbc.update("INSERT INTO topic_indicator(id,project_id,topic_id,node_id,indicator_definition_id,target_quantity,status,publish_version) VALUES(?,1,1,1,?,0,'PUBLISHED',1)",i,i);
            for(int unit=1;unit<=3;unit++) jdbc.update("INSERT INTO unit_indicator_allocation(project_id,topic_id,membership_id,unit_id,node_id,indicator_definition_id,topic_indicator_id,target_quantity,status,publish_version) VALUES(1,1,?,?,1,?,?,0,'PUBLISHED',1)",unit,unit,i,i);
        }
        jdbc.update("UPDATE topic_indicator SET target_quantity=1 WHERE indicator_definition_id=1");
        jdbc.update("UPDATE unit_indicator_allocation SET target_quantity=1 WHERE indicator_definition_id=1");
    }
    @AfterEach void cleanupHistory() {
        jdbc.update("DELETE FROM approval_record WHERE business_type='ACHIEVEMENT'");
        jdbc.update("DELETE FROM submission_snapshot WHERE business_type='ACHIEVEMENT'");
        jdbc.update("DELETE FROM sys_user WHERE username='synthetic-statistics-actor'");
    }
    private UsernamePasswordAuthenticationToken auth(String role,Long unit,Set<String> permissions) {
        var memberships=unit==null?List.<CurrentUser.TopicMembership>of():jdbc.query("SELECT * FROM biz_topic_unit_membership WHERE unit_id=? AND enabled=1",
                (rs,row)->new CurrentUser.TopicMembership(rs.getLong("id"),rs.getLong("topic_id"),rs.getLong("unit_id"),rs.getString("membership_type"),rs.getBoolean("enabled")),unit);
        var user=new CurrentUser(unit==null?101:100+unit,"synthetic",unit,role,permissions,memberships,0);
        return new UsernamePasswordAuthenticationToken(user,null,permissions.stream().map(SimpleGrantedAuthority::new).toList());
    }
    private ResultActions call(MockHttpServletRequestBuilder request,String role,Long unit) throws Exception {
        return mvc.perform(request.with(authentication(auth(role,unit,Set.of("achievement.submit")))).contentType("application/json"));
    }
    @Test void nodeSortOrderAccumulatesOnceAndUsesOnlySelectedPublishedTarget() throws Exception {
        jdbc.update("INSERT INTO time_node(id,project_id,code,name,deadline,sort_order) VALUES(9,1,'EARLY','Early','2026-01-01',0)");
        fact(1,2,9,1,"EFFECTIVE",true,"{}");fact(2,2,1,1,"EFFECTIVE",true,"{}");fact(3,2,2,1,"EFFECTIVE",true,"{}");
        jdbc.update("UPDATE topic_indicator SET target_quantity=1,publish_version=7 WHERE indicator_definition_id=1");
        jdbc.update("UPDATE unit_indicator_allocation SET target_quantity=3,publish_version=8 WHERE unit_id=2 AND indicator_definition_id=1");
        var first=statistics("RESEARCH_ASSISTANT",null,"nodeId=1&topicId=1");
        assertThat(first.path("baseTotals").path("PAPER").asLong()).isEqualTo(2);
        assertThat(row(first,"TOPIC",null,"1").path("completionRate").decimalValue()).isEqualByComparingTo("200.00");
        assertThat(row(first,"TOPIC",null,"1").path("targetVersion").asInt()).isEqualTo(7);
        assertThat(row(first,"UNIT","2","1").path("completionRate").decimalValue()).isEqualByComparingTo("66.67");
        jdbc.update("INSERT INTO topic_indicator(project_id,topic_id,node_id,indicator_definition_id,target_quantity,status,publish_version) VALUES(1,1,2,1,5,'PUBLISHED',9)");
        var later=statistics("RESEARCH_ASSISTANT",null,"nodeId=2&topicId=1");
        assertThat(later.path("baseTotals").path("PAPER").asLong()).isEqualTo(3);
        assertThat(row(later,"TOPIC",null,"1").path("targetQuantity").asLong()).isEqualTo(6);
        assertThat(row(later,"TOPIC",null,"1").path("completionRate").decimalValue()).isEqualByComparingTo("50.00");
    }
    @Test void stageHistoryCountsExistenceNotRetriesOrCurrentReturnedState() throws Exception {
        fact(1,2,1,1,"SUPPLEMENT_RETURNED",false,"{}");history(1,1,"PRE_REVIEW");history(1,2,"FORMAL");history(1,3,"FORMAL");history(1,4,"SUPPLEMENT");
        for(int version:List.of(1,2)) jdbc.update("INSERT INTO approval_record(business_type,business_id,stage,approval_level,decision,operator_id,submitted_version) VALUES('ACHIEVEMENT',1,'PRE_REVIEW','FINAL','APPROVED',9000,?)",version);
        jdbc.update("INSERT INTO achievement_workflow_operation(achievement_id,actor_id,request_key,operation_kind,request_json,response_json) VALUES(1,9000,'synthetic-register','ACTION',?, '{}')","{\"action\":\"REGISTER_EXTERNAL_SUBMISSION\"}");
        var result=statistics("INTERNAL_TOPIC_UNIT",2L,"nodeId=1&topicId=1").path("baseStages");
        for(String stage:List.of("initiated","submitted","preApproved","external","formal","supplement")) assertThat(result.path(stage).asLong()).isEqualTo(1);
        assertThat(result.path("effective").asLong()).isZero();
    }
    @Test void totalsRequireEffectiveAndCountFlagAndNeverGuessMissingHistory() throws Exception {
        fact(1,2,1,1,"EFFECTIVE",false,"{}");
        assertThatThrownBy(() -> fact(2,2,1,1,"DRAFT",true,"{}"))
                .hasMessageContaining("chk_achievement_indicator_state");
        fact(3,2,1,1,"EFFECTIVE",true,"{}");
        var result=statistics("INTERNAL_TOPIC_UNIT",2L,"nodeId=1");
        assertThat(result.path("baseTotals").path("PAPER").asLong()).isEqualTo(1);
        assertThat(result.path("baseStages").path("initiated").asLong()).isEqualTo(2);
        assertThat(result.path("baseStages").path("submitted").asLong()).isEqualTo(2);
        assertThat(result.path("baseStages").path("preApproved").asLong()).isZero();
    }
    @Test void progressUsesTheSameRoleVisibilityAsAchievementLists() throws Exception {
        fact(1,2,1,1,"DRAFT",false,"{}");fact(2,2,1,1,"PRE_INITIAL",false,"{}");
        assertThat(statistics("SYSTEM_ADMIN",null,"nodeId=1&topicId=1").path("baseStages").path("initiated").asLong()).isEqualTo(2);
        assertThat(statistics("RESEARCH_ASSISTANT",null,"nodeId=1&topicId=1").path("baseStages").path("initiated").asLong()).isEqualTo(1);
        assertThat(statistics("PROJECT_TECH_LEADER",null,"nodeId=1&topicId=1").path("baseStages").path("initiated").asLong()).isZero();
        assertThat(statistics("INTERNAL_TOPIC_UNIT",2L,"nodeId=1&topicId=1").path("baseStages").path("initiated").asLong()).isEqualTo(2);
    }
    @Test void overlappingSpecialsDoNotInflateBaseAndBooleanStringsDoNotMatch() throws Exception {
        special(6,"PAPER","isChineseCoreJournal");special(7,"PAPER","isPowerGridFirstAuthor");special(8,"PATENT","isPowerGridFirstApplicant");special(9,"COPYRIGHT","isPowerGridFirstCopyrightOwner");
        fact(1,2,1,1,"EFFECTIVE",true,"{\"isChineseCoreJournal\":true,\"isPowerGridFirstAuthor\":true}");
        fact(2,2,1,1,"EFFECTIVE",true,"{\"isChineseCoreJournal\":\"true\"}");
        fact(3,2,1,2,"EFFECTIVE",true,"{\"isPowerGridFirstApplicant\":true}");fact(4,2,1,3,"EFFECTIVE",true,"{\"isPowerGridFirstCopyrightOwner\":true}");
        var result=statistics("INTERNAL_TOPIC_UNIT",2L,"topicId=1&nodeId=1");
        assertThat(result.path("baseTotals").path("PAPER").asLong()).isEqualTo(2);
        assertThat(result.path("baseStages").path("effective").asLong()).isEqualTo(4);
        for(String definition:List.of("6","7","8","9")) assertThat(row(result,"UNIT","2",definition).path("stages").path("effective").asLong()).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"null","{}","{\"field\":\"isChineseCoreJournal\",\"equals\":false}","{\"field\":\"isChineseCoreJournal\",\"equals\":\"true\"}",
            "{\"field\":\"isPowerGridFirstApplicant\",\"equals\":true}","{\"field\":\"arbitraryExpression()\",\"equals\":true}","{\"field\":\"isChineseCoreJournal\",\"equals\":true,\"script\":\"x\"}"})
    void invalidOrMissingSpecialRulesFailExplicitly(String rule) throws Exception {
        special(6,"PAPER","isChineseCoreJournal");jdbc.update("UPDATE indicator_definition SET match_rule=? WHERE id=6",rule);
        call(get("/api/v1/achievement-progress?topicId=1&nodeId=1"),"SYSTEM_ADMIN",null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_SPECIAL_INDICATOR_RULE"));
    }
    @Test void zeroAndUnpublishedTargetsAreDistinctAndNeverProduceFakePercentages() throws Exception {
        fact(1,2,1,1,"EFFECTIVE",true,"{}");
        jdbc.update("UPDATE unit_indicator_allocation SET status='DRAFT' WHERE unit_id=2 AND indicator_definition_id=2");
        var result=statistics("INTERNAL_TOPIC_UNIT",2L,"topicId=1&nodeId=1");
        var zero=row(result,"UNIT","2","1");var absent=row(result,"UNIT","2","2");
        assertThat(zero.path("targetQuantity").asLong()).isZero();assertThat(zero.path("targetPublished").asBoolean()).isTrue();
        assertThat(zero.path("hasTarget").asBoolean()).isFalse();assertThat(zero.has("completionRate") && zero.path("completionRate").isNull()).isTrue();
        assertThat(absent.path("targetQuantity").isNull()).isTrue();assertThat(absent.path("targetPublished").asBoolean()).isFalse();
        assertThat(absent.path("completionRate").isNull()).isTrue();
    }
    @Test void ordinaryLeadAndGlobalUsersHaveDifferentScopes() throws Exception {
        fact(1,2,1,1,"EFFECTIVE",true,"{}");fact(2,3,1,1,"EFFECTIVE",true,"{}");
        var own=statistics("INTERNAL_TOPIC_UNIT",2L,"nodeId=1");assertThat(own.path("baseTotals").path("PAPER").asInt()).isEqualTo(1);
        for(var row:own.path("rows")) {assertThat(row.path("scope").asText()).isEqualTo("UNIT");assertThat(row.path("unitId").asText()).isEqualTo("2");}
        assertThat(statistics("INTERNAL_TOPIC_UNIT",1L,"nodeId=1").path("baseTotals").path("PAPER").asInt()).isEqualTo(2);
        assertThat(statistics("EXTERNAL_TOPIC_UNIT",3L,"nodeId=1").path("baseTotals").path("PAPER").asInt()).isEqualTo(1);
        call(get("/api/v1/achievement-progress?topicId=1&nodeId=1&unitId=3"),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isForbidden());
        var filtered=statistics("INTERNAL_TOPIC_UNIT",1L,"nodeId=1&unitId=3");
        assertThat(filtered.path("baseTotals").path("PAPER").asInt()).isEqualTo(1);
        for(var row:filtered.path("rows")) assertThat(row.path("scope").asText()).isEqualTo("UNIT");
        call(get("/api/v1/achievement-progress?topicId=1&nodeId=1"),"INTERNAL_TOPIC_UNIT",4L).andExpect(status().isForbidden());
    }
    @Test void disabledMembersAndUnitsAreHistoricalOnlyForManagers() throws Exception {
        fact(1,2,1,1,"EFFECTIVE",true,"{}");fact(2,3,1,1,"EFFECTIVE",true,"{}");
        jdbc.update("UPDATE biz_topic_unit_membership SET enabled=0 WHERE unit_id=2");jdbc.update("UPDATE sys_unit SET enabled=0 WHERE id=3");
        var admin=statistics("SYSTEM_ADMIN",null,"nodeId=1&topicId=1");
        assertThat(admin.path("baseTotals").path("PAPER").asInt()).isZero();
        for(String unit:List.of("2","3")) {
            var historical=row(admin,"UNIT",unit,"1");assertThat(historical.path("historical").asBoolean()).isTrue();assertThat(historical.path("stages").path("effective").asInt()).isEqualTo(1);
        }
        var lead=statistics("INTERNAL_TOPIC_UNIT",1L,"nodeId=1");
        for(var row:lead.path("rows")) assertThat(row.path("unitId").isNull() || "1".equals(row.path("unitId").asText())).isTrue();
        call(get("/api/v1/achievement-progress?topicId=1&nodeId=1"),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isForbidden());
        call(get("/api/v1/achievement-progress?nodeId=1"),"EXTERNAL_TOPIC_UNIT",3L).andExpect(status().isForbidden());
    }
    @Test void oldLeadAndStaleMembershipCannotRetainStatisticsScope() throws Exception {
        fact(1,3,1,1,"EFFECTIVE",true,"{}");var old=auth("INTERNAL_TOPIC_UNIT",1L,Set.of());
        jdbc.update("UPDATE biz_topic SET lead_unit_id=2");jdbc.update("UPDATE biz_topic_unit_membership SET membership_type=CASE WHEN unit_id=2 THEN 'LEAD' ELSE 'PARTICIPANT' END");
        mvc.perform(get("/api/v1/achievement-progress?nodeId=1").with(authentication(old))).andExpect(status().isOk()).andExpect(jsonPath("$.baseTotals.PAPER").value(0));
        jdbc.update("UPDATE biz_topic_unit_membership SET enabled=0 WHERE unit_id=1");
        mvc.perform(get("/api/v1/achievement-progress?nodeId=1").with(authentication(old))).andExpect(status().isOk()).andExpect(jsonPath("$.rows.length()").value(0));
        mvc.perform(get("/api/v1/achievement-progress?nodeId=1&topicId=1").with(authentication(old))).andExpect(status().isForbidden());
    }
    @Test void projectAndNodeIsolationInvalidIdsAndAnonymousRequests() throws Exception {
        jdbc.update("INSERT INTO biz_project(id,code,name,enabled) VALUES(2,'OTHER','Synthetic other',0)");
        jdbc.update("INSERT INTO time_node(id,project_id,code,name,deadline,sort_order) VALUES(3,2,'MID','Other','2027-01-01',1)");
        fact(1,2,3,1,"EFFECTIVE",true,"{}");
        assertThat(statistics("SYSTEM_ADMIN",null,"nodeId=1").path("baseTotals").path("PAPER").asLong()).isZero();
        call(get("/api/v1/achievement-progress?nodeId=3"),"SYSTEM_ADMIN",null).andExpect(status().isUnprocessableEntity());
        call(get("/api/v1/achievement-progress?nodeId=999"),"SYSTEM_ADMIN",null).andExpect(status().isNotFound());
        for(String query:List.of("","nodeId=0","nodeId=bad","nodeId=1&unitId=-1")) call(get("/api/v1/achievement-progress?"+query),"SYSTEM_ADMIN",null).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/achievement-progress?nodeId=1")).andExpect(status().isUnauthorized());
    }
    @Test void pausedTopicAndDisabledNodeStayReadableAndDraftTargetsDoNotOverridePublished() throws Exception {
        fact(1,2,1,1,"EFFECTIVE",true,"{}");jdbc.update("UPDATE biz_topic SET status='PAUSED',enabled=0");jdbc.update("UPDATE time_node SET enabled=0 WHERE id=1");
        jdbc.update("UPDATE topic_indicator SET target_quantity=2 WHERE indicator_definition_id=1");
        jdbc.update("INSERT INTO topic_indicator_draft(id,topic_id,node_id,draft_version,updated_by) VALUES(1,1,1,1,101)");
        jdbc.update("INSERT INTO topic_indicator_draft_target(draft_id,indicator_definition_id,target_quantity) VALUES(1,1,99)");
        var row=row(statistics("SYSTEM_ADMIN",null,"nodeId=1"),"TOPIC",null,"1");
        assertThat(row.path("targetQuantity").asInt()).isEqualTo(2);assertThat(row.path("completionRate").decimalValue()).isEqualByComparingTo("50.00");
    }
    @Test void publicContractsAreScopedImmutableAndWorkWithoutFileCapability() throws Exception {
        fact(1,2,1,1,"EFFECTIVE",true,"{}");fact(2,3,1,1,"EFFECTIVE",true,"{}");
        var holder=org.springframework.security.core.context.SecurityContextHolder.getContext();holder.setAuthentication(auth("INTERNAL_TOPIC_UNIT",2L,Set.of()));
        try {
            var context=indicatorQuery.targets(1,1,null);assertThat(context.units()).hasSize(1);assertThat(context.topicTargets()).isEmpty();
            assertThatThrownBy(()->context.units().clear()).isInstanceOf(UnsupportedOperationException.class);
            var effective=progressQuery.effectiveAchievements(1,1,null);assertThat(effective).hasSize(1);assertThat(effective.getFirst().unitId()).isEqualTo("2");
            assertThatThrownBy(()->progressQuery.effectiveAchievements(1,1,3L)).isInstanceOf(com.gzxm.server.common.exception.BusinessException.class);
        } finally {org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    }
    @Test void reportHistoryNeverContributesToAchievementStages() throws Exception {
        fact(1,2,1,1,"DRAFT",false,"{}");history(1,1,"PRE_REVIEW");
        jdbc.update("INSERT INTO submission_snapshot(business_type,business_id,stage,submitted_version,submitter_id,payload_json) VALUES('REPORT',1,'FORMAL',1,9000,'{}')");
        jdbc.update("INSERT INTO approval_record(business_type,business_id,stage,approval_level,decision,operator_id,submitted_version) VALUES('REPORT',1,'PRE_REVIEW','FINAL','APPROVED',9000,1)");
        try {
            var stages=statistics("INTERNAL_TOPIC_UNIT",2L,"nodeId=1").path("baseStages");
            assertThat(stages.path("preApproved").asInt()).isZero();assertThat(stages.path("formal").asInt()).isZero();
        } finally {jdbc.update("DELETE FROM submission_snapshot WHERE business_type='REPORT' AND submitter_id=9000");jdbc.update("DELETE FROM approval_record WHERE business_type='REPORT' AND operator_id=9000");}
    }
    @Test void exportsRealStatisticsResponseIncludingNullRates() throws Exception {
        special(6,"PAPER","isChineseCoreJournal");fact(1,2,1,1,"EFFECTIVE",true,"{\"isChineseCoreJournal\":true}");
        var result=statistics("SYSTEM_ADMIN",null,"nodeId=1&topicId=1");
        json.writerWithDefaultPrettyPrinter().writeValue(java.nio.file.Path.of("target/achievement-progress-contract-responses.json").toFile(),
                Map.of("getAchievementProgress",Map.of("status","200","body",result)));
    }
    private JsonNode statistics(String role,Long unit,String query) throws Exception {
        return json.readTree(call(get("/api/v1/achievement-progress?"+query),role,unit).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private JsonNode row(JsonNode response,String scope,String unit,String definition) {
        for(String group:List.of("rows","specialIndicators")) for(var row:response.path(group))
            if(scope.equals(row.path("scope").asText()) && Objects.equals(unit,row.path("unitId").isNull()?null:row.path("unitId").asText()) && definition.equals(row.path("indicatorDefinitionId").asText())) return row;
        throw new AssertionError("Missing row "+scope+"/"+unit+"/"+definition);
    }
    private void fact(long id,long unit,long node,int definition,String status,boolean counts,String detail) {
        String type=List.of("PAPER","PATENT","COPYRIGHT","STANDARD","TALENT").get(definition-1);
        jdbc.update("INSERT INTO achievement(id,project_id,topic_id,membership_id,unit_id,node_id,indicator_definition_id,achievement_type,title,responsible_person,status,counts_to_indicator,detail_json,created_by,updated_by) VALUES(?,1,1,?,?,?,?,?,'Synthetic achievement','Synthetic person',?,?,?,101,101)",id,unit,unit,node,definition,type,status,counts,detail);
    }
    private void special(int id,String type,String field) {
        jdbc.update("INSERT INTO indicator_definition(id,code,name,achievement_type,category,unit_name,match_rule) VALUES(?,?,?,?,'SPECIAL','count',?)",id,"SPECIAL"+id,"Synthetic special",type,"{\"field\":\""+field+"\",\"equals\":true}");
    }
    private void history(long id,int version,String stage) {
        jdbc.update("INSERT IGNORE INTO sys_user(id,username,password_hash,contact_name) VALUES(9000,'synthetic-statistics-actor','!unusable-test-hash','Synthetic person')");
        jdbc.update("INSERT INTO submission_snapshot(business_type,business_id,stage,submitted_version,submitter_id,payload_json) VALUES('ACHIEVEMENT',?,?,?,9000,'{}')",id,stage,version);
    }
    private ObjectNode body(int definition) {
        var body=json.createObjectNode();body.put("topicId","1");body.put("nodeId","1");body.put("indicatorDefinitionId",Integer.toString(definition));
        body.put("title","Synthetic achievement");body.put("responsiblePerson","Synthetic person");return body;
    }
    private JsonNode create(int definition,long unit) throws Exception {
        var response=call(post("/api/v1/achievements").content(body(definition).toString()),unit==3?"EXTERNAL_TOPIC_UNIT":"INTERNAL_TOPIC_UNIT",unit)
                .andExpect(status().isCreated()).andReturn().getResponse();return json.readTree(response.getContentAsString());
    }
}
