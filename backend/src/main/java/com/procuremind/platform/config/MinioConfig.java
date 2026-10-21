package com.procuremind.platform.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {
    @Bean MinioClient minioClient(@Value("${procuremind.minio.endpoint}")String endpoint,@Value("${procuremind.minio.access-key}")String access,@Value("${procuremind.minio.secret-key}")String secret){return MinioClient.builder().endpoint(endpoint).credentials(access,secret).build();}
}
