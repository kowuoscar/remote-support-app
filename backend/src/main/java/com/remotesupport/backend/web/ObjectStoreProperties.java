package com.remotesupport.backend.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The S3-compatible object store holding Carrier Invoice File bytes (ADR 0007), bound from {@code
 * app.object-store}. Every value comes from an {@code OBJECT_STORE_*} environment variable; the
 * defaults in {@code application.yml} serve the local SeaweedFS only. Nothing here validates: a
 * setting that cannot build a client is reported by {@link ObjectStoreConfig}, never by binding, so
 * no store setting can fail startup.
 *
 * @param endpoint {@code OBJECT_STORE_ENDPOINT}, parsed when the client is built, never at binding
 * @param region {@code OBJECT_STORE_REGION}
 * @param bucket {@code OBJECT_STORE_BUCKET}
 * @param accessKey {@code OBJECT_STORE_ACCESS_KEY}
 * @param secretKey {@code OBJECT_STORE_SECRET_KEY}
 * @param pathStyleAccess {@code OBJECT_STORE_PATH_STYLE}: path-style (SeaweedFS) or virtual-hosted
 *     (Hetzner) addressing
 * @param createBucket {@code OBJECT_STORE_CREATE_BUCKET}: create a missing bucket at startup
 */
@ConfigurationProperties("app.object-store")
public record ObjectStoreProperties(
    String endpoint,
    String region,
    String bucket,
    String accessKey,
    String secretKey,
    boolean pathStyleAccess,
    boolean createBucket) {}
