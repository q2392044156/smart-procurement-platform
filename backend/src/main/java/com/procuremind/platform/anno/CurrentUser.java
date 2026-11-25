package com.procuremind.platform.anno;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.io.Serializable;
import java.util.Set;

/**
 * 认证通过后放进 SecurityContext 的当前用户快照（record + Serializable，便于随会话序列化）。
 * 由 UserDirectory.current(id) 逐请求查库构造，因此角色与权限变更在下一次请求即生效；
 * 只承载身份与授权结果，不含密码哈希等敏感字段。
 *
 * <p>两个 ID 显式声明成字符串输出：`/auth/me` 直接把它序列化给前端，而全站 ID 口径是字符串
 * （见 JacksonConfig 的注释与 ApiContractTest 的快照）。内部仍保持 Long，方便与库里的自增主键比较。</p>
 */
public record CurrentUser(@JsonSerialize(using = ToStringSerializer.class) Long id,
                          String username,
                          String displayName,
                          @JsonSerialize(using = ToStringSerializer.class) Long departmentId,
                          Set<String> roles,
                          Set<String> permissions) implements Serializable {}
