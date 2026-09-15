package com.gzxm.server.modules.topic;

import com.fasterxml.jackson.databind.*;
import com.gzxm.server.GzxmApplication;
import com.gzxm.server.common.exception.BusinessException;
import com.gzxm.server.common.security.CurrentUser;
import com.gzxm.server.modules.topic.api.TopicDtos.TopicWriteRequest;
import com.gzxm.server.modules.topic.application.TopicService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;

import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Full production application + real MySQL; no H2, mocked persistence, or skipped integration checks. */
@SpringBootTest(classes = GzxmApplication.class)
@AutoConfigureMockMvc
class TopicIntegrationTest {
    private static MySQLContainer<?> container;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("GZXM_TOPIC_TEST_MYSQL_URL");
        if (url == null || url.isBlank()) {
            container = new MySQLContainer<>("mysql:8.4").withDatabaseName("gzxm_topic_test_container");
            container.start();
            registry.add("spring.datasource.url", container::getJdbcUrl);
            registry.add("spring.datasource.username", container::getUsername);
            registry.add("spring.datasource.password", container::getPassword);
        } else {
            // Destructive fixtures must never run against a deployed or remotely addressed database.
            if (!url.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/gzxm_topic_test_[a-zA-Z0-9_]+(\\?.*)?"))
                throw new IllegalArgumentException("Integration URL must name a local gzxm_topic_test_* database");
            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("GZXM_TOPIC_TEST_MYSQL_USER", "root"));
            registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("GZXM_TOPIC_TEST_MYSQL_PASSWORD", ""));
        }
        registry.add("app.bootstrap.enabled", () -> false);
        registry.add("app.bootstrap.admin-password", () -> UUID.randomUUID().toString());
        String secret = UUID.randomUUID().toString() + UUID.randomUUID();
        registry.add("app.security.jwt-secret", () -> secret);
        registry.add("spring.data.redis.password", () -> "");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TopicService service;

    @BeforeEach
    void fixtures() {
        jdbc.update("DELETE FROM biz_topic_unit_membership");
        jdbc.update("DELETE FROM biz_topic");
        jdbc.update("DELETE FROM biz_project");
        jdbc.update("DELETE FROM sys_unit");
        jdbc.update("DELETE FROM audit_log");
        jdbc.update("INSERT INTO biz_project(id,code,name,enabled) VALUES(1,'TEST-P','Synthetic project',1)");
        for (int unit = 1; unit <= 5; unit++) {
            jdbc.update("INSERT INTO sys_unit(id,code,name,internal_flag,enabled) VALUES(?,?,?,?,?)",
                    unit, "TEST-U" + unit, "Synthetic unit " + unit, unit != 3, unit != 5);
        }
    }

    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }
    @AfterAll static void stopContainer() { if (container != null) container.stop(); }

    @Test
    void createsAndReadsTopicAndMembersWithStringIdsAndCommittedAudit() throws Exception {
        JsonNode topic = create("TEST-T", "1", List.of("2", "3"));
        assertThat(topic.path("id").isTextual()).isTrue();
        assertThat(topic.path("recordVersion").asInt()).isEqualTo(1);
        assertThat(topic.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(topic.path("members").size()).isEqualTo(3);
        String id = topic.path("id").asText();
        call(get("/api/v1/topics/" + id), "INTERNAL_TOPIC_UNIT", 2L).andExpect(status().isOk());
        call(get("/api/v1/topics/" + id + "/members"), "EXTERNAL_TOPIC_UNIT", 3L)
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].membershipType").value("LEAD"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action_code='topic.create'", Integer.class)).isEqualTo(1);
    }

    @Test
    void filtersListCountAndDetailsByActualMembership() throws Exception {
        String own = create("VISIBLE", "1", List.of("2")).path("id").asText();
        String other = create("HIDDEN", "3", List.of()).path("id").asText();
        call(get("/api/v1/topics?page=1&size=1"), "INTERNAL_TOPIC_UNIT", 2L)
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(own));
        call(get("/api/v1/topics/" + other), "INTERNAL_TOPIC_UNIT", 2L).andExpect(status().isForbidden());
        call(get("/api/v1/topics/" + other + "/members"), "INTERNAL_TOPIC_UNIT", 2L).andExpect(status().isForbidden());
        call(get("/api/v1/topics?keyword=HIDDEN&enabled=true&status=ACTIVE"), "SYSTEM_ADMIN", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        call(get("/api/v1/topics?keyword=' OR 1=1 --"), "SYSTEM_ADMIN", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void deniesUnauthenticatedRequestsForEveryOperation() throws Exception {
        for (var request : List.of(get("/api/v1/topics"), post("/api/v1/topics"), get("/api/v1/topics/1"),
                put("/api/v1/topics/1"), put("/api/v1/topics/1/status"), get("/api/v1/topics/1/members"),
                post("/api/v1/topics/1/members"), put("/api/v1/topics/1/members/1/status"))) {
            mvc.perform(request.contentType("application/json").content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"SYSTEM_ADMIN", "PROJECT_TECH_LEADER", "INTERNAL_TOPIC_UNIT", "EXTERNAL_TOPIC_UNIT"})
    void onlyAssistantMayManageTopicsEvenWithMisconfiguredAuthorities(String role) throws Exception {
        String id = create("TEST-T", "1", List.of()).path("id").asText();
        var body = write("TEST-T", "1", List.of(), 1);
        Long unit = role.endsWith("TOPIC_UNIT") ? 1L : null;
        call(post("/api/v1/topics").content(json.writeValueAsString(body)), role, unit).andExpect(status().isForbidden());
        call(put("/api/v1/topics/" + id).content(json.writeValueAsString(body)), role, unit).andExpect(status().isForbidden());
        call(put("/api/v1/topics/" + id + "/status").content("{\"enabled\":false}"), role, unit).andExpect(status().isForbidden());
    }

    @Test
    void methodAuthorizationRejectsAssistantWithoutActionPermission() throws Exception {
        var user = new CurrentUser(101, "synthetic", null, "RESEARCH_ASSISTANT", Set.of(), List.of(), 0);
        mvc.perform(post("/api/v1/topics").with(authentication(auth(user))).contentType("application/json")
                .content(json.writeValueAsString(write("TEST-T", "1", List.of(), null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void changesLeadAtomicallyAndKeepsOldLeadAndAdditiveMembers() throws Exception {
        String id = create("TEST-T", "1", List.of("2")).path("id").asText();
        call(put("/api/v1/topics/" + id).content(json.writeValueAsString(write("UPDATED", "2", List.of("3"), 1))),
                "RESEARCH_ASSISTANT", null).andExpect(status().isOk()).andExpect(jsonPath("$.recordVersion").value(2));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM biz_topic_unit_membership WHERE topic_id=? AND membership_type='LEAD' AND enabled=1",
                Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT membership_type FROM biz_topic_unit_membership WHERE topic_id=? AND unit_id=1", String.class, id)).isEqualTo("PARTICIPANT");
        call(post("/api/v1/topics/" + id + "/members").content("{\"unitId\":\"4\"}"), "INTERNAL_TOPIC_UNIT", 1L)
                .andExpect(status().isForbidden());
        call(post("/api/v1/topics/" + id + "/members").content("{\"unitId\":\"4\"}"), "INTERNAL_TOPIC_UNIT", 2L)
                .andExpect(status().isCreated());
        int version = jdbc.queryForObject("SELECT record_version FROM biz_topic WHERE id=?", Integer.class, id);
        call(put("/api/v1/topics/" + id).content(json.writeValueAsString(write("UPDATED", "2", null, version))),
                "RESEARCH_ASSISTANT", null).andExpect(status().isOk()).andExpect(jsonPath("$.members.length()").value(4));
    }

    @Test
    void leadCanDisableRestoreButCannotDuplicateOrDisableLeadOrCrossTopicMember() throws Exception {
        String id = create("TEST-T", "1", List.of("2")).path("id").asText();
        String other = create("OTHER", "3", List.of("4")).path("id").asText();
        long member = memberId(id, 2), lead = memberId(id, 1), foreign = memberId(other, 4);
        call(post("/api/v1/topics/" + id + "/members").content("{\"unitId\":\"2\"}"), "INTERNAL_TOPIC_UNIT", 1L).andExpect(status().isConflict());
        call(put("/api/v1/topics/" + id + "/members/" + lead + "/status").content("{\"enabled\":false}"), "INTERNAL_TOPIC_UNIT", 1L).andExpect(status().isConflict());
        call(put("/api/v1/topics/" + id + "/members/" + foreign + "/status").content("{\"enabled\":false}"), "INTERNAL_TOPIC_UNIT", 1L).andExpect(status().isNotFound());
        call(put("/api/v1/topics/" + id + "/members/" + member + "/status").content("{\"enabled\":false}"), "INTERNAL_TOPIC_UNIT", 1L).andExpect(status().isOk());
        call(get("/api/v1/topics/" + id), "INTERNAL_TOPIC_UNIT", 2L).andExpect(status().isForbidden());
        call(get("/api/v1/topics"), "INTERNAL_TOPIC_UNIT", 2L).andExpect(jsonPath("$.total").value(0));
        call(get("/api/v1/topics/" + id), "SYSTEM_ADMIN", null).andExpect(status().isOk());
        call(post("/api/v1/topics/" + id + "/members").content("{\"unitId\":\"2\"}"), "INTERNAL_TOPIC_UNIT", 1L).andExpect(status().isConflict());
        call(put("/api/v1/topics/" + id + "/members/" + member + "/status").content("{\"enabled\":true}"), "INTERNAL_TOPIC_UNIT", 1L).andExpect(status().isOk());
        call(get("/api/v1/topics/" + id), "INTERNAL_TOPIC_UNIT", 2L).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"RESEARCH_ASSISTANT", "SYSTEM_ADMIN", "PROJECT_TECH_LEADER", "PARTICIPANT", "OTHER_LEAD"})
    void refusesMemberWritesFromNonLeadActors(String actor) throws Exception {
        String id = create("TEST-T", "1", List.of("2")).path("id").asText();
        create("OTHER", "3", List.of());
        long member = memberId(id, 2);
        String role = Set.of("PARTICIPANT", "OTHER_LEAD").contains(actor) ? "INTERNAL_TOPIC_UNIT" : actor;
        Long unit = null;
        if ("PARTICIPANT".equals(actor)) unit = 2L;
        if ("OTHER_LEAD".equals(actor)) unit = 3L;
        call(post("/api/v1/topics/" + id + "/members").content("{\"unitId\":\"4\"}"), role, unit).andExpect(status().isForbidden());
        call(put("/api/v1/topics/" + id + "/members/" + member + "/status").content("{\"enabled\":false}"), role, unit).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PAUSED", "CLOSED", "DISABLED"})
    void readOnlyTopicsCanBeRestoredThroughStatusEndpoint(String state) throws Exception {
        String id = create("TEST-T", "1", List.of("2")).path("id").asText();
        String statusBody = "DISABLED".equals(state) ? "{\"enabled\":false}" : "{\"enabled\":true,\"status\":\"" + state + "\"}";
        call(put("/api/v1/topics/" + id + "/status").content(statusBody), "RESEARCH_ASSISTANT", null).andExpect(status().isOk());
        call(get("/api/v1/topics/" + id), "INTERNAL_TOPIC_UNIT", 2L).andExpect(status().isOk());
        call(put("/api/v1/topics/" + id).content(json.writeValueAsString(write("CHANGE", "1", null, 2))), "RESEARCH_ASSISTANT", null).andExpect(status().isConflict());
        call(post("/api/v1/topics/" + id + "/members").content("{\"unitId\":\"3\"}"), "INTERNAL_TOPIC_UNIT", 1L).andExpect(status().isConflict());
        call(put("/api/v1/topics/" + id + "/members/" + memberId(id, 2) + "/status").content("{\"enabled\":false}"), "INTERNAL_TOPIC_UNIT", 1L).andExpect(status().isConflict());
        call(put("/api/v1/topics/" + id + "/status").content("{\"enabled\":true,\"status\":\"ACTIVE\"}"), "RESEARCH_ASSISTANT", null).andExpect(status().isOk());
        call(post("/api/v1/topics/" + id + "/members").content("{\"unitId\":\"3\"}"), "INTERNAL_TOPIC_UNIT", 1L).andExpect(status().isCreated());
    }

    @Test
    void rejectsMissingOrAmbiguousProjectWithoutImplicitInitialization() throws Exception {
        jdbc.update("DELETE FROM biz_project");
        call(post("/api/v1/topics").content(json.writeValueAsString(write("TEST-T", "1", null, null))), "RESEARCH_ASSISTANT", null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROJECT_CONFIGURATION_REQUIRED"));
        jdbc.update("INSERT INTO biz_project(code,name) VALUES('ONE','Synthetic one'),('TWO','Synthetic two')");
        call(post("/api/v1/topics").content(json.writeValueAsString(write("TEST-T", "1", null, null))), "RESEARCH_ASSISTANT", null).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM biz_topic", Integer.class)).isZero();
    }

    @Test
    void rejectsInvalidUnitsAndDuplicateParticipantList() throws Exception {
        for (var request : List.of(write("TEST-T", "5", null, null), write("TEST-T", "99", null, null),
                write("TEST-T", "1", List.of("2", "2"), null), write("TEST-T", "1", List.of("1"), null))) {
            call(post("/api/v1/topics").content(json.writeValueAsString(request)), "RESEARCH_ASSISTANT", null).andExpect(status().isUnprocessableEntity());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM biz_topic", Integer.class)).isZero();
    }

    @Test
    void duplicateCodeDuringLeadChangeRollsBackAllMembershipChanges() throws Exception {
        create("TAKEN", "3", List.of());
        String id = create("ORIGINAL", "1", List.of("2")).path("id").asText();
        call(put("/api/v1/topics/" + id).content(json.writeValueAsString(write("TAKEN", "2", List.of("4"), 1))),
                "RESEARCH_ASSISTANT", null).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT lead_unit_id FROM biz_topic WHERE id=?", Long.class, id)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT membership_type FROM biz_topic_unit_membership WHERE topic_id=? AND unit_id=1", String.class, id)).isEqualTo("LEAD");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM biz_topic_unit_membership WHERE topic_id=?", Integer.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action_code='topic.update'", Integer.class)).isZero();
    }

    @Test
    void staleAndMissingEditVersionsAreRejected() throws Exception {
        String id = create("TEST-T", "1", List.of()).path("id").asText();
        for (Integer version : Arrays.asList(null, 9)) {
            call(put("/api/v1/topics/" + id).content(json.writeValueAsString(write("CHANGE", "1", null, version))),
                    "RESEARCH_ASSISTANT", null).andExpect(status().isConflict());
        }
    }

    @Test
    void concurrentEditsWithSameVersionHaveExactlyOneWinner() throws Exception {
        long id = create("TEST-T", "1", List.of()).path("id").asLong();
        var user = actor("RESEARCH_ASSISTANT", null);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<String>> results = new ArrayList<>();
            for (String code : List.of("FIRST", "SECOND")) {
                results.add(executor.submit(() -> {
                    SecurityContextHolder.getContext().setAuthentication(auth(user));
                    try {
                        start.await();
                        service.update(id, write(code, "1", null, 1));
                        return "OK";
                    } catch (BusinessException ex) { return ex.code(); }
                    finally { SecurityContextHolder.clearContext(); }
                }));
            }
            start.countDown();
            assertThat(List.of(results.get(0).get(15, TimeUnit.SECONDS), results.get(1).get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("OK", "TOPIC_VERSION_CONFLICT");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=0", "size=201", "page=abc", "status=UNKNOWN", "enabled=not-boolean", "page=9223372036854775807&size=200"})
    void rejectsMalformedQueryWithProblemDetails(String query) throws Exception {
        call(get("/api/v1/topics?" + query), "SYSTEM_ADMIN", null).andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json")).andExpect(jsonPath("$.code").exists());
    }

    @Test
    void rejectsMissingEnabledAndMalformedDateInsteadOfSilentlyDisabling() throws Exception {
        String id = create("TEST-T", "1", List.of("2")).path("id").asText();
        call(put("/api/v1/topics/" + id + "/status").content("{}"), "RESEARCH_ASSISTANT", null).andExpect(status().isUnprocessableEntity());
        call(put("/api/v1/topics/" + id + "/members/" + memberId(id, 2) + "/status").content("{}"), "INTERNAL_TOPIC_UNIT", 1L).andExpect(status().isUnprocessableEntity());
        call(post("/api/v1/topics").content("{\"code\":\"T\",\"name\":\"T\",\"leadUnitId\":\"1\",\"startDate\":\"not-a-date\"}"), "RESEARCH_ASSISTANT", null).andExpect(status().isUnprocessableEntity());
        call(get("/api/v1/topics/9999999999999999999999"), "SYSTEM_ADMIN", null).andExpect(status().isUnprocessableEntity());
        call(get("/api/v1/topics/999999"), "SYSTEM_ADMIN", null).andExpect(status().isNotFound());
    }

    @Test
    void rejectsReversedDatesAndOverlongCode() throws Exception {
        var body = json.valueToTree(write("TEST-T", "1", List.of(), null));
        ((com.fasterxml.jackson.databind.node.ObjectNode) body).put("startDate", "2026-12-01").put("endDate", "2026-01-01");
        call(post("/api/v1/topics").content(body.toString()), "RESEARCH_ASSISTANT", null).andExpect(status().isUnprocessableEntity());
        call(post("/api/v1/topics").content(json.writeValueAsString(write("X".repeat(65), "1", List.of(), null))),
                "RESEARCH_ASSISTANT", null).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void databaseUniquenessProtectsLeadAndMembershipEvenWithoutServiceValidation() throws Exception {
        String id = create("TEST-T", "1", List.of("2")).path("id").asText();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO biz_topic_unit_membership(topic_id,unit_id,membership_type,enabled,created_by,updated_by) VALUES(?,3,'LEAD',1,101,101)", id))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO biz_topic_unit_membership(topic_id,unit_id,membership_type,enabled,created_by,updated_by) VALUES(?,2,'PARTICIPANT',1,101,101)", id))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test
    void stalePrincipalCannotRetainLeadWriteAccessAfterTransfer() throws Exception {
        String id = create("TEST-T", "1", List.of("2")).path("id").asText();
        var oldPrincipal = actor("INTERNAL_TOPIC_UNIT", 1L);
        call(put("/api/v1/topics/" + id).content(json.writeValueAsString(write("TEST-T", "2", null, 1))),
                "RESEARCH_ASSISTANT", null).andExpect(status().isOk());
        mvc.perform(post("/api/v1/topics/" + id + "/members").with(authentication(auth(oldPrincipal)))
                .contentType("application/json").content("{\"unitId\":\"3\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void exportsActualResponsesForAllEightOperationSchemas() throws Exception {
        var samples = new LinkedHashMap<String, Object>();
        JsonNode topic = create("CONTRACT", "1", List.of("2"));
        String id = topic.path("id").asText();
        samples.put("createTopic", Map.of("status", "201", "body", topic));
        capture(samples, "listTopics", get("/api/v1/topics"), "SYSTEM_ADMIN", null, 200);
        capture(samples, "getTopic", get("/api/v1/topics/" + id), "INTERNAL_TOPIC_UNIT", 1L, 200);
        capture(samples, "listTopicMembers", get("/api/v1/topics/" + id + "/members"), "INTERNAL_TOPIC_UNIT", 2L, 200);
        capture(samples, "updateTopic", put("/api/v1/topics/" + id).content(json.writeValueAsString(write("CONTRACT", "1", null, 1))), "RESEARCH_ASSISTANT", null, 200);
        capture(samples, "addTopicParticipant", post("/api/v1/topics/" + id + "/members").content("{\"unitId\":\"3\"}"), "INTERNAL_TOPIC_UNIT", 1L, 201);
        capture(samples, "setTopicMembershipStatus", put("/api/v1/topics/" + id + "/members/" + memberId(id, 2) + "/status").content("{\"enabled\":false}"), "INTERNAL_TOPIC_UNIT", 1L, 200);
        capture(samples, "setTopicStatus", put("/api/v1/topics/" + id + "/status").content("{\"enabled\":true,\"status\":\"PAUSED\"}"), "RESEARCH_ASSISTANT", null, 200);
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target"));
        json.writerWithDefaultPrettyPrinter().writeValue(java.nio.file.Path.of("target/topic-contract-responses.json").toFile(), samples);
    }

    private void capture(Map<String, Object> samples, String operation, MockHttpServletRequestBuilder request,
                          String role, Long unit, int expected) throws Exception {
        var response = call(request, role, unit).andExpect(status().is(expected)).andReturn().getResponse();
        samples.put(operation, Map.of("status", String.valueOf(expected), "body", json.readTree(response.getContentAsString())));
    }

    @Test
    void flywayAndRuntimeOpenApiContainTopicModule() throws Exception {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1", Integer.class)).isEqualTo(1);
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/topics'].get.operationId").value("listTopics"))
                .andExpect(jsonPath("$.paths['/api/v1/topics/{topicId}/members'].post.operationId").value("addTopicParticipant"));
    }

    private JsonNode create(String code, String lead, List<String> participants) throws Exception {
        var result = call(post("/api/v1/topics").content(json.writeValueAsString(write(code, lead, participants, null))),
                "RESEARCH_ASSISTANT", null).andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private TopicWriteRequest write(String code, String lead, List<String> participants, Integer version) {
        return new TopicWriteRequest(code, "Synthetic topic", null, lead, participants, null, null, version);
    }

    private long memberId(String topic, long unit) {
        return jdbc.queryForObject("SELECT id FROM biz_topic_unit_membership WHERE topic_id=? AND unit_id=?", Long.class, topic, unit);
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String role, Long unit) throws Exception {
        return mvc.perform(request.with(authentication(auth(actor(role, unit)))).contentType("application/json"));
    }

    private CurrentUser actor(String role, Long unit) {
        var memberships = unit == null ? List.<CurrentUser.TopicMembership>of() : jdbc.query(
                "SELECT * FROM biz_topic_unit_membership WHERE unit_id=? AND enabled=1",
                (rs, row) -> new CurrentUser.TopicMembership(rs.getLong("id"), rs.getLong("topic_id"), rs.getLong("unit_id"),
                        rs.getString("membership_type"), rs.getBoolean("enabled")), unit);
        return new CurrentUser(101, "synthetic-actor", unit, role,
                Set.of("topic.manage", "topic-unit.manage", "ROLE_" + role), memberships, 0);
    }

    private UsernamePasswordAuthenticationToken auth(CurrentUser user) {
        return new UsernamePasswordAuthenticationToken(user, null, user.authorities().stream().map(SimpleGrantedAuthority::new).toList());
    }
}
