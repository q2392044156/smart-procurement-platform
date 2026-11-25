package com.procuremind.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 接口契约快照测试：把 20 个高频端点的响应“形状”（字段名、嵌套关系、标量类型）沉成
 * {@code src/test/resources/contract/api-shapes.json}，任何改动只要改变了返回结构就会红。
 *
 * <p>它保护的是前端与 Harness 依赖的 JSON 契约，而不是具体数据：字段值可以变，
 * 字段名、层级、类型不能悄悄变。需要有意变更契约时用
 * {@code mvn test -Dtest=ApiContractTest -Dcontract.update=true} 重新生成快照，
 * 并在 PR 里把 json 的 diff 一起提交。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ApiContractTest {
    private static final String INTERNAL_TOKEN = "integration-test-internal-token-0123456789";
    private static final Path SNAPSHOT = Path.of("src/test/resources/contract/api-shapes.json");
    private static final int MAX_DEPTH = 6;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("procuremind").withUsername("procuremind").withPassword("procuremind123");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
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

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void responseShapesMatchCommittedSnapshot() throws IOException {
        String token = login("admin", "Admin@123");

        ObjectNode page = data(send(token, "/api/v1/requisitions/page?current=1&size=5"));
        String requisitionId = firstId(page);
        ObjectNode orders = data(send(token, "/api/v1/purchase-orders/page?current=1&size=5"));
        String orderId = firstId(orders);
        ObjectNode suppliers = data(send(token, "/api/v1/suppliers/page?current=1&size=5"));
        String supplierId = firstId(suppliers);

        Map<String, String> endpoints = new LinkedHashMap<>();
        endpoints.put("GET /api/v1/auth/me", "/api/v1/auth/me");
        endpoints.put("GET /api/v1/requisitions/page", "/api/v1/requisitions/page?current=1&size=5");
        endpoints.put("GET /api/v1/requisitions/{id}", "/api/v1/requisitions/" + requisitionId);
        endpoints.put("GET /api/v1/purchase-orders/page", "/api/v1/purchase-orders/page?current=1&size=5");
        endpoints.put("GET /api/v1/purchase-orders/{id}", "/api/v1/purchase-orders/" + orderId);
        endpoints.put("GET /api/v1/receipts/page", "/api/v1/receipts/page?current=1&size=5");
        endpoints.put("GET /api/v1/inventory/balances/page", "/api/v1/inventory/balances/page?current=1&size=5");
        endpoints.put("GET /api/v1/inventory/ledger/page", "/api/v1/inventory/ledger/page?current=1&size=5");
        endpoints.put("GET /api/v1/inventory/issues", "/api/v1/inventory/issues?current=1&size=5");
        endpoints.put("GET /api/v1/suppliers/page", "/api/v1/suppliers/page?current=1&size=5");
        endpoints.put("GET /api/v1/suppliers/{id}", "/api/v1/suppliers/" + supplierId);
        endpoints.put("GET /api/v1/materials/page", "/api/v1/materials/page?current=1&size=5");
        endpoints.put("GET /api/v1/contracts", "/api/v1/contracts?current=1&size=5");
        endpoints.put("GET /api/v1/budgets", "/api/v1/budgets");
        endpoints.put("GET /api/v1/dashboard/summary", "/api/v1/dashboard/summary");
        endpoints.put("GET /api/v1/alerts/summary", "/api/v1/alerts/summary");
        endpoints.put("GET /api/v1/workflow/tasks", "/api/v1/workflow/tasks");
        endpoints.put("GET /api/v1/kb/documents", "/api/v1/kb/documents?current=1&size=5");
        endpoints.put("GET /api/v1/users", "/api/v1/users");
        endpoints.put("GET /api/v1/audits", "/api/v1/audits");

        ObjectNode actual = json.createObjectNode();
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> entry : endpoints.entrySet()) {
            ObjectNode body = send(token, entry.getValue());
            assertThat(body.path("code").asInt()).as(entry.getKey() + " 返回 code").isZero();
            actual.set(entry.getKey(), shape(body.get("data"), 0));
        }

        if (Boolean.getBoolean("contract.update")) {
            Files.createDirectories(SNAPSHOT.getParent());
            ObjectNode sorted = json.createObjectNode();
            actual.fieldNames().forEachRemaining(name -> sorted.set(name, actual.get(name)));
            Files.writeString(SNAPSHOT, json.writerWithDefaultPrettyPrinter().writeValueAsString(sorted) + "\n",
                    StandardCharsets.UTF_8);
            return;
        }

        assertThat(Files.exists(SNAPSHOT))
                .as("缺少契约快照，先跑一次 -Dcontract.update=true 生成：" + SNAPSHOT.toAbsolutePath())
                .isTrue();
        JsonNode expected = json.readTree(Files.readString(SNAPSHOT, StandardCharsets.UTF_8));
        for (Map.Entry<String, String> entry : endpoints.entrySet()) {
            JsonNode want = expected.get(entry.getKey());
            JsonNode got = actual.get(entry.getKey());
            if (want == null) {
                problems.add(entry.getKey() + "：快照里没有这个端点");
            } else if (!want.equals(got)) {
                problems.add(entry.getKey() + "：\n  期望 " + want + "\n  实际 " + got);
            }
        }
        Files.createDirectories(Path.of("target", "contract"));
        Files.writeString(Path.of("target", "contract", "api-shapes.actual.json"),
                json.writerWithDefaultPrettyPrinter().writeValueAsString(actual), StandardCharsets.UTF_8);
        assertThat(problems).as("响应契约发生变化（如为有意改动，用 -Dcontract.update=true 重新生成快照）").isEmpty();
    }

    /**
     * 递归提取“形状”：对象→按字段名排序的对象；数组→首个元素的形状；标量→类型名。
     * 空对象/空数组单独标记，避免“字段消失”被判成“空对象”。
     */
    private JsonNode shape(JsonNode node, int depth) {
        if (node == null || node.isNull()) {
            return json.createObjectNode().put("$type", "null");
        }
        if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            ObjectNode result = json.createObjectNode();
            if (array.isEmpty()) {
                result.put("$type", "array").put("$items", "empty");
            } else {
                result.put("$type", "array").set("$items", shape(array.get(0), depth + 1));
            }
            return result;
        }
        if (node.isObject()) {
            ObjectNode result = json.createObjectNode();
            if (node.isEmpty()) {
                return result.put("$type", "object");
            }
            List<String> names = new ArrayList<>();
            for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
                names.add(it.next());
            }
            names.sort(String::compareTo);
            result.put("$type", "object");
            for (String name : names) {
                JsonNode child = node.get(name);
                result.set(name, depth >= MAX_DEPTH ? json.createObjectNode().put("$type", child.getNodeType().name().toLowerCase())
                        : shape(child, depth + 1));
            }
            return result;
        }
        if (node.isNumber()) {
            return json.createObjectNode().put("$type", "number");
        }
        if (node.isBoolean()) {
            return json.createObjectNode().put("$type", "boolean");
        }
        return json.createObjectNode().put("$type", "string");
    }

    private String firstId(ObjectNode page) {
        JsonNode records = page.get("records");
        assertThat(records).as("分页接口返回 records").isNotNull();
        assertThat(records.isArray() && !records.isEmpty()).as("演示数据里至少有一条记录").isTrue();
        return records.get(0).get("id").asText();
    }

    private ObjectNode data(ObjectNode body) {
        JsonNode data = body.get("data");
        assertThat(data).as("响应体包含 data").isNotNull();
        assertThat(data.isObject()).as("data 是对象").isTrue();
        return (ObjectNode) data;
    }

    private ObjectNode send(String token, String path) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> response = rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).as("GET " + path + " 状态码").isTrue();
        JsonNode body = json.readTree(response.getBody());
        assertThat(body.path("code").asInt()).as("GET " + path + " code").isZero();
        return (ObjectNode) body;
    }

    private String login(String username, String password) throws IOException {
        ResponseEntity<String> response = rest.postForEntity("/api/v1/auth/login",
                Map.of("username", username, "password", password), String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return json.readTree(response.getBody()).path("data").path("accessToken").asText();
    }
}
