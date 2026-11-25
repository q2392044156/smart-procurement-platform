package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 库存侧的纯查询，对应 resources/mapper/InventoryQueryMapper.xml；没有写方法、没有行锁。
 * 收货单、余额、台账各有一对“全量 + 分页”：全量版给导出与看板，分页版按 keyword 过滤并配 count。
 * 刻意不做权限判断——行级范围由 Service 拼进查询条件，本层只执行 SQL。
 */
@Mapper
public interface InventoryQueryMapper {

    // 收货单：receipts() 全量、receiptPage() 分页（keyword 匹配单号/订单号），findReceipt(+Items) 取详情。
    List<Map<String, Object>> receipts();

    // 三组 count 必须与各自的 page 成对使用，过滤条件不一致会导致总数与实际结果对不上。
    int countReceiptPage(@Param("keyword") String keyword);

    List<Map<String, Object>> receiptPage(@Param("keyword") String keyword,
                                          @Param("size") int size, @Param("offset") int offset);

    Map<String, Object> findReceipt(@Param("id") Long id);

    List<Map<String, Object>> findReceiptItems(@Param("id") Long id);

    int countBalances(@Param("like") String like);

    List<Map<String, Object>> balancesPage(@Param("like") String like,
                                           @Param("size") int size, @Param("offset") int offset);

    // 余额：balances() 全量、balancesPage() 分页（like 匹配物资与仓库），countBalances 供分页器。
    List<Map<String, Object>> balances();

    int countLedger(@Param("like") String like);

    List<Map<String, Object>> ledgerPage(@Param("like") String like,
                                         @Param("size") int size, @Param("offset") int offset);

    // 台账：ledger() 全量、ledgerPage() 分页，按发生时间倒序，countLedger 供分页器。
    List<Map<String, Object>> ledger();
}
