package com.gzxm.server.modules.indicator;

import com.fasterxml.jackson.databind.*;
import com.gzxm.server.GzxmApplication;
import com.gzxm.server.common.security.CurrentUser;
import org.junit.jupiter.api.*;
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
class UnitAllocationIntegrationTest {
    private static MySQLContainer<?> container;
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url=System.getenv("GZXM_TOPIC_TEST_MYSQL_URL");
        if(url==null || url.isBlank()) {
            container=new MySQLContainer<>("mysql:8.4").withDatabaseName("gzxm_topic_test_allocations")
                    .withCommand("--log-bin-trust-function-creators=1");
            container.start();
            registry.add("spring.datasource.url",container::getJdbcUrl);
            registry.add("spring.datasource.username",container::getUsername);
            registry.add("spring.datasource.password",container::getPassword);
        } else {
            if(!url.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/gzxm_topic_test_[a-zA-Z0-9_]+(\\?.*)?"))
                throw new IllegalArgumentException("Use a local gzxm_topic_test_* database only");
            registry.add("spring.datasource.url",()->url);
            registry.add("spring.datasource.username",()->System.getenv().getOrDefault("GZXM_TOPIC_TEST_MYSQL_USER","root"));
            registry.add("spring.datasource.password",()->System.getenv().getOrDefault("GZXM_TOPIC_TEST_MYSQL_PASSWORD",""));
        }
        registry.add("app.bootstrap.enabled",()->false);
        registry.add("app.bootstrap.admin-password",()->UUID.randomUUID().toString());
        String secret=UUID.randomUUID().toString()+UUID.randomUUID();
        registry.add("app.security.jwt-secret",()->secret);
        registry.add("spring.data.redis.password",()->"");
    }
    @AfterAll static void stopContainer() { if(container!=null) container.stop(); }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @BeforeEach
    void fixtures() {
        for(String table:List.of("achievement_workflow_operation","achievement_material","achievement","unit_allocation_publication","unit_allocation_draft_item","unit_allocation_draft","unit_indicator_allocation",
                "topic_indicator_publication","topic_indicator_draft_target","topic_indicator_draft","topic_indicator",
                "time_node","indicator_definition","biz_topic_user_assignment","biz_topic_unit_membership","biz_topic","biz_project","audit_log","sys_user","sys_unit")) jdbc.update("DELETE FROM "+table);
        jdbc.update("INSERT INTO biz_project(id,code,name) VALUES(1,'P','Synthetic project')");
        for(int i=1;i<=5;i++) jdbc.update("INSERT INTO sys_unit(id,code,name,internal_flag,enabled) VALUES(?,?,?,?,?)",i,"U"+i,"Synthetic unit "+i,i!=3,i!=5);
        jdbc.update("INSERT INTO biz_topic(id,project_id,code,name,lead_unit_id,created_by,updated_by) VALUES(1,1,'T','Synthetic topic',1,101,101)");
        for(int i=1;i<=3;i++) jdbc.update("INSERT INTO biz_topic_unit_membership(id,topic_id,unit_id,membership_type,created_by,updated_by) VALUES(?,1,?,?,101,101)",i,i,i==1?"LEAD":"PARTICIPANT");
        for(int i=1;i<=4;i++) jdbc.update("INSERT INTO sys_user(id,username,password_hash,principal_name,contact_name,unit_id,account_type) VALUES(?,?,?,'Synthetic principal','Synthetic contact',?,'TOPIC_UNIT')",100+i,"synthetic-allocation-"+i,"unused",i);
        for(int i=1;i<=3;i++) jdbc.update("INSERT INTO biz_topic_user_assignment(membership_id,user_id,created_by,updated_by) VALUES(?,?,101,101)",i,100+i);
        jdbc.update("INSERT INTO time_node(id,project_id,code,name,deadline,sort_order,enabled) VALUES(1,1,'MID','Mid','2027-01-01',1,1),(2,1,'END','End','2028-01-01',2,1),(3,1,'OFF','Off','2029-01-01',3,0)");
        jdbc.update("INSERT INTO indicator_definition(id,code,name,achievement_type,category,unit_name) VALUES(1,'BASE','Papers','PAPER','BASE','count'),(2,'SPECIAL','Special papers','PAPER','SPECIAL','count')");
        jdbc.update("INSERT INTO topic_indicator(id,project_id,topic_id,node_id,indicator_definition_id,target_quantity,status,publish_version) VALUES(1,1,1,1,1,5,'PUBLISHED',1),(2,1,1,2,1,10,'PUBLISHED',1)");
    }

    private UsernamePasswordAuthenticationToken auth(String role,Long unit,Set<String> permissions) {
        var memberships=unit==null?List.<CurrentUser.TopicMembership>of():jdbc.query("SELECT * FROM biz_topic_unit_membership WHERE unit_id=? AND enabled=1",
                (rs,row)->new CurrentUser.TopicMembership(rs.getLong("id"),rs.getLong("topic_id"),rs.getLong("unit_id"),rs.getString("membership_type"),rs.getBoolean("enabled")),unit);
        var actor=new CurrentUser(unit==null?101:100+unit,"synthetic-actor",unit,role,permissions,memberships,0);
        return new UsernamePasswordAuthenticationToken(actor,null,permissions.stream().map(SimpleGrantedAuthority::new).toList());
    }
    private ResultActions call(MockHttpServletRequestBuilder request,String role,Long unit) throws Exception {
        return mvc.perform(request.with(authentication(auth(role,unit,Set.of("unit-allocation.manage","unit-allocation.publish","topic.manage")))).contentType("application/json"));
    }
    private String rows(int lead,int internal,int external) {
        return "["+row(1,1,lead)+","+row(2,1,internal)+","+row(3,1,external)+"]";
    }
    private String row(int unit,int definition,int quantity) {
        return "{\"unitId\":\""+unit+"\",\"indicatorDefinitionId\":\""+definition+"\",\"targetQuantity\":"+quantity+"}";
    }
    private ResultActions save(int node,int revision,String rows) throws Exception {
        return call(put("/api/v1/topics/1/unit-allocations").content("{\"nodeId\":\""+node+"\",\"draftVersion\":"+revision+",\"allocations\":"+rows+"}"),"INTERNAL_TOPIC_UNIT",1L);
    }
    private ResultActions publish(int node,int revision,String key) throws Exception {
        return call(post("/api/v1/topics/1/unit-allocations:publish").header("Idempotency-Key",key)
                .content("{\"nodeId\":\""+node+"\",\"draftVersion\":"+revision+"}"),"INTERNAL_TOPIC_UNIT",1L);
    }

    private ResultActions confirmPlan(String stages, String key) throws Exception {
        return call(put("/api/v1/topics/1/unit-allocations:confirm-plan").header("Idempotency-Key",key)
                .content("{\"stages\":"+stages+"}"),"INTERNAL_TOPIC_UNIT",1L);
    }

    @Test
    void completePlanRequiresEveryActiveStageAndPublishesAtomically() throws Exception {
        String first="{\"nodeId\":\"1\",\"draftVersion\":0,\"allocations\":"+rows(1,2,2)+"}";
        String second="{\"nodeId\":\"2\",\"draftVersion\":0,\"allocations\":"+rows(2,4,4)+"}";
        confirmPlan("["+first+"]","incomplete-plan-key").andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM unit_allocation_draft",Integer.class)).isZero();
        confirmPlan("["+first+","+second+"]","complete-plan-key").andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM unit_indicator_allocation",Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT SUM(target_quantity) FROM unit_indicator_allocation WHERE node_id=1",Long.class)).isEqualTo(5L);
        assertThat(jdbc.queryForObject("SELECT SUM(target_quantity) FROM unit_indicator_allocation WHERE node_id=2",Long.class)).isEqualTo(10L);
    }

    @Test
    void completeDraftPublishScopeAndVersionHistory() throws Exception {
        save(1,0,rows(1,2,2)).andExpect(status().isOk()).andExpect(header().string("X-Draft-Version","1"))
                .andExpect(header().string("X-Topic-Indicator-Version","1"));
        call(get("/api/v1/topics/1/unit-allocations?nodeId=1"),"INTERNAL_TOPIC_UNIT",2L).andExpect(jsonPath("$.length()").value(0));
        publish(1,1,"allocation-first-key").andExpect(status().isNoContent());
        call(get("/api/v1/topics/1/unit-allocations?nodeId=1"),"INTERNAL_TOPIC_UNIT",1L).andExpect(jsonPath("$.length()").value(3));
        call(get("/api/v1/topics/1/unit-allocations?nodeId=1"),"SYSTEM_ADMIN",null).andExpect(jsonPath("$.length()").value(3));
        call(get("/api/v1/topics/1/unit-allocations?nodeId=1"),"INTERNAL_TOPIC_UNIT",2L)
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].unitId").value("2"));
        call(get("/api/v1/topics/1/unit-allocations?nodeId=1"),"EXTERNAL_TOPIC_UNIT",3L)
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].unitId").value("3"));
        long id=jdbc.queryForObject("SELECT id FROM unit_indicator_allocation WHERE unit_id=1",Long.class);
        save(1,1,rows(2,2,1)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT target_quantity FROM unit_indicator_allocation WHERE unit_id=1",Integer.class)).isEqualTo(1);
        publish(1,2,"allocation-second-key").andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT id FROM unit_indicator_allocation WHERE unit_id=1",Long.class)).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM unit_allocation_publication",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT JSON_EXTRACT(allocations_json,'$[0].targetQuantity') FROM unit_allocation_publication WHERE publish_version=1",String.class)).isEqualTo("1");
    }

    @Test
    void draftsMayBePartialButPublicationRequiresCoverageAndExactStageTotals() throws Exception {
        save(1,0,"["+row(1,1,5)+"]").andExpect(status().isOk());
        publish(1,1,"missing-coverage-key").andExpect(status().isUnprocessableEntity());
        save(1,1,rows(0,1,1)).andExpect(status().isOk());
        publish(1,2,"insufficient-total-key").andExpect(status().isUnprocessableEntity());
        save(1,2,"[]").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        publish(1,3,"empty-allocation-key").andExpect(status().isUnprocessableEntity());
        save(1,3,rows(0,3,3)).andExpect(status().isOk());
        publish(1,4,"above-total-key").andExpect(status().isUnprocessableEntity());
        save(1,4,rows(0,3,2)).andExpect(status().isOk());
        publish(1,5,"exact-total-key").andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT SUM(target_quantity) FROM unit_indicator_allocation",Long.class)).isEqualTo(5);
    }

    @Test
    void targetVersionChangeRequiresResaveAndDoesNotOverwriteOldEffectiveRows() throws Exception {
        save(1,0,rows(1,2,2)).andExpect(status().isOk());
        publish(1,1,"target-original-key").andExpect(status().isNoContent());
        save(1,1,rows(2,2,2)).andExpect(status().isOk());
        jdbc.update("UPDATE topic_indicator SET target_quantity=7,publish_version=2 WHERE node_id=1");
        publish(1,2,"stale-topic-target-key").andExpect(status().isConflict());
        save(1,2,rows(2,2,3)).andExpect(status().isOk()).andExpect(header().string("X-Topic-Indicator-Version","2"));
        publish(1,3,"fresh-topic-target-key").andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT topic_indicator_version FROM unit_allocation_publication WHERE publish_version=2",Integer.class)).isEqualTo(2);
    }

    @Test
    void unissuedTargetsDuplicateDimensionsInvalidUnitsAndNodesAreRejected() throws Exception {
        save(1,0,"["+row(4,1,5)+"]").andExpect(status().isUnprocessableEntity());
        save(1,0,"["+row(1,2,5)+"]").andExpect(status().isUnprocessableEntity());
        save(1,0,"["+row(1,1,5)+","+row(1,1,5)+"]").andExpect(status().isUnprocessableEntity());
        save(3,0,rows(1,2,2)).andExpect(status().isUnprocessableEntity());
        save(999,0,rows(1,2,2)).andExpect(status().isUnprocessableEntity());
        jdbc.update("INSERT INTO biz_project(id,code,name,enabled) VALUES(2,'OTHER','Other',0)");
        jdbc.update("INSERT INTO time_node(id,project_id,code,name,deadline,sort_order) VALUES(4,2,'OTHER','Other','2027-01-01',1)");
        save(4,0,rows(1,2,2)).andExpect(status().isUnprocessableEntity());
        jdbc.update("UPDATE topic_indicator SET status='DRAFT' WHERE node_id=1");
        save(1,0,rows(1,2,2)).andExpect(status().isConflict());
    }

    @Test
    void activeMembershipsAreRecheckedAtPublicationAndHistoricalRowsRemain() throws Exception {
        save(1,0,rows(1,2,2)).andExpect(status().isOk());
        publish(1,1,"member-first-publish").andExpect(status().isNoContent());
        save(1,1,rows(2,2,2)).andExpect(status().isOk());
        jdbc.update("UPDATE biz_topic_unit_membership SET enabled=0 WHERE unit_id=3");
        publish(1,2,"disabled-member-key").andExpect(status().isUnprocessableEntity());
        call(get("/api/v1/topics/1/unit-allocations?nodeId=1"),"EXTERNAL_TOPIC_UNIT",3L).andExpect(status().isForbidden());
        save(1,2,"["+row(1,1,3)+","+row(2,1,2)+"]").andExpect(status().isOk());
        publish(1,3,"active-members-key").andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT target_quantity FROM unit_indicator_allocation WHERE unit_id=3",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT publish_version FROM unit_indicator_allocation WHERE unit_id=3",Integer.class)).isEqualTo(1);
    }

    @Test
    void rolesAndCrossTopicScopeCannotBypassLeadAuthorization() throws Exception {
        String body="{\"nodeId\":\"1\",\"draftVersion\":0,\"allocations\":"+rows(1,2,2)+"}";
        for(String role:List.of("SYSTEM_ADMIN","PROJECT_TECH_LEADER","RESEARCH_ASSISTANT","INTERNAL_TOPIC_UNIT","EXTERNAL_TOPIC_UNIT")) {
            Long unit=role.equals("INTERNAL_TOPIC_UNIT")?Long.valueOf(2):role.equals("EXTERNAL_TOPIC_UNIT")?Long.valueOf(3):null;
            call(put("/api/v1/topics/1/unit-allocations").content(body),role,unit).andExpect(status().isForbidden());
            call(post("/api/v1/topics/1/unit-allocations:publish").header("Idempotency-Key","denied-publication")
                    .content("{\"nodeId\":\"1\",\"draftVersion\":1}"),role,unit).andExpect(status().isForbidden());
        }
        call(get("/api/v1/topics/1/unit-allocations?nodeId=1&view=draft"),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isForbidden());
        call(get("/api/v1/topics/1/unit-allocations?nodeId=1"),"INTERNAL_TOPIC_UNIT",4L).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/topics/1/unit-allocations").with(authentication(auth("INTERNAL_TOPIC_UNIT",1L,Set.of())))
                .contentType("application/json").content(body)).andExpect(status().isForbidden());
    }

    @Test
    void changedLeadCannotReplayPreviouslySuccessfulRequest() throws Exception {
        save(1,0,rows(1,2,2)).andExpect(status().isOk());
        publish(1,1,"old-lead-publish-key").andExpect(status().isNoContent());
        var staleLead=auth("INTERNAL_TOPIC_UNIT",1L,Set.of("unit-allocation.manage","unit-allocation.publish"));
        jdbc.update("UPDATE biz_topic_unit_membership SET membership_type='PARTICIPANT' WHERE unit_id=1");
        jdbc.update("UPDATE biz_topic_unit_membership SET membership_type='LEAD' WHERE unit_id=2");
        jdbc.update("UPDATE biz_topic SET lead_unit_id=2 WHERE id=1");
        mvc.perform(post("/api/v1/topics/1/unit-allocations:publish").with(authentication(staleLead)).header("Idempotency-Key","old-lead-publish-key")
                .contentType("application/json").content("{\"nodeId\":\"1\",\"draftVersion\":1}")).andExpect(status().isForbidden());
        call(get("/api/v1/topics/1/unit-allocations?nodeId=1"),"INTERNAL_TOPIC_UNIT",1L).andExpect(jsonPath("$.length()").value(1));
        call(get("/api/v1/topics/1/unit-allocations?nodeId=1&view=draft"),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isOk());
    }

    @Test
    void eachStageIsIndependentAndCanBeUpdatedToAnotherExactDistribution() throws Exception {
        save(1,0,rows(1,2,2)).andExpect(status().isOk());
        save(2,0,rows(0,5,5)).andExpect(status().isOk());
        save(1,1,rows(3,2,0)).andExpect(status().isOk());
        publish(1,2,"mid-allocation-key").andExpect(status().isNoContent());
        publish(2,1,"end-allocation-key").andExpect(status().isNoContent());
        save(1,2,rows(0,3,2)).andExpect(status().isOk());
        publish(1,3,"redistributed-allocation-key").andExpect(status().isNoContent());
    }

    @Test
    void specialAllocationCannotExceedSameUnitBaseAndStageTotalsMustMatch() throws Exception {
        jdbc.update("INSERT INTO topic_indicator(project_id,topic_id,node_id,indicator_definition_id,target_quantity,status,publish_version) VALUES(1,1,1,2,1,'PUBLISHED',1)");
        save(1,0,"["+row(1,1,1)+","+row(1,2,2)+"]").andExpect(status().isUnprocessableEntity());
        String complete="["+row(1,1,2)+","+row(2,1,2)+","+row(3,1,1)+","+row(1,2,1)+","+row(2,2,0)+","+row(3,2,0)+"]";
        save(1,0,complete).andExpect(status().isOk());
        publish(1,1,"large-sum-publish-key").andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT SUM(target_quantity) FROM unit_indicator_allocation WHERE indicator_definition_id=1",Long.class)).isEqualTo(5L);
    }

    @Test
    void retryCannotPublishNewDraftOrReuseKeyForDifferentRevision() throws Exception {
        save(1,0,rows(1,2,2)).andExpect(status().isOk());
        publish(1,1,"durable-allocation-key").andExpect(status().isNoContent());
        save(1,1,rows(2,2,2)).andExpect(status().isOk());
        publish(1,1,"durable-allocation-key").andExpect(status().isNoContent());
        publish(1,2,"durable-allocation-key").andExpect(status().isConflict());
        publish(2,1,"durable-allocation-key").andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM unit_allocation_publication",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT target_quantity FROM unit_indicator_allocation WHERE unit_id=1",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action_code='allocation.publish'",Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentSaveAndPublicationSerializeOnTopic() throws Exception {
        save(1,0,rows(1,2,2)).andExpect(status().isOk());
        try(var executor=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<Integer> work=()->{start.await();return save(1,1,rows(2,2,1)).andReturn().getResponse().getStatus();};
            var first=executor.submit(work);var second=executor.submit(work);start.countDown();
            assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
            var publishStart=new CountDownLatch(1);
            Callable<Integer> publish=()->{publishStart.await();return publish(1,2,"concurrent-allocation-key").andReturn().getResponse().getStatus();};
            var a=executor.submit(publish);var b=executor.submit(publish);publishStart.countDown();
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactly(204,204);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM unit_allocation_publication",Integer.class)).isEqualTo(1);
    }

    @Test
    void secondRowFailureRollsBackPublicationAndAllowsRetry() throws Exception {
        save(1,0,rows(1,2,2)).andExpect(status().isOk());
        jdbc.execute("ALTER TABLE unit_indicator_allocation ADD CONSTRAINT chk_fail_allocation_test CHECK (unit_id<>2)");
        try { publish(1,1,"rollback-allocation-key").andExpect(status().isInternalServerError()); }
        finally { jdbc.execute("ALTER TABLE unit_indicator_allocation DROP CHECK chk_fail_allocation_test"); }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM unit_allocation_publication",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM unit_indicator_allocation",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT published_draft_version FROM unit_allocation_draft",Integer.class)).isZero();
        publish(1,1,"rollback-allocation-key").andExpect(status().isNoContent());
    }

    @Test
    void readOnlyTopicsMalformedAndAnonymousRequestsAreRejected() throws Exception {
        save(1,0,rows(1,2,2)).andExpect(status().isOk());
        for(String state:List.of("PAUSED","CLOSED")) {
            jdbc.update("UPDATE biz_topic SET status=?",state);
            save(1,1,rows(1,2,2)).andExpect(status().isConflict());
            publish(1,1,"readonly-allocation-key").andExpect(status().isConflict());
            call(get("/api/v1/topics/1/unit-allocations?nodeId=1"),"INTERNAL_TOPIC_UNIT",2L).andExpect(status().isOk());
        }
        jdbc.update("UPDATE biz_topic SET status='ACTIVE',enabled=0");
        publish(1,1,"disabled-topic-key").andExpect(status().isConflict());
        jdbc.update("UPDATE biz_topic SET enabled=1");
        call(get("/api/v1/topics/1/unit-allocations"),"INTERNAL_TOPIC_UNIT",1L).andExpect(status().isUnprocessableEntity());
        call(get("/api/v1/topics/1/unit-allocations?nodeId=1&view=all"),"INTERNAL_TOPIC_UNIT",1L).andExpect(status().isUnprocessableEntity());
        save(1,1,"[{\"unitId\":\"1\",\"indicatorDefinitionId\":\"1\",\"targetQuantity\":1.5}]").andExpect(status().isUnprocessableEntity());
        publish(1,1,"short").andExpect(status().isUnprocessableEntity());
        for(var request:List.of(get("/api/v1/topics/1/unit-allocations?nodeId=1"),put("/api/v1/topics/1/unit-allocations"),post("/api/v1/topics/1/unit-allocations:publish")))
            mvc.perform(request.contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
    }

    @Test
    void exportsThreeActualAllocationResponses() throws Exception {
        var samples=new LinkedHashMap<String,Object>();
        var saved=save(1,0,rows(1,2,2)).andExpect(status().isOk()).andReturn().getResponse();
        samples.put("saveUnitAllocations",Map.of("status","200","body",json.readTree(saved.getContentAsString())));
        var published=publish(1,1,"contract-allocation-key").andExpect(status().isNoContent()).andReturn().getResponse();
        assertThat(published.getContentAsString()).isEmpty();
        samples.put("publishUnitAllocations",Map.of("status","204"));
        var listed=call(get("/api/v1/topics/1/unit-allocations?nodeId=1"),"INTERNAL_TOPIC_UNIT",1L).andExpect(status().isOk()).andReturn().getResponse();
        samples.put("listUnitAllocations",Map.of("status","200","body",json.readTree(listed.getContentAsString())));
        json.writerWithDefaultPrettyPrinter().writeValue(java.nio.file.Path.of("target/allocation-contract-responses.json").toFile(),samples);
    }

    @Test
    void externalUnitCanMaintainAllocationsWhenItIsCurrentLead() throws Exception {
        jdbc.update("UPDATE biz_topic_unit_membership SET membership_type='PARTICIPANT' WHERE unit_id=1");
        jdbc.update("UPDATE biz_topic_unit_membership SET membership_type='LEAD' WHERE unit_id=3");
        jdbc.update("UPDATE biz_topic SET lead_unit_id=3 WHERE id=1");
        call(put("/api/v1/topics/1/unit-allocations").content("{\"nodeId\":\"1\",\"draftVersion\":0,\"allocations\":"+rows(1,2,2)+"}"),"EXTERNAL_TOPIC_UNIT",3L)
                .andExpect(status().isOk());
        call(post("/api/v1/topics/1/unit-allocations:publish").header("Idempotency-Key","external-lead-publish")
                .content("{\"nodeId\":\"1\",\"draftVersion\":1}"),"EXTERNAL_TOPIC_UNIT",3L).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM unit_indicator_allocation WHERE membership_id=unit_id AND topic_indicator_id=1",Integer.class)).isEqualTo(3);
    }

    @Test
    void newMembersAndDisabledUnitsAreRevalidatedBeforePublication() throws Exception {
        save(1,0,rows(1,2,2)).andExpect(status().isOk());
        jdbc.update("INSERT INTO biz_topic_unit_membership(id,topic_id,unit_id,membership_type,created_by,updated_by) VALUES(4,1,4,'PARTICIPANT',101,101)");
        publish(1,1,"new-member-coverage-key").andExpect(status().isUnprocessableEntity());
        save(1,1,"["+row(1,1,1)+","+row(2,1,2)+","+row(3,1,2)+","+row(4,1,0)+"]").andExpect(status().isOk());
        jdbc.update("UPDATE sys_unit SET enabled=0 WHERE id=4");
        publish(1,2,"disabled-unit-recheck").andExpect(status().isUnprocessableEntity());
        save(1,2,rows(1,2,2)).andExpect(status().isOk());
        publish(1,3,"remaining-units-key").andExpect(status().isNoContent());
        jdbc.update("UPDATE sys_unit SET enabled=0 WHERE id=1");
        save(1,3,rows(1,2,2)).andExpect(status().isForbidden());
        publish(1,3,"remaining-units-key").andExpect(status().isForbidden());
    }
}
