package ru.tinkoff.kora.s3.client.aws;

import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.http.client.common.HttpClient;
import ru.tinkoff.kora.s3.client.S3ClientModule;
import ru.tinkoff.kora.s3.client.S3Config;
import ru.tinkoff.kora.s3.client.S3SimpleAsyncClient;
import ru.tinkoff.kora.s3.client.S3SimpleClient;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public interface AwsS3ClientModule extends S3ClientModule {

    @Tag(S3Client.class)
    @DefaultComponent
    default HttpClient awsS3httpClient(HttpClient client) {
        return client;
    }

    @DefaultComponent
    default KoraAwsSdkHttpClient awsKoraSdkHttpClient(@Tag(S3Client.class) HttpClient client) {
        return new KoraAwsSdkHttpClient(client);
    }

    @Tag(S3Client.class)
    @DefaultComponent
    default ExecutorService awsAsyncExecutorService() {
        return Executors.newFixedThreadPool(Math.max(Runtime.getRuntime().availableProcessors(), 2) * 2);
    }

    default AwsS3ClientConfig awsS3ClientConfig(Config config, ConfigValueExtractor<AwsS3ClientConfig> extractor) {
        var value = config.get("s3.client.aws");
        return extractor.extract(value);
    }

    @DefaultComponent
    default AwsCredentialsProvider awsCredentialsProvider(S3Config s3Config) {
        return () -> AwsBasicCredentials.create(s3Config.accessKey(), s3Config.secretKey());
    }

    @DefaultComponent
    default S3Configuration awsS3Configuration(AwsS3ClientConfig awsS3ClientConfig) {
        return S3Configuration.builder()
            .checksumValidationEnabled(awsS3ClientConfig.checksumValidationEnabled())
            .chunkedEncodingEnabled(awsS3ClientConfig.chunkedEncodingEnabled())
            .multiRegionEnabled(awsS3ClientConfig.multiRegionEnabled())
            .pathStyleAccessEnabled(awsS3ClientConfig.pathStyleAccessEnabled())
            .accelerateModeEnabled(awsS3ClientConfig.accelerateModeEnabled())
            .useArnRegionEnabled(awsS3ClientConfig.useArnRegionEnabled())
            .build();
    }

    default S3Client awsS3Client(SdkHttpClient httpClient,
                                 AwsCredentialsProvider credentialsProvider,
                                 S3Configuration s3Configuration,
                                 List<ExecutionInterceptor> interceptors) {
        return S3Client.builder()
            .credentialsProvider(credentialsProvider)
            .httpClient(httpClient)
            .serviceConfiguration(s3Configuration)
            .overrideConfiguration(b -> interceptors.forEach(b::addExecutionInterceptor))
            .build();
    }

    default S3AsyncClient awsS3AsyncClient(SdkAsyncHttpClient asyncHttpClient,
                                           AwsCredentialsProvider credentialsProvider,
                                           S3Configuration s3Configuration,
                                           List<ExecutionInterceptor> interceptors) {
        return S3AsyncClient.builder()
            .credentialsProvider(credentialsProvider)
            .httpClient(asyncHttpClient)
            .serviceConfiguration(s3Configuration)
            .overrideConfiguration(b -> interceptors.forEach(b::addExecutionInterceptor))
            .build();
    }

    default S3SimpleClient awsS3SimpleClient(S3Client s3Client) {
        return new AwsS3SimpleClient(s3Client);
    }

    default S3SimpleAsyncClient awsS3SimpleAsyncClient(S3AsyncClient s3AsyncClient, @Tag(S3Client.class) ExecutorService awsExecutor) {
        return new AwsS3SimpleAsyncClient(s3AsyncClient, awsExecutor);
    }
}
