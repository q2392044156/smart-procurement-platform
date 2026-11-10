# 演示业务数据

`demo_business_data.sql` 是可重复执行的演示业务数据脚本，覆盖：

- 时间范围：2025-11 至 2026-11（约 12 个月）
- 采购申请：56 张（含 DRAFT / APPROVED / CONVERTED / REJECTED / CANCELLED）
- 采购订单：120 张，总金额约 900 万元
- 订单状态：RECEIVED 72、CLOSED 15、APPROVED 12、DRAFT 8、PARTIALLY_RECEIVED 8、CANCELLED 5
- 收货单：95 张
- 领料/出库：48 张
- 库存流水：401 条
- 库存余额：33 条（多仓库、批次/序列号）
- 合同：13 份（3 份既有 + 8 份演示历史合同）
- 价格历史：每个核心物料有 6~12 个月的多点成交价，可直接用于价格趋势

## 使用方式

### 方式一：一键应用

```powershell
.\sql\apply_demo_data.ps1
```

### 方式二：手动执行

```powershell
Get-Content .\sql\demo_business_data.sql -Raw -Encoding UTF8 |
  docker exec -i procuremind-mysql-1 mysql -uroot -proot123456 --default-character-set=utf8mb4 procuremind
```

## 清库后复现

```powershell
# 1. 清库，重建主数据 + 知识库，并自动导入业务演示数据
.\scripts\reset_demo.ps1 -Agent -WithBusiness

# 2. 如果只清了业务表，也可以单独重新导入
.\sql\apply_demo_data.ps1
```

> 脚本会清理 `pr_*`、`po_*`、`inv_*` 以及演示合同（id 9001~9008），保留主数据、用户、权限、知识库和既有 3 份合同。
> 可以重复执行，不会产生重复订单。

## 设计说明

- 订单金额按物料真实价格区间生成，并带月度价格漂移；
- 订单状态与时间匹配：近期为 DRAFT/APPROVED，历史为 RECEIVED/CLOSED；
- 收货、领料、批次库存、序列号、库存台账由同一份数据生成，库存不会为负；
- 不使用 QA/P1 测试前缀，名称、用途、部门、供应商均按工业物资场景生成；
- 不包含 Flowable 待办任务：历史订单直接使用终态；需要审批演示时，请通过页面新建申请/订单。
