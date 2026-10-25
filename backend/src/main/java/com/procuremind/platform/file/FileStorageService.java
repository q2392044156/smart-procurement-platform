package com.procuremind.platform.file;

import com.procuremind.platform.common.BusinessException;
import com.procuremind.platform.system.SecurityUtils;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Service
public class FileStorageService {
    private final MinioClient minio; private final MinioClient publicMinio; private final JdbcTemplate jdbc;
    private final BusinessAttachmentService attachments; private final String bucket;
    public FileStorageService(MinioClient minio,@Qualifier("publicMinioClient") MinioClient publicMinio,JdbcTemplate jdbc,
                              BusinessAttachmentService attachments,@Value("${procuremind.minio.bucket}") String bucket){
        this.minio=minio;this.publicMinio=publicMinio;this.jdbc=jdbc;this.attachments=attachments;this.bucket=bucket;
    }

    public Map<String,Object> presign(FileController.PresignRequest request){try{
        ensureBucket();String key="uploads/"+LocalDate.now()+"/"+UUID.randomUUID()+"-"+safe(request.fileName());
        String url=publicMinio.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.PUT).bucket(bucket).object(key).expiry(900).build());
        jdbc.update("insert into fs_file_metadata(original_name,object_key,content_type,size_bytes,bucket,uploader_id) values(?,?,?,?,?,?)",request.fileName(),key,request.contentType(),request.size(),bucket,SecurityUtils.currentUser().id());
        Long id=jdbc.queryForObject("select id from fs_file_metadata where object_key=?",Long.class,key);
        return Map.of("fileId",id,"objectKey",key,"uploadUrl",url,"expiresIn",900);
    }catch(Exception e){throw unavailable();}}

    public Map<String,Object> download(Long id){try{
        Map<String,Object> file=jdbc.queryForMap("select original_name,object_key,bucket,uploader_id from fs_file_metadata where id=? and deleted=0",id);var user=SecurityUtils.currentUser();
        boolean supplier=user.permissions().contains("supplier:view")&&count("select count(*) from sp_supplier_qualification where file_id=? and deleted=0",id)>0;
        boolean business=attachments.canReadFile(id);
        if(!user.id().toString().equals(file.get("uploader_id").toString())&&!user.roles().contains("ADMIN")&&!user.roles().contains("AUDITOR")&&!supplier&&!business)throw new BusinessException(HttpStatus.FORBIDDEN,"无权访问该文件");
        String url=publicMinio.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.GET).bucket(file.get("bucket").toString()).object(file.get("object_key").toString()).expiry(900).build());
        return Map.of("fileName",file.get("original_name"),"downloadUrl",url,"expiresIn",900);
    }catch(BusinessException e){throw e;}catch(org.springframework.dao.EmptyResultDataAccessException e){throw e;}catch(Exception e){throw unavailable();}}

    public void delete(Long id){try{
        Map<String,Object> file=jdbc.queryForMap("select object_key,bucket,uploader_id from fs_file_metadata where id=? and deleted=0",id);var user=SecurityUtils.currentUser();
        if(!user.id().toString().equals(file.get("uploader_id").toString())&&!user.roles().contains("ADMIN"))throw new BusinessException(HttpStatus.FORBIDDEN,"只能删除本人上传的文件");
        if(count("select count(*) from sp_supplier_qualification where file_id=? and deleted=0",id)>0||attachments.isFileLinked(id))throw new BusinessException(HttpStatus.CONFLICT,"文件已关联业务单据，不能删除");
        minio.removeObject(RemoveObjectArgs.builder().bucket(file.get("bucket").toString()).object(file.get("object_key").toString()).build());
        jdbc.update("update fs_file_metadata set deleted=1 where id=?",id);
    }catch(BusinessException e){throw e;}catch(org.springframework.dao.EmptyResultDataAccessException e){throw e;}catch(Exception e){throw unavailable();}}

    private void ensureBucket()throws Exception{if(!minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()))minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());}
    private String safe(String name){return name==null?"file":name.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]","_");}
    private long count(String sql,Object...args){Long value=jdbc.queryForObject(sql,Long.class,args);return value==null?0:value;}
    private BusinessException unavailable(){return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"文件服务不可用");}
}
