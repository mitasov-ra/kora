package ru.tinkoff.kora.s3.client.aws.model;

import ru.tinkoff.kora.s3.client.model.S3Body;

import java.io.InputStream;

final class AwsS3Body implements S3Body {

    private final String encoding;
    private final String type;
    private final long size;
    private final InputStream inputStream;

    AwsS3Body(InputStream inputStream, long size, String encoding, String type) {
        this.encoding = encoding;
        this.type = type;
        this.size = size;
        this.inputStream = inputStream;
    }

    @Override
    public InputStream asInputStream() {
        return inputStream;
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public String encoding() {
        return encoding;
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public String toString() {
        return "AwsS3Body{type=" + type +
            ", encoding=" + encoding +
            ", size=" + size +
            '}';
    }
}
