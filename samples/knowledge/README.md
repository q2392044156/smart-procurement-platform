# 知识库示例文档

本目录存放知识库功能的演示与验收文档，均为虚构内容，仅用于本地演示。

| 文件 | 说明 |
|---|---|
| `internal-procurement-policy.docx` | 《采购管理制度》V1.2，覆盖申请、审批权限、供应商、订单、收货与档案要求 |
| `supplier-admission-policy.pdf` | 《供应商准入与绩效评价管理办法》V2.0，含资质要求与评分权重表 |

## 重新生成

使用 DSH 自带 Python（已包含 `python-docx`），并依赖 LibreOffice Kit 导出 PDF：

```powershell
& 'C:\Users\Administrator\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe' .\samples\knowledge\generate_samples.py
```

脚本会先写 DOCX，再导出 PDF，并删除 PDF 的中间 DOCX，保证目录内只有「一份 DOCX + 一份 PDF」。

## 验收

`scripts/knowledge-base-smoke.py` 会自动上传这两份文档，验证上传、创建、发布、部门/角色可见性、版本、归档、删除、下载、引用保护与审计。
