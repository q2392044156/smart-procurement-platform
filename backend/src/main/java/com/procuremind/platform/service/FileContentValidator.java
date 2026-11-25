package com.procuremind.platform.service;

import com.procuremind.platform.exception.BusinessException;
import com.procuremind.platform.mapper.FileMapper;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;

/**
 * 上传文件内容校验：读取对象头，用魔数比对声明的 Content-Type，防止“改后缀/改 MIME”绕过类型限制。
 *
 * <p>对象由浏览器直传 MinIO，服务端在“关联业务”时回读前 16 字节做校验，失败返回 400。
 * 只判断“声明类型与真实字节是否一致”，不判断文件是否安全：病毒扫描、宏检测与内容脱敏不在本组件范围内。
 * 依赖 MinIO（按 offset/length 做范围读取，不整文件下载）与 FileMapper（取 object_key 与声明类型）；
 * MinIO 不可用时转 503，避免把基础设施故障误报成用户文件的问题。</p>
 */
@Component
public class FileContentValidator {
    /** 只需覆盖最长魔数（OLE2 为 4 字节），16 字节留余量；纯文本类不依赖文件头。 */
    private static final int HEAD_BYTES = 16;

    private final MinioClient minio;
    private final FileMapper mapper;

    public FileContentValidator(MinioClient minio, FileMapper mapper) {
        this.minio = minio;
        this.mapper = mapper;
    }

    /**
     * 在文件“关联到业务单据”前调用：每次回读对象头，不做结果缓存，防止文件被替换后仍通过校验。
     * 元数据缺失按 400 处理（对调用方是入参问题），存储层异常按 503 处理，两类故障不混用状态码。
     */
    public void verify(Long fileId) {
        Map<String, Object> file = mapper.findForValidation(fileId);
        if (file == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "文件不存在");
        String contentType = String.valueOf(file.get("content_type"));
        byte[] head = readHead(file);
        if (!matches(contentType, head)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文件内容与声明类型不符，请重新上传");
        }
    }

    /** 用 offset/length 只取对象头部，避免为了校验一个几个字就把整个大文件拉进内存。 */
    private byte[] readHead(Map<String, Object> file) {
        try (InputStream stream = minio.getObject(GetObjectArgs.builder()
                .bucket(String.valueOf(file.get("bucket")))
                .object(String.valueOf(file.get("object_key")))
                .offset(0L)
                .length((long) HEAD_BYTES)
                .build())) {
            return stream.readNBytes(HEAD_BYTES);
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "文件服务不可用，无法校验上传内容");
        }
    }

    /**
     * 按声明的 Content-Type 校验文件头；无魔数的纯文本类型直接放行。
     * 白名单是封闭的：未列出的类型一律 false（默认拒绝），新增类型必须同时补魔数，不能只放宽声明。
     */
    public static boolean matches(String contentType, byte[] head) {
        if (contentType == null || head == null) return false;
        String type = contentType.toLowerCase();
        return switch (type) {
            case "application/pdf" -> startsWith(head, 0x25, 0x50, 0x44, 0x46);                       // %PDF
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                 "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                 "application/vnd.openxmlformats-officedocument.presentationml.presentation" ->
                    startsWith(head, 0x50, 0x4B, 0x03, 0x04);                                            // PK..
            case "application/msword", "application/vnd.ms-excel", "application/vnd.ms-powerpoint" ->
                    startsWith(head, 0xD0, 0xCF, 0x11, 0xE0);                                            // OLE2
            case "image/jpeg" -> startsWith(head, 0xFF, 0xD8, 0xFF);
            case "image/png" -> startsWith(head, 0x89, 0x50, 0x4E, 0x47);
            case "text/plain", "text/markdown", "application/octet-stream" -> true;
            default -> false;
        };
    }

    /** 与 0xFF 掩码比较：Java 的 byte 有符号，直接和 int 比会把 0xD0 这类字节当负数而永远不相等。 */
    private static boolean startsWith(byte[] head, int... magic) {
        if (head.length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if ((head[i] & 0xFF) != magic[i]) return false;
        }
        return true;
    }
}
