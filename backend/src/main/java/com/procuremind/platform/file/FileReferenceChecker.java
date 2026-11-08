package com.procuremind.platform.file;

/**
 * 文件引用保护扩展点。
 *
 * <p>其他业务模块（如知识库、合同库）实现该接口后，文件服务在删除对象前会先询问所有实现，
 * 避免出现“文件被删、业务记录悬空”的情况。文件模块不认识任何具体业务模块。</p>
 */
public interface FileReferenceChecker {
    /** 该文件是否仍被业务数据引用（含历史版本）。 */
    boolean isReferenced(Long fileId);

    /** 引用来源的可读名称，用于错误提示，例如“知识库文档”。 */
    default String referenceName() { return "业务数据"; }
}
