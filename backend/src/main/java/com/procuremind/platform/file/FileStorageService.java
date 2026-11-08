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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FileStorageService {
    private final MinioClient minio; private final MinioClient publicMinio; private final JdbcTemplate jdbc;
    private final BusinessAttachmentService attachments; private final String bucket;
    private final List<FileReferenceChecker> referenceCheckers;
    public FileStorageService(MinioClient minio,@Qualifier("publicMinioClient") MinioClient publicMinio,JdbcTemplate jdbc,
                              BusinessAttachmentService attachments,@Value("${procuremind.minio.bucket}") String bucket,
                              List<FileReferenceChecker> referenceCheckers){
        this.minio=minio;this.publicMinio=publicMinio;this.jdbc=jdbc;this.attachments=attachments;this.bucket=bucket;
        this.referenceCheckers=referenceCheckers;
    }

    public Map<String,Object> presign(FileController.PresignRequest request){
        return presignUpload(request.fileName(),request.contentType(),request.size());
    }

    /**
     * 通用上传预签名。业务模块（如知识库）可以复用同一套对象存储与文件元数据，
     * 只需在自己的接口层声明允许的内容类型。
     */
    public Map<String,Object> presignUpload(String fileName,String contentType,Long size){try{
        ensureBucket();String key="uploads/"+LocalDate.now()+"/"+UUID.randomUUID()+"-"+safe(fileName);
        String url=publicMinio.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.PUT).bucket(bucket).object(key).expiry(900).build());
        jdbc.update("insert into fs_file_metadata(original_name,object_key,content_type,size_bytes,bucket,uploader_id) values(?,?,?,?,?,?)",fileName,key,contentType,size,bucket,SecurityUtils.currentUser().id());
        Long id=jdbc.queryForObject("select id from fs_file_metadata where object_key=?",Long.class,key);
        return Map.of("fileId",id,"objectKey",key,"uploadUrl",url,"expiresIn",900);
    }catch(Exception e){throw unavailable();}}

    /** 仅生成下载地址，不判定权限；调用方必须先完成自己的业务权限校验。 */
    public Map<String,Object> presignRead(Long id){try{
        Map<String,Object> file=jdbc.queryForMap("select original_name,object_key,bucket from fs_file_metadata where id=? and deleted=0",id);
        return Map.of("fileName",file.get("original_name"),"downloadUrl",presignGet(file), "expiresIn",900);
    }catch(org.springframework.dao.EmptyResultDataAccessException e){throw new BusinessException(HttpStatus.NOT_FOUND,"文件不存在");}
     catch(Exception e){throw unavailable();}}

    public Map<String,Object> download(Long id){try{
        Map<String,Object> file=jdbc.queryForMap("select original_name,object_key,bucket,uploader_id from fs_file_metadata where id=? and deleted=0",id);var user=SecurityUtils.currentUser();
        boolean supplier=user.permissions().contains("supplier:view")&&count("select count(*) from sp_supplier_qualification where file_id=? and deleted=0",id)>0;
        boolean business=attachments.canReadFile(id);
        if(!user.id().toString().equals(file.get("uploader_id").toString())&&!user.roles().contains("ADMIN")&&!user.roles().contains("AUDITOR")&&!supplier&&!business)throw new BusinessException(HttpStatus.FORBIDDEN,"无权访问该文件");
        return Map.of("fileName",file.get("original_name"),"downloadUrl",presignGet(file),"expiresIn",900);
    }catch(BusinessException e){throw e;}catch(org.springframework.dao.EmptyResultDataAccessException e){throw e;}catch(Exception e){throw unavailable();}}

    public void delete(Long id){try{
        Map<String,Object> file=jdbc.queryForMap("select object_key,bucket,uploader_id from fs_file_metadata where id=? and deleted=0",id);var user=SecurityUtils.currentUser();
        if(!user.id().toString().equals(file.get("uploader_id").toString())&&!user.roles().contains("ADMIN"))throw new BusinessException(HttpStatus.FORBIDDEN,"只能删除本人上传的文件");
        if(count("select count(*) from sp_supplier_qualification where file_id=? and deleted=0",id)>0||attachments.isFileLinked(id))throw new BusinessException(HttpStatus.CONFLICT,"文件已关联业务单据，不能删除");
        for(FileReferenceChecker checker:referenceCheckers){
            if(checker.isReferenced(id))throw new BusinessException(HttpStatus.CONFLICT,"文件已被"+checker.referenceName()+"引用，不能删除");
        }
        minio.removeObject(RemoveObjectArgs.builder().bucket(file.get("bucket").toString()).object(file.get("object_key").toString()).build());
        jdbc.update("update fs_file_metadata set deleted=1 where id=?",id);
    }catch(BusinessException e){throw e;}catch(org.springframework.dao.EmptyResultDataAccessException e){throw e;}catch(Exception e){throw unavailable();}}

    private String presignGet(Map<String,Object> file)throws Exception{
        return publicMinio.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.GET).bucket(file.get("bucket").toString()).object(file.get("object_key").toString()).expiry(900).build());
    }
    private void ensureBucket()throws Exception{if(!minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()))minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());}
    private String safe(String name){return name==null?"file":name.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]","_");}
    private long count(String sql,Object...args){Long value=jdbc.queryForObject(sql,Long.class,args);return value==null?0:value;}
    private BusinessException unavailable(){return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"文件服务不可用");}
}
