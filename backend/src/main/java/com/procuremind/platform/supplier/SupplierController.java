package com.procuremind.platform.supplier;

import com.procuremind.platform.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierController {
    private final JdbcTemplate jdbc;
    public SupplierController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @GetMapping public ApiResponse<List<Map<String,Object>>> list(@RequestParam(required=false) String keyword){String like="%"+(keyword==null?"":keyword)+"%";return ApiResponse.ok(jdbc.queryForList("select cast(id as char) id,code,name,unified_credit_code unifiedCreditCode,level,status,contact_name contactName,contact_phone contactPhone,email,address,score from sp_supplier where deleted=0 and (code like ? or name like ?) order by score desc,id",like,like));}
    @GetMapping("/{id}") public ApiResponse<Map<String,Object>> get(@PathVariable Long id){Map<String,Object> data=jdbc.queryForMap("select cast(id as char) id,code,name,unified_credit_code unifiedCreditCode,level,status,contact_name contactName,contact_phone contactPhone,email,address,score from sp_supplier where id=? and deleted=0",id);data.put("qualifications",jdbc.queryForList("select cast(id as char) id,name,cast(file_id as char) fileId,valid_from validFrom,valid_until validUntil,status from sp_supplier_qualification where supplier_id=?",id));data.put("evaluations",jdbc.queryForList("select cast(id as char) id,period,quality_score qualityScore,delivery_score deliveryScore,service_score serviceScore,total_score totalScore,comment from sp_supplier_evaluation where supplier_id=? order by period desc",id));return ApiResponse.ok(data);}
    @PostMapping @PreAuthorize("hasAuthority('supplier:edit')") public ApiResponse<Map<String,Object>> create(@Valid @RequestBody SupplierRequest r){jdbc.update("insert into sp_supplier(code,name,unified_credit_code,level,status,contact_name,contact_phone,email,address) values(?,?,?,?,?,?,?,?,?)",r.code(),r.name(),r.unifiedCreditCode(),r.level(),r.status(),r.contactName(),r.contactPhone(),r.email(),r.address());return ApiResponse.ok(Map.of("id",jdbc.queryForObject("select id from sp_supplier where code=?",Long.class,r.code())));}
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('supplier:edit')") public ApiResponse<Void> update(@PathVariable Long id,@Valid @RequestBody SupplierRequest r){jdbc.update("update sp_supplier set name=?,unified_credit_code=?,level=?,status=?,contact_name=?,contact_phone=?,email=?,address=? where id=? and deleted=0",r.name(),r.unifiedCreditCode(),r.level(),r.status(),r.contactName(),r.contactPhone(),r.email(),r.address(),id);return ApiResponse.ok(null);}
    public record SupplierRequest(@NotBlank String code,@NotBlank String name,String unifiedCreditCode,@NotBlank String level,@NotBlank String status,String contactName,String contactPhone,String email,String address){}
}
