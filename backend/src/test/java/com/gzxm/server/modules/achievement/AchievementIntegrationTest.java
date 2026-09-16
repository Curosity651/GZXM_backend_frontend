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
class AchievementIntegrationTest {
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
    @BeforeEach void fixtures() {
        for(String table:List.of("achievement_material","achievement","unit_allocation_publication","unit_allocation_draft_item","unit_allocation_draft","unit_indicator_allocation",
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
    private ObjectNode body(int definition) {
        var body=json.createObjectNode();body.put("topicId","1");body.put("nodeId","1");body.put("indicatorDefinitionId",Integer.toString(definition));
        body.put("title","Synthetic achievement");body.put("responsiblePerson","Synthetic person");return body;
    }
    private JsonNode create(int definition,long unit) throws Exception {
        var response=call(post("/api/v1/achievements").content(body(definition).toString()),unit==3?"EXTERNAL_TOPIC_UNIT":"INTERNAL_TOPIC_UNIT",unit)
                .andExpect(status().isCreated()).andReturn().getResponse();return json.readTree(response.getContentAsString());
    }
    @ParameterizedTest @ValueSource(ints={1,2,3,4,5})
    void createsAllFiveDraftTypesAgainstZeroAllocation(int definition) throws Exception {
        var result=create(definition,2);
        assertThat(result.path("achievementType").asText()).isEqualTo(List.of("PAPER","PATENT","COPYRIGHT","STANDARD","TALENT").get(definition-1));
        assertThat(result.path("unitId").asText()).isEqualTo("2");assertThat(result.path("status").asText()).isEqualTo("DRAFT");
        assertThat(result.path("countsToIndicator").asBoolean()).isFalse();assertThat(result.path("submittedVersion").asInt()).isZero();
        assertThat(result.path("materials").size()).isZero();assertThat(result.path("recordVersion").asInt()).isEqualTo(1);
    }
    @Test void validatesTypeSpecificDetailsEnumsDatesAndLengths() throws Exception {
        var request=body(1);request.set("detail",json.readTree("{\"issn\":\"1234-5678\",\"paperType\":\"SCI\",\"paperFormType\":\"期刊论文\",\"isChineseCoreJournal\":false,\"submissionDate\":\"2026-01-01\",\"acceptanceDate\":\"2026-02-01\"}"));
        call(post("/api/v1/achievements").content(request.toString()),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isCreated());
        for(String detail:List.of("[]","{\"studentName\":\"Wrong type\"}","{\"paperType\":\"INVALID\"}","{\"isChineseCoreJournal\":\"false\"}",
                "{\"submissionDate\":\"2026-02-30\"}","{\"submissionDate\":\"2026-03-01\",\"acceptanceDate\":\"2026-02-01\"}","{\"journalName\":9}")) {
            request.set("detail",json.readTree(detail));call(post("/api/v1/achievements").content(request.toString()),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isUnprocessableEntity());
        }
        request.set("detail",json.createObjectNode().put("journalName","x".repeat(501)));
        call(post("/api/v1/achievements").content(request.toString()),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isUnprocessableEntity());
    }
    @Test void mapsRemainingFormTypesAndDoesNotRequireFormalFieldsInDrafts() throws Exception {
        var values=Map.of(2,"{\"patentScope\":\"国内\",\"applicationDate\":\"2026-01-01\"}",
                3,"{\"version\":\"1.0\",\"isPowerGridFirstCompleter\":true}",4,"{\"standardLevel\":\"行业标准\"}",5,"{\"educationLevel\":\"硕士\",\"studentName\":\"Synthetic student\"}");
        for(var pair:values.entrySet()) {
            var request=body(pair.getKey());request.set("detail",json.readTree(pair.getValue()));
            call(post("/api/v1/achievements").content(request.toString()),"EXTERNAL_TOPIC_UNIT",3L).andExpect(status().isCreated());
        }
    }
    @Test void rejectsUnassignedSpecialWrongNodeDisabledAndOutsiderCreation() throws Exception {
        jdbc.update("UPDATE unit_indicator_allocation SET status='DRAFT' WHERE unit_id=2");
        call(post("/api/v1/achievements").content(body(1).toString()),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isConflict());
        jdbc.update("UPDATE unit_indicator_allocation SET status='PUBLISHED' WHERE unit_id=2");
        jdbc.update("UPDATE indicator_definition SET category='SPECIAL' WHERE id=1");
        call(post("/api/v1/achievements").content(body(1).toString()),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isUnprocessableEntity());
        var request=body(2).put("nodeId","2");call(post("/api/v1/achievements").content(request.toString()),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isConflict());
        jdbc.update("UPDATE sys_unit SET enabled=0 WHERE id=2");
        call(post("/api/v1/achievements").content(body(2).toString()),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isForbidden());
        call(post("/api/v1/achievements").content(body(2).toString()),"INTERNAL_TOPIC_UNIT",4L).andExpect(status().isForbidden());
    }
    @Test void ignoresForgedOwnershipAndEnforcesListDetailScopes() throws Exception {
        var request=body(1).put("unitId","3").put("status","EFFECTIVE").put("countsToIndicator",true);
        var response=call(post("/api/v1/achievements").content(request.toString()),"INTERNAL_TOPIC_UNIT",2L)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.unitId").value("2")).andExpect(jsonPath("$.status").value("DRAFT")).andReturn().getResponse();
        String id=json.readTree(response.getContentAsString()).path("id").asText();create(1,3);
        call(get("/api/v1/achievements"),"INTERNAL_TOPIC_UNIT",1L).andExpect(jsonPath("$.total").value(2));
        call(get("/api/v1/achievements"),"INTERNAL_TOPIC_UNIT",2L).andExpect(jsonPath("$.total").value(1));
        call(get("/api/v1/achievements?unitId=3"),"INTERNAL_TOPIC_UNIT",2L).andExpect(jsonPath("$.total").value(0));
        call(get("/api/v1/achievements/"+id),"EXTERNAL_TOPIC_UNIT",3L).andExpect(status().isForbidden());
        call(get("/api/v1/achievements/"+id),"INTERNAL_TOPIC_UNIT",1L).andExpect(status().isOk());
        call(get("/api/v1/achievements"),"SYSTEM_ADMIN",null).andExpect(jsonPath("$.total").value(2));
        call(get("/api/v1/achievements?pendingForMe=true"),"RESEARCH_ASSISTANT",null).andExpect(jsonPath("$.total").value(0));
        jdbc.update("UPDATE biz_topic_unit_membership SET enabled=0 WHERE unit_id=2");
        call(get("/api/v1/achievements/"+id),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isForbidden());
        call(get("/api/v1/achievements"),"INTERNAL_TOPIC_UNIT",2L).andExpect(jsonPath("$.total").value(0));
    }
    @Test void editingRequiresOwnershipImmutableBindingAndFreshVersion() throws Exception {
        String id=create(1,2).path("id").asText();var request=body(1).put("recordVersion",1).put("title","Updated title");
        call(put("/api/v1/achievements/"+id).content(request.toString()),"INTERNAL_TOPIC_UNIT",1L).andExpect(status().isForbidden());
        call(put("/api/v1/achievements/"+id).content(request.toString()),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isOk()).andExpect(jsonPath("$.recordVersion").value(2));
        call(put("/api/v1/achievements/"+id).content(request.toString()),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isConflict());
        request.put("recordVersion",2).put("indicatorDefinitionId","2");
        call(put("/api/v1/achievements/"+id).content(request.toString()),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isConflict());
        request.put("indicatorDefinitionId","1");request.remove("recordVersion");
        call(put("/api/v1/achievements/"+id).content(request.toString()),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isConflict());
    }
    @Test void missingFileCapabilityFailsClosedAndRollsBackDraft() throws Exception {
        var request=body(1);request.set("materialAttachments",json.readTree("[{\"fileId\":\"123\",\"materialType\":\"论文附件\"}]"));
        call(post("/api/v1/achievements").content(request.toString()),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("FILE_REFERENCE_CAPABILITY_UNAVAILABLE"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM achievement",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM achievement_material",Integer.class)).isZero();
        request.remove("materialAttachments");request.set("materialFileIds",json.readTree("[\"123\"]"));
        call(post("/api/v1/achievements").content(request.toString()),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action_code='achievement.create'",Integer.class)).isZero();
    }
    @Test void unavailableFilesRollBackEditsAndLargeVersionsRemainEditable() throws Exception {
        String id=create(1,2).path("id").asText();
        jdbc.update("UPDATE achievement SET record_version=1000 WHERE id=?",id);
        var request=body(1).put("recordVersion",1000).put("title","Must roll back");
        request.set("materialAttachments",json.readTree("[{\"fileId\":\"123\",\"materialType\":\"论文附件\"}]"));
        call(put("/api/v1/achievements/"+id).content(request.toString()),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isServiceUnavailable());
        call(get("/api/v1/achievements/"+id),"INTERNAL_TOPIC_UNIT",2L).andExpect(jsonPath("$.recordVersion").value(1000))
                .andExpect(jsonPath("$.title").value("Synthetic achievement"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action_code='achievement.update'",Integer.class)).isZero();
        request.remove("materialAttachments");
        call(put("/api/v1/achievements/"+id).content(request.toString()),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isOk())
                .andExpect(jsonPath("$.recordVersion").value(1001));
    }
    @Test void oldLeadAndDisabledMemberLoseScopeEvenWithStaleAuthentication() throws Exception {
        String id=create(1,3).path("id").asText();
        var oldLead=auth("INTERNAL_TOPIC_UNIT",1L,Set.of("achievement.submit"));
        var formerMember=auth("EXTERNAL_TOPIC_UNIT",3L,Set.of("achievement.submit"));
        jdbc.update("UPDATE biz_topic SET lead_unit_id=2 WHERE id=1");
        jdbc.update("UPDATE biz_topic_unit_membership SET membership_type=CASE WHEN unit_id=2 THEN 'LEAD' ELSE 'PARTICIPANT' END");
        mvc.perform(get("/api/v1/achievements/"+id).with(authentication(oldLead))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/achievements").with(authentication(oldLead))).andExpect(jsonPath("$.total").value(0));
        call(get("/api/v1/achievements/"+id),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isOk());
        jdbc.update("UPDATE biz_topic_unit_membership SET enabled=0 WHERE unit_id=3");
        mvc.perform(get("/api/v1/achievements/"+id).with(authentication(formerMember))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/achievements").with(authentication(formerMember))).andExpect(jsonPath("$.total").value(0));
    }
    @Test void pausedClosedOrSubmittedAchievementsRejectEdits() throws Exception {
        String id=create(1,2).path("id").asText();var request=body(1).put("recordVersion",1);
        for(String state:List.of("PAUSED","CLOSED")) {
            jdbc.update("UPDATE biz_topic SET status=?",state);
            call(put("/api/v1/achievements/"+id).content(request.toString()),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isConflict());
            call(get("/api/v1/achievements/"+id),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isOk());
        }
        jdbc.update("UPDATE biz_topic SET status='ACTIVE'");jdbc.update("UPDATE achievement SET status='SUBMITTED'");
        call(put("/api/v1/achievements/"+id).content(request.toString()),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isConflict());
    }
    @Test void malformedAndUnauthorizedRequestsAreRejected() throws Exception {
        for(String role:List.of("SYSTEM_ADMIN","PROJECT_TECH_LEADER","RESEARCH_ASSISTANT"))
            call(post("/api/v1/achievements").content(body(1).toString()),role,null).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/achievements").with(authentication(auth("INTERNAL_TOPIC_UNIT",2L,Set.of()))).contentType("application/json").content(body(1).toString())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/achievements")).andExpect(status().isUnauthorized());
        call(get("/api/v1/achievements?page=0"),"SYSTEM_ADMIN",null).andExpect(status().isUnprocessableEntity());
        call(get("/api/v1/achievements?topicId=bad"),"SYSTEM_ADMIN",null).andExpect(status().isUnprocessableEntity());
        var request=body(1).put("recordVersion",1.5);
        call(post("/api/v1/achievements").content(request.toString()),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isUnprocessableEntity());
    }
    @Test void concurrentEditorsCannotOverwriteEachOther() throws Exception {
        String id=create(1,2).path("id").asText();String request=body(1).put("recordVersion",1).toString();
        try(var executor=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<Integer> work=()->{start.await();return call(put("/api/v1/achievements/"+id).content(request),"INTERNAL_TOPIC_UNIT",2L).andReturn().getResponse().getStatus();};
            var first=executor.submit(work);var second=executor.submit(work);start.countDown();
            assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }
    }
    @Test void exportsFourRealDraftResponses() throws Exception {
        var samples=new LinkedHashMap<String,Object>();var created=create(1,2);String id=created.path("id").asText();
        samples.put("createAchievement",Map.of("status","201","body",created));
        for(var operation:Map.of("getAchievement",get("/api/v1/achievements/"+id),"listAchievements",get("/api/v1/achievements"),
                "updateAchievement",put("/api/v1/achievements/"+id).content(body(1).put("recordVersion",1).toString())).entrySet()) {
            var response=call(operation.getValue(),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isOk()).andReturn().getResponse();
            samples.put(operation.getKey(),Map.of("status","200","body",json.readTree(response.getContentAsString())));
        }
        json.writerWithDefaultPrettyPrinter().writeValue(java.nio.file.Path.of("target/achievement-contract-responses.json").toFile(),samples);
    }
}
