package com.procuremind.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface InventoryQueryMapper {

    List<Map<String, Object>> receipts();

    int countReceiptPage(@Param("keyword") String keyword);

    List<Map<String, Object>> receiptPage(@Param("keyword") String keyword,
                                          @Param("size") int size, @Param("offset") int offset);

    Map<String, Object> findReceipt(@Param("id") Long id);

    List<Map<String, Object>> findReceiptItems(@Param("id") Long id);

    int countBalances(@Param("like") String like);

    List<Map<String, Object>> balancesPage(@Param("like") String like,
                                           @Param("size") int size, @Param("offset") int offset);

    List<Map<String, Object>> balances();

    int countLedger(@Param("like") String like);

    List<Map<String, Object>> ledgerPage(@Param("like") String like,
                                         @Param("size") int size, @Param("offset") int offset);

    List<Map<String, Object>> ledger();
}
