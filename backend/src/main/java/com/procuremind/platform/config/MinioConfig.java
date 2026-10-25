package com.procuremind.platform.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class MinioConfig {
    @Bean @Primary MinioClient minioClient(@Value("${procuremind.minio.endpoint}")String endpoint,@Value("${procuremind.minio.access-key}")String access,@Value("${procuremind.minio.secret-key}")String secret){return MinioClient.builder().endpoint(endpoint).credentials(access,secret).build();}
    @Bean("publicMinioClient") MinioClient publicMinioClient(@Value("${procuremind.minio.public-endpoint}")String endpoint,@Value("${procuremind.minio.access-key}")String access,@Value("${procuremind.minio.secret-key}")String secret){return MinioClient.builder().endpoint(endpoint).credentials(access,secret).region("us-east-1").build();}
}
