package com.procuremind.platform.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * 统一响应里的数值口径（与 {@code ApiContractTest} 的快照互为守门）：
 *
 * <ul>
 *   <li>{@link BigDecimal} —— 金额、数量、单价、税率一律写成<b>字符串</b>，避免 JS 双精度丢分位；
 *       前端用 {@code formatQty}/{@code formatTime} 或直接展示，不参与算术。</li>
 *   <li>{@code Long} —— 保持 JSON <b>数字</b>，但只允许出现在计数、时长、天数这类「安全整数」上；
 *       所有 ID 必须在 SQL 里 {@code cast(... as char)} 成字符串再返回，不要让自增主键以 Long 形态漏到契约里。</li>
 * </ul>
 *
 * <p>历史背景：这里曾经把 {@code Long} 也写成字符串，本意是保护 snowflake ID 的精度，
 * 结果让「计数」跟着变成了 {@code "21"}，并且 {@code timestampdiff}/{@code datediff} 这类列在
 * 不同驱动版本下时而是 Long、时而是 Integer —— 同一个字段忽字符串忽数字。
 * 现在把 Long 收敛成数字、把 ID 交给 SQL 显式转换，边界就只剩一条：<b>能进 JS 安全整数范围的用数字，
 * 金额与 ID 用字符串</b>。改这条约定前先看 {@code ApiContractTest} 会不会红。</p>
 */
@Configuration
public class JacksonConfig {
    @Bean Module precisionSafeModule() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(BigDecimal.class, ToStringSerializer.instance);
        return module;
    }
}
