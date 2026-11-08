package com.procuremind.platform.file;

import com.procuremind.platform.common.BusinessException;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;

/**
 * 上传文件内容校验：读取对象头，用魔数比对声明的 Content-Type，防止“改后缀/改 MIME”绕过类型限制。
 *
 * <p>对象由浏览器直传 MinIO，服务端在“关联业务”时回读前 16 字节做校验，失败返回 400。
 * 病毒扫描与内容脱敏不在本组件范围内。</p>
 */
@Component
public class FileContentValidator {
    private static final int HEAD_BYTES = 16;

    private final MinioClient minio;
    private final JdbcTemplate jdbc;

    public FileContentValidator(MinioClient minio, JdbcTemplate jdbc) {
        this.minio = minio;
        this.jdbc = jdbc;
    }

    public void verify(Long fileId) {
        Map<String, Object> file = jdbc.queryForMap(
                "select object_key,content_type,bucket,size_bytes from fs_file_metadata where id=? and deleted=0", fileId);
        String contentType = String.valueOf(file.get("content_type"));
        byte[] head = readHead(file);
        if (!matches(contentType, head)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文件内容与声明类型不符，请重新上传");
        }
    }

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

    /** 按声明的 Content-Type 校验文件头；无魔数的纯文本类型直接放行。 */
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

    private static boolean startsWith(byte[] head, int... magic) {
        if (head.length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if ((head[i] & 0xFF) != magic[i]) return false;
        }
        return true;
    }
}
