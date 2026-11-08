package com.procuremind.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真机集成测试：MySQL + Redis 容器、Flyway 迁移、Flowable 建表、演示账号初始化，
 * 再用 HTTP 覆盖登录、知识库、单号序列、提醒、Agent 工具面权限与幂等、预算拦截、出库扣减。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PlatformIntegrationTest {
    private static final String INTERNAL_TOKEN = "integration-test-internal-token-0123456789";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("procuremind").withUsername("procuremind").withPassword("procuremind123");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // Docker Desktop 在 Windows 上只映射 IPv4，localhost 可能解析到 ::1，这里固定 127.0.0.1。
        registry.add("spring.datasource.url", () -> MYSQL.getJdbcUrl().replace("//localhost:", "//127.0.0.1:"));
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("procuremind.agent.enabled", () -> "true");
        registry.add("procuremind.agent.internal-token", () -> INTERNAL_TOKEN);
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void loginAndReadKnowledgeBase() {
        String token = login("admin", "Admin@123").get("token");
        ResponseEntity<Map> response = rest.exchange("/api/v1/kb/documents", HttpMethod.GET,
                new HttpEntity<>(auth(token)), Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("code", 0);
        assertThat(((Map<?, ?>) response.getBody().get("data")).get("records")).isInstanceOf(List.class);
    }

    @Test
    void createsRequisitionsWithDailySequenceNumbers() {
        String token = login("admin", "Admin@123").get("token");
        String first = createRequisition(token, "集成测试-序列 A");
        String second = createRequisition(token, "集成测试-序列 B");
        assertThat(first).matches("PR\\d{8}-\\d{4}");
        assertThat(second).matches("PR\\d{8}-\\d{4}");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void alertsSummaryReturnsThreeBlocks() {
        String token = login("admin", "Admin@123").get("token");
        ResponseEntity<Map> response = rest.exchange("/api/v1/alerts/summary", HttpMethod.GET,
                new HttpEntity<>(auth(token)), Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data).containsKeys("qualifications", "deliveries", "approvals");
    }

    @Test
    void agentFacadeRejectsUnauthorizedToolAndDeduplicatesRetries() {
        Map<String, String> purchaser = login("purchaser", "Purchase@123");
        Map<String, String> applicant = login("requester", "Request@123");

        ResponseEntity<Map> denied = rest.exchange("/api/orders/page", HttpMethod.GET,
                new HttpEntity<>(internal(applicant.get("id"))), Map.class);
        assertThat(denied.getStatusCode().value()).isEqualTo(403);

        Long supplier = jdbc.queryForObject("select id from sp_supplier where status='QUALIFIED' and deleted=0 limit 1", Long.class);
        Long material = jdbc.queryForObject("select id from md_material where deleted=0 and enabled=1 limit 1", Long.class);
        Map<String, Object> body = Map.of(
                "supplierId", supplier,
                "deliveryDate", LocalDate.now().plusDays(3).toString(),
                "taxRate", "0.13",
                "terms", "集成测试-幂等",
                "orderDetail", List.of(Map.of("partId", material, "quantity", "1", "unitPrice", "9.90")));

        ResponseEntity<Map> first = rest.exchange("/api/orders/create", HttpMethod.POST,
                new HttpEntity<>(body, internal(purchaser.get("id"))), Map.class);
        ResponseEntity<Map> second = rest.exchange("/api/orders/create", HttpMethod.POST,
                new HttpEntity<>(body, internal(purchaser.get("id"))), Map.class);

        assertThat(first.getBody()).containsEntry("code", 0);
        Map<?, ?> firstData = (Map<?, ?>) first.getBody().get("data");
        Map<?, ?> secondData = (Map<?, ?>) second.getBody().get("data");
        assertThat(String.valueOf(secondData.get("id"))).isEqualTo(String.valueOf(firstData.get("id")));
        assertThat(String.valueOf(firstData.get("status"))).isEqualTo("DRAFT");
    }

    @Test
    void budgetBlocksOverspendingRequisition() {
        Map<String, String> quality = login("quality.requester", "QualityReq@123");
        Long department = jdbc.queryForObject("select id from sys_department where code='QA'", Long.class);
        jdbc.update("""
                insert into fin_budget(department_id,fiscal_year,amount,created_by,updated_by)
                values(?,?,?,0,0) on duplicate key update amount=values(amount)
                """, department, LocalDate.now().getYear(), new BigDecimal("1000"));
        Long material = jdbc.queryForObject("select id from md_material where deleted=0 and enabled=1 limit 1", Long.class);
        Map<String, Object> request = Map.of(
                "requiredDate", LocalDate.now().plusDays(5).toString(),
                "purpose", "集成测试-预算拦截",
                "items", List.of(Map.of("materialId", material, "quantity", 5, "estimatedUnitPrice", 1000)));
        ResponseEntity<Map> created = rest.exchange("/api/v1/requisitions", HttpMethod.POST,
                new HttpEntity<>(request, auth(quality.get("token"))), Map.class);
        assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();
        String id = String.valueOf(((Map<?, ?>) created.getBody().get("data")).get("id"));
        ResponseEntity<Map> submit = rest.exchange("/api/v1/requisitions/" + id + "/submit", HttpMethod.POST,
                new HttpEntity<>(auth(quality.get("token"))), Map.class);
        assertThat(submit.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void outboundIssueDecrementsStockAndRejectsOverdraw() {
        Map<String, String> warehouse = login("warehouse", "Warehouse@123");
        Long warehouseId = jdbc.queryForObject("select id from md_warehouse where code='MAIN'", Long.class);
        Long materialId = jdbc.queryForObject("select id from md_material where code='MOUSE-USB-001'", Long.class);
        jdbc.update("""
                insert into inv_balance(warehouse_id,material_id,quantity,created_by,updated_by)
                values(?,?,100,0,0) on duplicate key update quantity=100
                """, warehouseId, materialId);

        Map<String, Object> issue = Map.of("warehouseId", warehouseId, "materialId", materialId,
                "quantity", 10, "purpose", "集成测试领用");
        ResponseEntity<Map> response = rest.exchange("/api/v1/inventory/issues", HttpMethod.POST,
                new HttpEntity<>(issue, auth(warehouse.get("token"))), Map.class);
        assertThat(response.getBody()).containsEntry("code", 0);
        assertThat(jdbc.queryForObject("select quantity from inv_balance where warehouse_id=? and material_id=?",
                BigDecimal.class, warehouseId, materialId)).isEqualByComparingTo("90");

        ResponseEntity<Map> overdraw = rest.exchange("/api/v1/inventory/issues", HttpMethod.POST,
                new HttpEntity<>(Map.of("warehouseId", warehouseId, "materialId", materialId,
                        "quantity", 1000, "purpose", "超量"), auth(warehouse.get("token"))), Map.class);
        assertThat(overdraw.getStatusCode().value()).isEqualTo(409);
    }

    private Map<String, String> login(String username, String password) {
        ResponseEntity<Map> response = rest.postForEntity("/api/v1/auth/login",
                Map.of("username", username, "password", password), Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        Map<?, ?> user = (Map<?, ?>) data.get("user");
        return Map.of("token", String.valueOf(data.get("accessToken")), "id", String.valueOf(user.get("id")));
    }

    private HttpHeaders auth(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private HttpHeaders internal(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Internal-Token", INTERNAL_TOKEN);
        headers.add("X-User-Id", userId);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private String createRequisition(String token, String purpose) {
        Map<String, Object> body = Map.of(
                "requiredDate", LocalDate.now().plusDays(7).toString(),
                "purpose", purpose,
                "items", List.of(Map.of("materialId", 1, "quantity", 2, "estimatedUnitPrice", 10)));
        ResponseEntity<Map> created = rest.exchange("/api/v1/requisitions", HttpMethod.POST,
                new HttpEntity<>(body, auth(token)), Map.class);
        assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();
        String id = String.valueOf(((Map<?, ?>) created.getBody().get("data")).get("id"));
        ResponseEntity<Map> detail = rest.exchange("/api/v1/requisitions/" + id, HttpMethod.GET,
                new HttpEntity<>(auth(token)), Map.class);
        return String.valueOf(((Map<?, ?>) detail.getBody().get("data")).get("requisitionNo"));
    }
}
