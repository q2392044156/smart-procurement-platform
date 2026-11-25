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

/**
 * 文件域的存储服务：负责 MinIO 对象的预签名上传/下载、流式读取、删除，以及 file 元数据的落库与软删。
 *
 * <p>两个客户端分工：{@code minio} 走内网做对象读写与建桶，{@code publicMinioClient} 只用于生成对外的
 * 预签名 URL（host 需与前端可达地址一致）。依赖 FileMapper 存元数据、BusinessAttachmentService 判断
 * 业务附件关联、全部 FileReferenceChecker 判断外部引用。
 *
 * <p>边界：权限只做到“上传者/管理员/审计员/供应商资质/附件可读”这一层，更细的业务权限由调用方先判；
 * 不解析文件内容（见 FileContentValidator），也不维护附件与单据的绑定关系（见 BusinessAttachmentService）。
 * MinIO 异常统一折叠成 503，数据库异常原样抛出。</p>
 */
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

    /** 预签名上传入口：只把请求体拆成三个参数转调，大小与类型白名单由接口层注解完成。 */
    public Map<String,Object> presign(FilePresignRequest request){
        return presignUpload(request.fileName(),request.contentType(),request.size());
    }

    /**
     * 通用上传预签名。业务模块（如知识库）可以复用同一套对象存储与文件元数据，
     * 只需在自己的接口层声明允许的内容类型。
     *
     * 对象 key 固定为 uploads/日期/uuid-安全文件名，天然按天分目录且不重名；先写元数据再按 objectKey
     * 反查 fileId（insert 不回填主键），因此返回的 fileId 立即可用于后续业务关联。
     * URL 有效期 900 秒，本方法不校验浏览器是否真的把文件传完，落库时 size 只是声明值。
     */
    public Map<String,Object> presignUpload(String fileName,String contentType,Long size){try{
        ensureBucket();String key="uploads/"+LocalDate.now()+"/"+UUID.randomUUID()+"-"+safe(fileName);
        String url=publicMinio.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.PUT).bucket(bucket).object(key).expiry(900).build());
        mapper.insertMetadata(fileName,key,contentType,size,bucket,SecurityUtils.currentUser().id());
        Long id=mapper.findIdByObjectKey(key);
        return Map.of("fileId",id,"objectKey",key,"uploadUrl",url,"expiresIn",900);
    }catch(Exception e){throw unavailable();}}

    /**
     * 仅生成下载地址，不判定权限；调用方必须先完成自己的业务权限校验。
     * 元数据不存在按 404 处理，链接有效期 900 秒。
     */
    public Map<String,Object> presignRead(Long id){try{
        Map<String,Object> file=mapper.findForRead(id);
        if(file==null)throw new BusinessException(HttpStatus.NOT_FOUND,"文件不存在");
        return Map.of("fileName",file.get("original_name"),"downloadUrl",presignGet(file), "expiresIn",900);
    }catch(BusinessException e){throw e;}
     catch(Exception e){throw unavailable();}}

    /**
     * 生成下载地址，放行条件是五选一：上传者本人 / ADMIN / AUDITOR / 供应商资质附件 / 业务附件可读。
     * 供应商分支要求 supplier:view 权限与“该文件确为某供应商资质”同时成立，避免凭一个权限码读他人资质。
     */
    public Map<String,Object> download(Long id){try{        Map<String,Object> file=mapper.findForDownload(id);
        if(file==null)throw new BusinessException(HttpStatus.NOT_FOUND,"文件不存在");
        var user=SecurityUtils.currentUser();
        boolean supplier=user.permissions().contains("supplier:view")&&mapper.countSupplierQualification(id)>0;
        boolean business=attachments.canReadFile(id);
        if(!user.id().toString().equals(file.get("uploader_id").toString())&&!user.roles().contains("ADMIN")&&!user.roles().contains("AUDITOR")&&!supplier&&!business)throw new BusinessException(HttpStatus.FORBIDDEN,"无权访问该文件");
        return Map.of("fileName",file.get("original_name"),"downloadUrl",presignGet(file),"expiresIn",900);
    }catch(BusinessException e){throw e;}catch(Exception e){throw unavailable();}}

    /**
     * 内部服务流式读取文件内容，绕过预签名 URL 的 host 限制。
     * 整块读入 byte[] 后随返回值带出，只适合中小文件；content_type 为空时回落 octet-stream 保证响应可解析。
     */
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

    /**
     * 删除顺序固定：归属校验 → 业务附件/供应商资质校验 → 扩展点引用校验 → 删 MinIO 对象 → 软删元数据。
     * 先删对象再软删记录：对象删除失败会直接抛异常、记录仍在可重试；反过来会留下永远删不掉的无主对象。
     * MinIO 与数据库没有分布式事务，只能靠“先对象后记录”的顺序把不可恢复的中间态降到最小。
     */
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

    /** 统一用公网客户端签 900 秒的 GET 地址，桶与 key 一律取自元数据，不接受调用方传入。 */
    private String presignGet(Map<String,Object> file)throws Exception{
        return publicMinio.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.GET).bucket(file.get("bucket").toString()).object(file.get("object_key").toString()).expiry(900).build());
    }
    /**
     * 桶不存在才创建，属幂等的懒初始化，省去部署时的初始化脚本；
     * 并发首次上传可能同时判定为不存在，后到者会收到“桶已存在”的错误并落到 503，属可接受的低概率抖动。
     */
    private void ensureBucket()throws Exception{if(!minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()))minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());}
    /** 文件名白名单替换：非字母/数字/点/下划线/横线/中文一律换成下划线，杜绝 "../" 等路径字符进入 objectKey。 */
    private String safe(String name){return name==null?"file":name.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]","_");}
    /** 统一把 MinIO SDK 的受检异常折叠成 503，避免每个方法重复 catch 并把底层错误细节泄露给前端。 */
    private BusinessException unavailable(){return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"文件服务不可用");}
}
