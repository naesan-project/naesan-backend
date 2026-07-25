package com.naesan.evidence.adapter.out.storage;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.naesan.evidence.application.port.out.FileStorage;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "naesan.storage.provider", havingValue = "s3")
public class S3StorageConfiguration {

    @Bean
    S3Client s3Client(
            @Value("${naesan.storage.s3.region}") String region,
            @Value("${naesan.storage.s3.endpoint:}") String endpoint,
            @Value("${naesan.storage.s3.path-style:false}") boolean pathStyle
    ) {
        S3ClientBuilder builder = S3Client.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .credentialsProvider(DefaultCredentialsProvider.create())
                .region(Region.of(region))
                .forcePathStyle(pathStyle);
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    @Bean
    FileStorage s3FileStorage(
            S3Client s3Client,
            @Value("${naesan.storage.s3.bucket}") String bucket,
            @Value("${naesan.storage.s3.server-side-encryption:AES256}")
            String serverSideEncryption
    ) {
        return new S3FileStorage(
                s3Client,
                bucket,
                encryption(serverSideEncryption)
        );
    }

    private ServerSideEncryption encryption(String value) {
        if (value.isBlank()) {
            return null;
        }
        return ServerSideEncryption.fromValue(value);
    }
}
