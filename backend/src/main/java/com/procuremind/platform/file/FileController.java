package com.procuremind.platform.file;

import com.procuremind.platform.common.ApiResponse;
import com.procuremind.platform.common.BusinessException;
import com.procuremind.platform.system.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.minio.*;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/files")
public class FileController {
    private final MinioClient minio;private final JdbcTemplate jdbc;private final String bucket;
    public FileController(MinioClient minio,JdbcTemplate jdbc,@Value("${procuremind.minio.bucket}")String bucket){this.minio=minio;this.jdbc=jdbc;this.bucket=bucket;}
    @PostMapping("/presign") public ApiResponse<Map<String,Object>> presign(@Valid @RequestBody PresignRequest r){try{ensureBucket();String key="uploads/"+java.time.LocalDate.now()+"/"+UUID.randomUUID()+"-"+safe(r.fileName());String url=minio.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.PUT).bucket(bucket).object(key).expiry(900).build());jdbc.update("insert into fs_file_metadata(original_name,object_key,content_type,size_bytes,bucket,uploader_id) values(?,?,?,?,?,?)",r.fileName(),key,r.contentType(),r.size(),bucket,SecurityUtils.currentUser().id());Long id=jdbc.queryForObject("select id from fs_file_metadata where object_key=?",Long.class,key);return ApiResponse.ok(Map.of("fileId",id,"objectKey",key,"uploadUrl",url,"expiresIn",900));}catch(Exception e){throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"文件服务不可用");}}
    @GetMapping("/{id}") public ApiResponse<Map<String,Object>> download(@PathVariable Long id){try{Map<String,Object> f=jdbc.queryForMap("select original_name,object_key,bucket,uploader_id from fs_file_metadata where id=? and deleted=0",id);var user=SecurityUtils.currentUser();if(!user.id().toString().equals(f.get("uploader_id").toString())&&!user.roles().contains("ADMIN")&&!user.roles().contains("AUDITOR"))throw new BusinessException(HttpStatus.FORBIDDEN,"无权访问该文件");String url=minio.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.GET).bucket(f.get("bucket").toString()).object(f.get("object_key").toString()).expiry(900).build());return ApiResponse.ok(Map.of("fileName",f.get("original_name"),"downloadUrl",url,"expiresIn",900));}catch(BusinessException e){throw e;}catch(org.springframework.dao.EmptyResultDataAccessException e){throw e;}catch(Exception e){throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"文件服务不可用");}}
    private void ensureBucket()throws Exception{if(!minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()))minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());}
    private String safe(String name){return name==null?"file":name.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]","_");}
    public record PresignRequest(@NotBlank @Size(max=255) String fileName,@NotBlank @Pattern(regexp="application/pdf|image/jpeg|image/png|application/octet-stream") String contentType,@NotNull @Min(1) @Max(52428800) Long size){}
}
