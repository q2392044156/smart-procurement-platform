package com.procuremind.platform.service;

/**
 * 文件引用保护扩展点。
 *
 * <p>其他业务模块（如知识库、合同库）实现该接口后，文件服务在删除对象前会先询问所有实现，
 * 避免出现“文件被删、业务记录悬空”的情况。文件模块不认识任何具体业务模块。</p>
 *
 * <p>Spring 会把全部实现以 {@code List<FileReferenceChecker>} 注入 FileStorageService，新增业务只需加一个
 * 实现类，无需改动文件模块。实现类只应依赖 Mapper 做计数查询，不能反向依赖文件服务或自身业务服务，
 * 否则会形成 Bean 循环依赖。本接口只回答“还能不能删”，不负责解绑引用、不清理历史版本。</p>
 */
public interface FileReferenceChecker {
    /**
     * 该文件是否仍被业务数据引用（含历史版本）。
     * 历史版本同样指向同一个 fileId，实现漏算历史版本会导致旧版附件被提前删除。
     */
    boolean isReferenced(Long fileId);

    /** 引用来源的可读名称，只用于拼接删除失败提示，例如“知识库文档”；不参与是否可删的判断。 */
    default String referenceName() { return "业务数据"; }
}
