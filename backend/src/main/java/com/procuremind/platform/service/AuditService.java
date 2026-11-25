package com.procuremind.platform.service;

import com.procuremind.platform.mapper.AuditMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AuditService {
    private final AuditMapper mapper;
    public AuditService(AuditMapper mapper){this.mapper=mapper;}
    public List<Map<String,Object>> list(){return mapper.list();}
}
