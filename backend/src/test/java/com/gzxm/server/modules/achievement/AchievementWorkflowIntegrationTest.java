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
class AchievementWorkflowIntegrationTest {
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
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.gzxm.server.modules.achievement.application.AchievementFileGateway files;
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
    }
    @AfterEach void cleanupHistory() {
        jdbc.update("DELETE FROM approval_record WHERE business_type='ACHIEVEMENT'");
        jdbc.update("DELETE FROM submission_snapshot WHERE business_type='ACHIEVEMENT'");
        jdbc.update("DELETE FROM achievement_material");
        jdbc.update("DELETE FROM file_object WHERE bucket_name='synthetic-workflow'");
        jdbc.update("DELETE FROM sys_user WHERE username LIKE 'synthetic-workflow-%'");
    }
    private UsernamePasswordAuthenticationToken auth(String role,Long unit,Set<String> permissions) {
        var memberships=unit==null?List.<CurrentUser.TopicMembership>of():jdbc.query("SELECT * FROM biz_topic_unit_membership WHERE unit_id=? AND enabled=1",
                (rs,row)->new CurrentUser.TopicMembership(rs.getLong("id"),rs.getLong("topic_id"),rs.getLong("unit_id"),rs.getString("membership_type"),rs.getBoolean("enabled")),unit);
        for(long actor:List.of(101L,102L,103L,104L,201L,202L,203L))
            jdbc.update("INSERT IGNORE INTO sys_user(id,username,password_hash,contact_name) VALUES(?,?,?,?)",actor,"synthetic-workflow-"+actor,"!unusable-test-hash","Synthetic person");
        var user=new CurrentUser(unit==null?("RESEARCH_ASSISTANT".equals(role)?201:"PROJECT_TECH_LEADER".equals(role)?202:203):100+unit,"synthetic",unit,role,permissions,memberships,0);
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
    void allTypesRequireTwoPreReviewsAndNeverCountPreApproval(int definition) throws Exception {
        String id=create(definition,2).path("id").asText();
        var submitted=action(id,"SUBMIT_PRE_REVIEW");
        assertThat(submitted.path("status").asText()).isEqualTo("PRE_INITIAL");
        assertThat(submitted.path("submittedVersion").asInt()).isEqualTo(1);
        reviewCall(id,"PROJECT_TECH_LEADER",reviewBody(id,"APPROVE"),key()).andExpect(status().isConflict());
        call(put("/api/v1/achievements/"+id).content(body(definition).put("recordVersion",2).toString()),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isConflict());
        review(id,"RESEARCH_ASSISTANT","APPROVE");review(id,"PROJECT_TECH_LEADER","APPROVE");
        assertThat(current(id).path("status").asText()).isEqualTo(definition<=2?"PRE_APPROVED":"FORMAL_DRAFT");
        assertThat(current(id).path("countsToIndicator").asBoolean()).isFalse();
        assertThat(current(id).path("approvals").size()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission_snapshot WHERE business_type='ACHIEVEMENT'",Integer.class)).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(ints={1,2,3,4,5})
    void fullWorkflowWithTestOnlyFileBoundaryCountsExactlyOnce(int definition) throws Exception {
        String id=create(definition,2).path("id").asText();preApprove(id);if(definition<=2) register(id);
        var types=Map.of(1,List.of("论文定稿","录用通知或接收函","项目标注页"),2,List.of("专利授权证书","专利授权文件","项目关联说明"),
                3,List.of("软件著作权证书","软件鉴别材料","著作权人证明"),4,List.of("标准送审稿","送审或立项证明"),5,List.of("研究生学位论文证明材料"));
        var detail=Map.of(1,"{\"paperStatus\":\"已录用\",\"acceptanceDate\":\"2026-02-01\"}",2,"{\"patentStatus\":\"已授权\",\"grantDate\":\"2026-02-01\"}",
                3,"{\"certificateDate\":\"2026-02-01\"}",4,"{\"draftCommitDate\":\"2026-02-01\"}",5,"{\"actualGraduationDate\":\"2026-02-01\"}");
        setMaterials(id,definition,detail.get(definition),types.get(definition));action(id,"SUBMIT_FORMAL");
        review(id,"RESEARCH_ASSISTANT","APPROVE");review(id,"PROJECT_TECH_LEADER","APPROVE");
        if(definition<=2) {
            assertThat(current(id).path("countsToIndicator").asBoolean()).isTrue();
            assertThat(current(id).path("status").asText()).isEqualTo(definition==1?"WAIT_PUBLICATION":"WAIT_GRANT");
            String supplement=definition==1?"{\"paperStatus\":\"已正式刊出\",\"publicationDate\":\"2026-03-01\",\"paperType\":\"SCI\",\"isChineseCoreJournal\":true}"
                    :"{\"patentStatus\":\"已授权\",\"grantDate\":\"2026-03-01\"}";
            var supplementTypes=definition==1?List.of("正式刊出论文全文","期刊封面、目录及见刊页","项目标注页","检索证明","中文核心期刊认定证明")
                    :List.of("专利授权证书","授权公告文本","法律状态证明","专利权属证明");
            setMaterials(id,definition,supplement,supplementTypes);action(id,"SUBMIT_SUPPLEMENT");review(id,"RESEARCH_ASSISTANT","APPROVE");
            var request=reviewBody(id,"APPROVE");String retryKey=key();
            var first=reviewCall(id,"PROJECT_TECH_LEADER",request,retryKey).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            assertThat(reviewCall(id,"PROJECT_TECH_LEADER",request,retryKey).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).isEqualTo(first);
        }
        var result=current(id);assertThat(result.path("status").asText()).isEqualTo("EFFECTIVE");assertThat(result.path("countsToIndicator").asBoolean()).isTrue();
        assertThat(result.path("submittedVersion").asInt()).isEqualTo(definition<=2?3:2);
        assertThat(result.path("approvals").size()).isEqualTo(definition<=2?6:4);
        call(put("/api/v1/achievements/"+id).content(body(definition).put("recordVersion",result.path("recordVersion").asInt()).toString()),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isConflict());
        call(post("/api/v1/achievements/"+id+"/actions").header("Idempotency-Key",key()).content(actionBody(id,"SUBMIT_SUPPLEMENT").toString()),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isConflict());
    }
    @Test void returnsResubmitFromInitialWithImmutableEarlierSnapshot() throws Exception {
        String id=create(1,2).path("id").asText();action(id,"SUBMIT_PRE_REVIEW");review(id,"RESEARCH_ASSISTANT","APPROVE");
        var stale=reviewBody(id,"APPROVE");review(id,"PROJECT_TECH_LEADER","RETURN");
        String payload=jdbc.queryForObject("SELECT payload_json FROM submission_snapshot WHERE business_type='ACHIEVEMENT' AND business_id=?",String.class,id);
        call(put("/api/v1/achievements/"+id).content(body(1).put("title","Revised").put("recordVersion",current(id).path("recordVersion").asInt()).toString()),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isOk());
        action(id,"SUBMIT_PRE_REVIEW");assertThat(current(id).path("submittedVersion").asInt()).isEqualTo(2);
        assertThat(current(id).path("status").asText()).isEqualTo("PRE_INITIAL");
        reviewCall(id,"PROJECT_TECH_LEADER",stale,key()).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT payload_json FROM submission_snapshot WHERE business_type='ACHIEVEMENT' AND business_id=? AND submitted_version=1",String.class,id)).isEqualTo(payload);
        assertThat(json.readTree(payload).path("title").asText()).isEqualTo("Synthetic achievement");
    }
    @Test void replayReturnsOriginalResultAndRejectsChangedRequest() throws Exception {
        String id=create(1,2).path("id").asText();var request=actionBody(id,"SUBMIT_PRE_REVIEW");String retryKey=key();
        var route="/api/v1/achievements/"+id+"/actions";
        var first=call(post(route).header("Idempotency-Key",retryKey).content(request.toString()),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        review(id,"RESEARCH_ASSISTANT","APPROVE");
        assertThat(call(post(route).header("Idempotency-Key",retryKey).content(request.toString()),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).isEqualTo(first);
        request.put("recordVersion",3);call(post(route).header("Idempotency-Key",retryKey).content(request.toString()),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action_code='achievement.action'",Integer.class)).isEqualTo(1);
        assertThat(current(id).path("status").asText()).isEqualTo("PRE_FINAL");
    }
    @Test void concurrentSubmissionAndApprovalRetriesHaveSingleEffects() throws Exception {
        String id=create(1,2).path("id").asText();String payload=actionBody(id,"SUBMIT_PRE_REVIEW").toString(),retryKey=key();
        runConcurrently(()->call(post("/api/v1/achievements/"+id+"/actions").header("Idempotency-Key",retryKey).content(payload),"INTERNAL_TOPIC_UNIT",2L).andReturn().getResponse().getStatus(),200);
        var request=reviewBody(id,"APPROVE");String reviewKey=key();
        runConcurrently(()->reviewCall(id,"RESEARCH_ASSISTANT",request,reviewKey).andReturn().getResponse().getStatus(),201);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission_snapshot WHERE business_type='ACHIEVEMENT'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_record WHERE business_type='ACHIEVEMENT'",Integer.class)).isEqualTo(1);
        assertThat(current(id).path("recordVersion").asInt()).isEqualTo(3);
    }
    private void runConcurrently(Callable<Integer> work,int expected) throws Exception {
        try(var executor=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);Callable<Integer> waiting=()->{start.await();return work.call();};
            var first=executor.submit(waiting);var second=executor.submit(waiting);start.countDown();
            assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactly(expected,expected);
        }
    }
    @Test void permissionsVersionsOpinionAndPendingQueuesAreEnforced() throws Exception {
        String id=create(1,2).path("id").asText();var request=actionBody(id,"SUBMIT_PRE_REVIEW");
        call(post("/api/v1/achievements/"+id+"/actions").header("Idempotency-Key",key()).content(request.toString()),"INTERNAL_TOPIC_UNIT",1L).andExpect(status().isForbidden());
        call(post("/api/v1/achievements/"+id+"/actions").content(request.toString()),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isUnprocessableEntity());
        action(id,"SUBMIT_PRE_REVIEW");
        var review=reviewBody(id,"RETURN");review.put("opinion"," ");reviewCall(id,"RESEARCH_ASSISTANT",review,key()).andExpect(status().isUnprocessableEntity());
        review=reviewBody(id,"APPROVE").put("submittedVersion",99);reviewCall(id,"RESEARCH_ASSISTANT",review,key()).andExpect(status().isConflict());
        reviewCall(id,"SYSTEM_ADMIN",reviewBody(id,"APPROVE"),key()).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/achievements?pendingForMe=true").with(authentication(auth("RESEARCH_ASSISTANT",null,Set.of("achievement.initial.approve")))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/v1/achievements?pendingForMe=true").with(authentication(auth("PROJECT_TECH_LEADER",null,Set.of("achievement.final.approve")))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(post("/api/v1/achievements/"+id+"/reviews").with(authentication(auth("RESEARCH_ASSISTANT",null,Set.of())))
                .contentType("application/json").header("Idempotency-Key",key()).content(reviewBody(id,"APPROVE").toString())).andExpect(status().isForbidden());
        jdbc.update("UPDATE biz_topic SET status='PAUSED'");reviewCall(id,"RESEARCH_ASSISTANT",reviewBody(id,"APPROVE"),key()).andExpect(status().isConflict());
    }
    @Test void snapshotFailureRollsBackEverythingAndRetryCanSucceed() throws Exception {
        String id=create(1,2).path("id").asText();var request=actionBody(id,"SUBMIT_PRE_REVIEW");String retryKey=key();
        jdbc.execute("CREATE TRIGGER fail_achievement_snapshot BEFORE INSERT ON submission_snapshot FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic snapshot failure'");
        try {
            call(post("/api/v1/achievements/"+id+"/actions").header("Idempotency-Key",retryKey).content(request.toString()),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isInternalServerError());
        } finally {jdbc.execute("DROP TRIGGER fail_achievement_snapshot");}
        assertThat(current(id).path("status").asText()).isEqualTo("DRAFT");assertThat(current(id).path("recordVersion").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM achievement_workflow_operation",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action_code='achievement.action'",Integer.class)).isZero();
        call(post("/api/v1/achievements/"+id+"/actions").header("Idempotency-Key",retryKey).content(request.toString()),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isOk());
    }
    @Test void reportHistoryIsExcludedAndSnapshotAccessUsesCurrentScope() throws Exception {
        String id=create(1,2).path("id").asText();action(id,"SUBMIT_PRE_REVIEW");
        jdbc.update("INSERT INTO submission_snapshot(business_type,business_id,stage,submitted_version,submitter_id,payload_json) VALUES('REPORT',?,'REPORT',1,102,'{}')",id);
        jdbc.update("INSERT INTO approval_record(business_type,business_id,stage,approval_level,decision,operator_id,submitted_version) VALUES('REPORT',?,'REPORT','INITIAL','APPROVED',201,1)",id);
        try {
            call(get("/api/v1/achievements/"+id+"/snapshots"),"INTERNAL_TOPIC_UNIT",1L).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
            call(get("/api/v1/achievements/"+id+"/snapshots"),"EXTERNAL_TOPIC_UNIT",3L).andExpect(status().isForbidden());
            assertThat(current(id).path("approvals").size()).isZero();
        } finally {jdbc.update("DELETE FROM submission_snapshot WHERE business_type='REPORT'");jdbc.update("DELETE FROM approval_record WHERE business_type='REPORT'");}
    }
    @Test void formalRequiredDatesAndConditionalMaterialsCannotBeSkipped() throws Exception {
        String id=create(1,2).path("id").asText();preApprove(id);register(id);
        setMaterials(id,1,"{}",List.of("论文定稿","录用通知或接收函","项目标注页"));
        call(post("/api/v1/achievements/"+id+"/actions").header("Idempotency-Key",key()).content(actionBody(id,"SUBMIT_FORMAL").toString()),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isUnprocessableEntity());
        setMaterials(id,1,"{\"paperStatus\":\"已录用\",\"acceptanceDate\":\"2026-02-01\"}",List.of("论文定稿"));
        call(post("/api/v1/achievements/"+id+"/actions").header("Idempotency-Key",key()).content(actionBody(id,"SUBMIT_FORMAL").toString()),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isUnprocessableEntity());
        assertThat(current(id).path("submittedVersion").asInt()).isEqualTo(1);
    }
    @Test void exportsThreeRealWorkflowResponses() throws Exception {
        String id=create(1,2).path("id").asText();var samples=new LinkedHashMap<String,Object>();
        samples.put("executeAchievementAction",Map.of("status","200","body",action(id,"SUBMIT_PRE_REVIEW")));
        samples.put("reviewAchievement",Map.of("status","201","body",review(id,"RESEARCH_ASSISTANT","APPROVE")));
        var snapshots=call(get("/api/v1/achievements/"+id+"/snapshots"),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isOk()).andReturn().getResponse();
        samples.put("listAchievementSnapshots",Map.of("status","200","body",json.readTree(snapshots.getContentAsString())));
        json.writerWithDefaultPrettyPrinter().writeValue(java.nio.file.Path.of("target/achievement-workflow-contract-responses.json").toFile(),samples);
    }
    @Test void competingReviewDecisionsCannotBothCommit() throws Exception {
        String id=create(1,2).path("id").asText();action(id,"SUBMIT_PRE_REVIEW");
        var approve=reviewBody(id,"APPROVE");var reject=reviewBody(id,"RETURN");
        try(var executor=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            var first=executor.submit(()->{start.await();return reviewCall(id,"RESEARCH_ASSISTANT",approve,key()).andReturn().getResponse().getStatus();});
            var second=executor.submit(()->{start.await();return reviewCall(id,"RESEARCH_ASSISTANT",reject,key()).andReturn().getResponse().getStatus();});
            start.countDown();assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_record WHERE business_type='ACHIEVEMENT'",Integer.class)).isEqualTo(1);
    }
    @Test void formalReturnRestartsInitialReviewAndKeepsPriorVersion() throws Exception {
        String id=create(5,2).path("id").asText();preApprove(id);
        setMaterials(id,5,"{\"actualGraduationDate\":\"2026-02-01\"}",List.of("研究生学位论文证明材料"));
        action(id,"SUBMIT_FORMAL");review(id,"RESEARCH_ASSISTANT","APPROVE");review(id,"PROJECT_TECH_LEADER","RETURN");
        assertThat(current(id).path("status").asText()).isEqualTo("FORMAL_RETURNED");
        var resubmit=action(id,"SUBMIT_FORMAL");assertThat(resubmit.path("status").asText()).isEqualTo("FORMAL_INITIAL");
        assertThat(resubmit.path("submittedVersion").asInt()).isEqualTo(3);assertThat(resubmit.path("countsToIndicator").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission_snapshot WHERE business_type='ACHIEVEMENT'",Integer.class)).isEqualTo(3);
    }
    @Test void supplementRequiresConditionalProofAndKeepsFormalCompletion() throws Exception {
        String id=create(1,2).path("id").asText();preApprove(id);register(id);
        setMaterials(id,1,"{\"paperStatus\":\"已录用\",\"acceptanceDate\":\"2026-02-01\"}",List.of("论文定稿","录用通知或接收函","项目标注页"));
        action(id,"SUBMIT_FORMAL");review(id,"RESEARCH_ASSISTANT","APPROVE");review(id,"PROJECT_TECH_LEADER","APPROVE");
        String detail="{\"paperStatus\":\"已正式刊出\",\"publicationDate\":\"2026-03-01\",\"paperType\":\"SCI\",\"isChineseCoreJournal\":true}";
        setMaterials(id,1,detail,List.of("正式刊出论文全文","期刊封面、目录及见刊页","项目标注页"));
        call(post("/api/v1/achievements/"+id+"/actions").header("Idempotency-Key",key()).content(actionBody(id,"SUBMIT_SUPPLEMENT").toString()),"INTERNAL_TOPIC_UNIT",2L)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("ACHIEVEMENT_MATERIALS_REQUIRED"));
        setMaterials(id,1,detail,List.of("正式刊出论文全文","期刊封面、目录及见刊页","项目标注页","检索证明","中文核心期刊认定证明"));
        action(id,"SUBMIT_SUPPLEMENT");review(id,"RESEARCH_ASSISTANT","RETURN");
        assertThat(current(id).path("status").asText()).isEqualTo("SUPPLEMENT_RETURNED");assertThat(current(id).path("countsToIndicator").asBoolean()).isTrue();
        assertThat(action(id,"SUBMIT_SUPPLEMENT").path("status").asText()).isEqualTo("SUPPLEMENT_INITIAL");
    }
    @Test void failedOperationPersistenceRollsBackApprovalAndStatus() throws Exception {
        String id=create(1,2).path("id").asText();action(id,"SUBMIT_PRE_REVIEW");var request=reviewBody(id,"APPROVE");String retryKey=key();
        jdbc.execute("CREATE TRIGGER fail_achievement_operation BEFORE INSERT ON achievement_workflow_operation FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic operation failure'");
        try {reviewCall(id,"RESEARCH_ASSISTANT",request,retryKey).andExpect(status().isInternalServerError());}
        finally {jdbc.execute("DROP TRIGGER fail_achievement_operation");}
        assertThat(current(id).path("status").asText()).isEqualTo("PRE_INITIAL");assertThat(current(id).path("recordVersion").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_record WHERE business_type='ACHIEVEMENT'",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action_code='achievement.review'",Integer.class)).isZero();
        reviewCall(id,"RESEARCH_ASSISTANT",request,retryKey).andExpect(status().isCreated());
    }
    private String key(){return "workflow-"+UUID.randomUUID();}
    private JsonNode current(String id) throws Exception {
        return json.readTree(call(get("/api/v1/achievements/"+id),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private ObjectNode actionBody(String id,String action) throws Exception {
        return json.createObjectNode().put("action",action).put("recordVersion",current(id).path("recordVersion").asInt());
    }
    private JsonNode action(String id,String action) throws Exception {
        return json.readTree(call(post("/api/v1/achievements/"+id+"/actions").header("Idempotency-Key",key()).content(actionBody(id,action).toString()),"INTERNAL_TOPIC_UNIT",2L)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private ResultActions reviewCall(String id,String role,ObjectNode request,String requestKey) throws Exception {
        return mvc.perform(post("/api/v1/achievements/"+id+"/reviews").with(authentication(auth(role,null,Set.of("achievement.initial.approve","achievement.final.approve"))))
                .header("Idempotency-Key",requestKey).contentType("application/json").content(request.toString()));
    }
    private ObjectNode reviewBody(String id,String decision) throws Exception {
        var row=current(id);return json.createObjectNode().put("decision",decision).put("recordVersion",row.path("recordVersion").asInt())
                .put("submittedVersion",row.path("submittedVersion").asInt()).put("opinion","Synthetic review");
    }
    private JsonNode review(String id,String role,String decision) throws Exception {
        return json.readTree(reviewCall(id,role,reviewBody(id,decision),key()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    private void preApprove(String id) throws Exception {
        action(id,"SUBMIT_PRE_REVIEW");review(id,"RESEARCH_ASSISTANT","APPROVE");review(id,"PROJECT_TECH_LEADER","APPROVE");
    }
    private void register(String id) throws Exception {
        var request=actionBody(id,"REGISTER_EXTERNAL_SUBMISSION").put("externalSubmissionDate","2026-01-01").put("externalSubmissionNumber","SYNTHETIC-001");
        call(post("/api/v1/achievements/"+id+"/actions").header("Idempotency-Key",key()).content(request.toString()),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isOk());
        action(id,"START_FORMAL");
    }
    private void setMaterials(String id,int definition,String detail,List<String> types) throws Exception {
        org.mockito.Mockito.when(files.requireOwnedReady(org.mockito.ArgumentMatchers.anyLong())).thenAnswer(invocation->file(invocation.getArgument(0)));
        org.mockito.Mockito.when(files.readMetadata(org.mockito.ArgumentMatchers.anyLong())).thenAnswer(invocation->file(invocation.getArgument(0)));
        var request=body(definition).put("recordVersion",current(id).path("recordVersion").asInt());request.set("detail",json.readTree(detail));
        var attachments=request.putArray("materialAttachments");
        for(String type:types) {
            long fileId=jdbc.queryForObject("SELECT COALESCE(MAX(id),1000)+1 FROM file_object",Long.class);
            jdbc.update("INSERT INTO file_object(id,bucket_name,object_key,original_name,content_type,size_bytes,status,uploader_id) VALUES(?,'synthetic-workflow',?,'synthetic.pdf','application/pdf',1,'READY',102)",fileId,"achievement/"+fileId);
            attachments.addObject().put("fileId",Long.toString(fileId)).put("materialType",type);
        }
        call(put("/api/v1/achievements/"+id).content(request.toString()),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isOk());
    }
    private com.gzxm.server.modules.file.api.FileDtos.FileView file(long id) {
        return new com.gzxm.server.modules.file.api.FileDtos.FileView(Long.toString(id),"synthetic.pdf",1,"application/pdf",null,"READY","102",java.time.LocalDateTime.of(2026,1,1,0,0));
    }
}
