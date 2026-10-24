package com.procuremind.platform.masterdata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.procuremind.platform.common.ApiResponse;
import com.procuremind.platform.common.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAuthority('masterdata:view')")
public class MasterDataController {
    private final JdbcTemplate jdbc; private final ObjectMapper mapper;
    public MasterDataController(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc=jdbc; this.mapper=mapper; }

    @GetMapping("/material-categories")
    public ApiResponse<List<Map<String,Object>>> categories() {
        var rows=jdbc.queryForList("select cast(id as char) id,cast(parent_id as char) parentId,code,name,description,sort_order sortOrder from md_material_category where deleted=0 order by sort_order,id");
        rows.forEach(c -> c.put("attributes", jdbc.queryForList("select cast(id as char) id,code,name,value_type valueType,required,options_json options,sort_order sortOrder from md_category_attribute where category_id=? order by sort_order", Long.valueOf(c.get("id").toString()))));
        return ApiResponse.ok(rows);
    }
    @PostMapping("/material-categories") @PreAuthorize("hasAuthority('masterdata:edit')")
    public ApiResponse<Map<String,Object>> createCategory(@Valid @RequestBody CategoryRequest r) {
        if(r.parentId()!=null&&jdbc.queryForObject("select count(*) from md_material_category where id=? and deleted=0",Long.class,r.parentId())==0)throw new BusinessException(HttpStatus.BAD_REQUEST,"上级分类不存在");
        jdbc.update("insert into md_material_category(parent_id,code,name,description,sort_order) values(?,?,?,?,?)", r.parentId(),r.code(),r.name(),r.description(),r.sortOrder()==null?0:r.sortOrder());
        Long id=jdbc.queryForObject("select id from md_material_category where code=?",Long.class,r.code());
        if(r.attributes()!=null) for(AttributeRequest a:r.attributes()) jdbc.update("insert into md_category_attribute(category_id,code,name,value_type,required,options_json,sort_order) values(?,?,?,?,?,?,?)",id,a.code(),a.name(),a.valueType(),Boolean.TRUE.equals(a.required()),json(a.options()),a.sortOrder()==null?0:a.sortOrder());
        return ApiResponse.ok(Map.of("id",id));
    }
    @GetMapping("/materials")
    public ApiResponse<List<Map<String,Object>>> materials(@RequestParam(required=false) String keyword) {
        String like="%"+(keyword==null?"":keyword)+"%";
        return ApiResponse.ok(jdbc.queryForList("select cast(m.id as char) id,m.code,m.name,c.name category,u.name unit,b.name brand,m.specification,m.tracking_mode trackingMode,m.min_stock minStock,m.attributes_json attributes,m.enabled from md_material m join md_material_category c on c.id=m.category_id join md_unit u on u.id=m.unit_id left join md_brand b on b.id=m.brand_id where m.deleted=0 and (m.code like ? or m.name like ?) order by m.id desc",like,like));
    }
    @PostMapping("/materials") @PreAuthorize("hasAuthority('masterdata:edit')")
    public ApiResponse<Map<String,Object>> createMaterial(@Valid @RequestBody MaterialRequest r) {
        if(jdbc.queryForObject("select count(*) from md_material_category where id=? and deleted=0",Long.class,r.categoryId())==0)throw new BusinessException(HttpStatus.BAD_REQUEST,"物资分类不存在");
        if(jdbc.queryForObject("select count(*) from md_unit where id=?",Long.class,r.unitId())==0)throw new BusinessException(HttpStatus.BAD_REQUEST,"计量单位不存在");
        if(r.brandId()!=null&&jdbc.queryForObject("select count(*) from md_brand where id=?",Long.class,r.brandId())==0)throw new BusinessException(HttpStatus.BAD_REQUEST,"品牌不存在");
        validateAttributes(r.categoryId(),r.attributes());
        jdbc.update("insert into md_material(code,name,category_id,unit_id,brand_id,specification,tracking_mode,min_stock,attributes_json) values(?,?,?,?,?,?,?,?,?)",r.code(),r.name(),r.categoryId(),r.unitId(),r.brandId(),r.specification(),r.trackingMode(),r.minStock()==null?BigDecimal.ZERO:r.minStock(),json(r.attributes()));
        return ApiResponse.ok(Map.of("id",jdbc.queryForObject("select id from md_material where code=?",Long.class,r.code())));
    }
    @GetMapping("/warehouses") public ApiResponse<List<Map<String,Object>>> warehouses(){return ApiResponse.ok(jdbc.queryForList("select cast(id as char) id,code,name,address,manager,enabled from md_warehouse where deleted=0 order by id"));}
    @GetMapping("/units") public ApiResponse<List<Map<String,Object>>> units(){return ApiResponse.ok(jdbc.queryForList("select cast(id as char) id,code,name,precision_scale precisionScale from md_unit order by id"));}
    @GetMapping("/brands") public ApiResponse<List<Map<String,Object>>> brands(){return ApiResponse.ok(jdbc.queryForList("select cast(id as char) id,code,name from md_brand order by id"));}

    private void validateAttributes(Long categoryId,Map<String,Object> attributes){
        List<Map<String,Object>> defs=jdbc.queryForList("select code,name,value_type valueType,required,options_json options from md_category_attribute where category_id=?",categoryId);
        Map<String,Object> values=attributes==null?Map.of():attributes;
        for(var d:defs){String code=d.get("code").toString(); Object value=values.get(code); boolean required=d.get("required") instanceof Number n?n.intValue()!=0:Boolean.parseBoolean(String.valueOf(d.get("required")));if(required&&(value==null||value.toString().isBlank())) throw new BusinessException(HttpStatus.BAD_REQUEST,d.get("name")+"不能为空");
            if(value!=null && "NUMBER".equals(d.get("valueType"))) try{new BigDecimal(value.toString());}catch(Exception e){throw new BusinessException(HttpStatus.BAD_REQUEST,d.get("name")+"必须是数值");}
            if(value!=null && "BOOLEAN".equals(d.get("valueType"))&&!List.of("true","false").contains(value.toString().toLowerCase()))throw new BusinessException(HttpStatus.BAD_REQUEST,d.get("name")+"必须是布尔值");
            if(value!=null && "DATE".equals(d.get("valueType")))try{java.time.LocalDate.parse(value.toString());}catch(Exception e){throw new BusinessException(HttpStatus.BAD_REQUEST,d.get("name")+"必须是日期");}
            if(value!=null && "ENUM".equals(d.get("valueType"))){try{List<?> options=mapper.readValue(String.valueOf(d.get("options")),List.class);if(!options.contains(value.toString()))throw new BusinessException(HttpStatus.BAD_REQUEST,d.get("name")+"不在可选范围内");}catch(BusinessException e){throw e;}catch(Exception e){throw new BusinessException(HttpStatus.BAD_REQUEST,d.get("name")+"枚举定义无效");}}}
    }
    private String json(Object value){try{return value==null?null:mapper.writeValueAsString(value);}catch(JsonProcessingException e){throw new BusinessException(HttpStatus.BAD_REQUEST,"JSON数据格式错误");}}
    public record CategoryRequest(Long parentId,@NotBlank @Size(max=50) String code,@NotBlank @Size(max=100) String name,@Size(max=500) String description,Integer sortOrder,List<@Valid AttributeRequest> attributes){}
    public record AttributeRequest(@NotBlank @Size(max=50) String code,@NotBlank @Size(max=100) String name,@NotBlank @Pattern(regexp="TEXT|NUMBER|BOOLEAN|DATE|ENUM") String valueType,Boolean required,List<@Size(max=100) String> options,Integer sortOrder){}
    public record MaterialRequest(@NotBlank @Size(max=50) String code,@NotBlank @Size(max=200) String name,@NotNull Long categoryId,@NotNull Long unitId,Long brandId,@Size(max=500) String specification,@NotBlank @Pattern(regexp="QUANTITY|BATCH|SERIAL") String trackingMode,@DecimalMin("0") BigDecimal minStock,Map<String,Object> attributes){}
}
