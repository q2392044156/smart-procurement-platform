package com.procuremind.platform;

import com.procuremind.platform.mapper.IssueMapper;
import com.procuremind.platform.mapper.PurchaseOrderMapper;
import com.procuremind.platform.mapper.ReceiptMapper;
import com.procuremind.platform.mapper.RequisitionMapper;
import com.procuremind.platform.pojo.row.BalanceLock;
import com.procuremind.platform.pojo.row.PurchaseOrderLock;
import com.procuremind.platform.pojo.row.PurchaseOrderStatus;
import com.procuremind.platform.pojo.row.ReceiptItemLock;
import com.procuremind.platform.pojo.row.RequisitionLock;
import com.procuremind.platform.pojo.row.RequisitionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 决策读的映射闸门：六个加锁查询现在返回 record，若 XML 的 select 少了列、
 * 或 resultType 退回 map，这里会因为字段为 null 或类型不匹配而失败——
 * 正是当初 {@code lockById} 漏 {@code total_amount} 导致订单提交 500 的那类问题。
 */
@SpringBootTest
@Testcontainers
class LockMappingTest {

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
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RequisitionMapper requisitions;

    @Autowired
    PurchaseOrderMapper purchaseOrders;

    @Autowired
    ReceiptMapper receipts;

    @Autowired
    IssueMapper issues;

    @Test
    void requisitionLockCarriesEveryDecisionField() {
        Long id = jdbc.queryForObject(
                "select id from pr_requisition where deleted=0 order by id limit 1", Long.class);

        RequisitionLock lock = requisitions.lockById(id);

        assertThat(lock).as("申请不存在时不该走到这里").isNotNull();
        assertThat(lock.applicantId()).as("applicant_id 漏列会让归属校验失效").isNotNull();
        assertThat(lock.departmentId()).as("department_id 漏列会让预算校验失效").isNotNull();
        assertThat(lock.budgetAmount()).as("budget_amount 漏列会 NPE 或跳过预算拦截").isNotNull();
        assertThat(lock.status()).as("status 漏列会让状态机判断失效").isNotBlank();
    }

    @Test
    void purchaseOrderLockCarriesEveryDecisionField() {
        Long id = jdbc.queryForObject(
                "select id from po_purchase_order where deleted=0 order by id limit 1", Long.class);

        PurchaseOrderLock lock = purchaseOrders.lockById(id);

        assertThat(lock).isNotNull();
        assertThat(lock.orderNo()).isNotBlank();
        assertThat(lock.purchaserId()).as("purchaser_id 漏列会让属主校验失效").isNotNull();
        assertThat(lock.status()).as("status 漏列会让编辑/提交判断走错分支").isNotBlank();
        assertThat(lock.totalAmount()).as("total_amount 漏列就是订单提交 500 的原始事故").isNotNull();
        // supplier_id / requisition_id 在直采单上本来就可以为空，这里不锁定它们的取值。
    }

    @Test
    void statusOnlyLocksMapTheirStatus() {
        Long requisitionId = jdbc.queryForObject(
                "select id from pr_requisition where deleted=0 order by id limit 1", Long.class);
        Long orderId = jdbc.queryForObject(
                "select id from po_purchase_order where deleted=0 order by id limit 1", Long.class);

        RequisitionStatus requisition = purchaseOrders.lockRequisition(requisitionId);
        PurchaseOrderStatus order = receipts.lockOrder(orderId);

        assertThat(requisition).isNotNull();
        assertThat(requisition.status()).isNotBlank();
        assertThat(order).isNotNull();
        assertThat(order.status()).isNotBlank();
    }

    @Test
    void receiptItemLockCarriesQuantitiesAndTrackingMode() {
        Map<String, Object> row = jdbc.queryForMap("""
                select i.id, i.purchase_order_id
                from po_purchase_order_item i join md_material m on m.id = i.material_id
                where i.deleted = 0 and m.deleted = 0
                order by i.id limit 1
                """);
        Long id = ((Number) row.get("id")).longValue();
        Long orderId = ((Number) row.get("purchase_order_id")).longValue();

        ReceiptItemLock lock = receipts.lockOrderItem(id, orderId);

        assertThat(lock).isNotNull();
        assertThat(lock.materialId()).isNotNull();
        assertThat(lock.quantity()).as("quantity 漏列会让剩余可收量算错").isNotNull();
        assertThat(lock.receivedQuantity()).as("received_quantity 漏列会让超收校验静默失效").isNotNull();
        assertThat(lock.trackingMode()).as("tracking_mode 漏列会跳过批次/序列号校验").isNotBlank();
    }

    @Test
    void balanceLockCarriesVersionForOptimisticUpdate() {
        Map<String, Object> row = jdbc.queryForMap("""
                select warehouse_id, material_id from inv_balance
                where deleted = 0 order by id limit 1
                """);
        Long warehouseId = ((Number) row.get("warehouse_id")).longValue();
        Long materialId = ((Number) row.get("material_id")).longValue();

        BalanceLock lock = issues.lockBalance(warehouseId, materialId);

        assertThat(lock).isNotNull();
        assertThat(lock.id()).isNotNull();
        assertThat(lock.quantity()).as("quantity 漏列会让库存不足校验失效").isNotNull();
        assertThat(lock.version()).as("version 漏列会让 CAS 更新条件失效").isNotNull();
    }

    @Test
    void locksReturnNullForMissingRows() {
        assertThat(requisitions.lockById(99_999_999L)).isNull();
        assertThat(purchaseOrders.lockById(99_999_999L)).isNull();
        assertThat(issues.lockBalance(99_999_999L, 99_999_999L)).isNull();
    }
}
