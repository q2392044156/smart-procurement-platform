package com.procuremind.platform.service;

import com.procuremind.platform.exception.BusinessException;
import com.procuremind.platform.mapper.FileMapper;
import com.procuremind.platform.pojo.FilePresignRequest;
import com.procuremind.platform.utils.SecurityUtils;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FileStorageService {
    private final MinioClient minio; private final MinioClient publicMinio; private final FileMapper mapper;
    private final BusinessAttachmentService attachments; private final String bucket;
    private final List<FileReferenceChecker> referenceCheckers;
    public FileStorageService(MinioClient minio,@Qualifier("publicMinioClient") MinioClient publicMinio,FileMapper mapper,
                              BusinessAttachmentService attachments,@Value("${procuremind.minio.bucket}") String bucket,
                              List<FileReferenceChecker> referenceCheckers){
        this.minio=minio;this.publicMinio=publicMinio;this.mapper=mapper;this.attachments=attachments;this.bucket=bucket;
        this.referenceCheckers=referenceCheckers;
    }

    public Map<String,Object> presign(FilePresignRequest request){
        return presignUpload(request.fileName(),request.contentType(),request.size());
    }

    /**
     * 通用上传预签名。业务模块（如知识库）可以复用同一套对象存储与文件元数据，
     * 只需在自己的接口层声明允许的内容类型。
     */
    public Map<String,Object> presignUpload(String fileName,String contentType,Long size){try{
        ensureBucket();String key="uploads/"+LocalDate.now()+"/"+UUID.randomUUID()+"-"+safe(fileName);
        String url=publicMinio.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.PUT).bucket(bucket).object(key).expiry(900).build());
        mapper.insertMetadata(fileName,key,contentType,size,bucket,SecurityUtils.currentUser().id());
        Long id=mapper.findIdByObjectKey(key);
        return Map.of("fileId",id,"objectKey",key,"uploadUrl",url,"expiresIn",900);
    }catch(Exception e){throw unavailable();}}

    /** 仅生成下载地址，不判定权限；调用方必须先完成自己的业务权限校验。 */
    public Map<String,Object> presignRead(Long id){try{
        Map<String,Object> file=mapper.findForRead(id);
        if(file==null)throw new BusinessException(HttpStatus.NOT_FOUND,"文件不存在");
        return Map.of("fileName",file.get("original_name"),"downloadUrl",presignGet(file), "expiresIn",900);
    }catch(BusinessException e){throw e;}
     catch(Exception e){throw unavailable();}}

    public Map<String,Object> download(Long id){try{        Map<String,Object> file=mapper.findForDownload(id);
        if(file==null)throw new BusinessException(HttpStatus.NOT_FOUND,"文件不存在");
        var user=SecurityUtils.currentUser();
        boolean supplier=user.permissions().contains("supplier:view")&&mapper.countSupplierQualification(id)>0;
        boolean business=attachments.canReadFile(id);
        if(!user.id().toString().equals(file.get("uploader_id").toString())&&!user.roles().contains("ADMIN")&&!user.roles().contains("AUDITOR")&&!supplier&&!business)throw new BusinessException(HttpStatus.FORBIDDEN,"无权访问该文件");
        return Map.of("fileName",file.get("original_name"),"downloadUrl",presignGet(file),"expiresIn",900);
    }catch(BusinessException e){throw e;}catch(Exception e){throw unavailable();}}

    /** 内部服务流式读取文件内容，绕过预签名 URL 的 host 限制。 */
    public Map<String,Object> raw(Long id){try{
        Map<String,Object> file=mapper.findForRaw(id);
        if(file==null)throw new BusinessException(HttpStatus.NOT_FOUND,"文件不存在");
        try(var stream=minio.getObject(GetObjectArgs.builder().bucket(file.get("bucket").toString()).object(file.get("object_key").toString()).build())){
            return Map.of("fileName",file.get("original_name"),
                    "contentType",file.get("content_type")==null?"application/octet-stream":file.get("content_type"),
                    "bytes",stream.readAllBytes());
        }
    }catch(BusinessException e){throw e;}
     catch(Exception e){throw unavailable();}}

    public void delete(Long id){try{
        Map<String,Object> file=mapper.findForDelete(id);
        if(file==null)throw new BusinessException(HttpStatus.NOT_FOUND,"文件不存在");
        var user=SecurityUtils.currentUser();
        if(!user.id().toString().equals(file.get("uploader_id").toString())&&!user.roles().contains("ADMIN"))throw new BusinessException(HttpStatus.FORBIDDEN,"只能删除本人上传的文件");
        if(mapper.countSupplierQualification(id)>0||attachments.isFileLinked(id))throw new BusinessException(HttpStatus.CONFLICT,"文件已关联业务单据，不能删除");
        for(FileReferenceChecker checker:referenceCheckers){
            if(checker.isReferenced(id))throw new BusinessException(HttpStatus.CONFLICT,"文件已被"+checker.referenceName()+"引用，不能删除");
        }
        minio.removeObject(RemoveObjectArgs.builder().bucket(file.get("bucket").toString()).object(file.get("object_key").toString()).build());
        mapper.softDelete(id);
    }catch(BusinessException e){throw e;}catch(Exception e){throw unavailable();}}

    private String presignGet(Map<String,Object> file)throws Exception{
        return publicMinio.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.GET).bucket(file.get("bucket").toString()).object(file.get("object_key").toString()).expiry(900).build());
    }
    private void ensureBucket()throws Exception{if(!minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()))minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());}
    private String safe(String name){return name==null?"file":name.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]","_");}
    private BusinessException unavailable(){return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"文件服务不可用");}
}
