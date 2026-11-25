package com.procuremind.platform.service;

import com.procuremind.platform.mapper.AuditMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 操作审计的只读查询口：只暴露最近 500 条审计记录，供系统管理页展示。
 *
 * <p>写入不在本类：{@code AuditAspect} 环绕各 Controller 的写请求，在 finally 里落库，所以这里没有新增接口
 * 是刻意的。查询按 id 倒序、不分页，也不做数据范围过滤——能看到全量是 {@code audit:view} 权限点的语义，
 * 由控制层把关，不在 SQL 里再收敛。</p>
 *
 * <p>依赖：AuditMapper（唯一数据来源，对应 sys_operation_audit 表）。</p>
 */
@Service
public class AuditService {
    private final AuditMapper mapper;
    public AuditService(AuditMapper mapper){this.mapper=mapper;}
    /** 固定取最近 500 条；id 与 userId 在 SQL 里已转成字符串，避免前端 Long 精度丢失。 */
    public List<Map<String,Object>> list(){return mapper.list();}
}
