package com.ayu.raksha.card.Ayu.Raksha.Card.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class S3Service {

    private final S3Client s3Client;
    private final S3Presigner presigner;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.region:us-east-1}")
    private String region;

    @Value("${aws.access-key-id:}")
    private String accessKeyId;

    @Value("${aws.secret-access-key:}")
    private String secretAccessKey;

    @Value("${aws.s3.presign-expiration-minutes:60}")
    private int presignMinutes;

    public S3Service(@Value("${aws.region:us-east-1}") String region,
                     @Value("${aws.access-key-id:}") String accessKeyId,
                     @Value("${aws.secret-access-key:}") String secretAccessKey) {
        Region r = Region.of(region);

        if (accessKeyId != null && !accessKeyId.isBlank() && secretAccessKey != null && !secretAccessKey.isBlank()) {
            AwsBasicCredentials creds = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
            this.s3Client = S3Client.builder()
                    .region(r)
                    .credentialsProvider(StaticCredentialsProvider.create(creds))
                    .build();

            this.presigner = S3Presigner.builder()
                    .region(r)
                    .credentialsProvider(StaticCredentialsProvider.create(creds))
                    .build();
        } else {
            this.s3Client = S3Client.builder()
                    .region(r)
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();

            this.presigner = S3Presigner.builder()
                    .region(r)
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
        }
    }

    public String uploadFile(String patientId, String type, MultipartFile file) throws IOException {
        String key = String.format("%s/%s/%d-%s", patientId, type, System.currentTimeMillis(), sanitizeFilename(file.getOriginalFilename()));

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        return key;
    }

    public String buildKey(String patientId, String type, String filename) {
        return String.format("%s/%s/%d-%s", patientId, type, System.currentTimeMillis(), sanitizeFilename(filename));
    }

    public String getPresignedUrl(String key, int minutes) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(minutes))
                    .getObjectRequest(getObjectRequest)
                    .build();

            PresignedGetObjectRequest presigned = presigner.presignGetObject(presignRequest);
            return presigned.url().toString();
        } catch (NoSuchKeyException e) {
            return null;
        }
    }

    public String getPresignedPutUrl(String key, int minutes, String contentType) {
        PutObjectRequest por = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(minutes))
                .putObjectRequest(por)
                .build();
        PresignedPutObjectRequest presigned = presigner.presignPutObject(presignRequest);
        return presigned.url().toString();
    }

    public List<S3Object> listFilesForPatient(String patientId) {
        ListObjectsV2Request req = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(patientId + "/")
                .build();

        ListObjectsV2Response resp = s3Client.listObjectsV2(req);
        if (resp == null || resp.contents() == null) return new ArrayList<>();
        return resp.contents();
    }

    private String sanitizeFilename(String name) {
        if (name == null) return "file";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
