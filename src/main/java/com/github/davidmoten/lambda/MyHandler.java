package com.github.davidmoten.lambda;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.S3ObjectInputStream;

public class MyHandler {

    public String handle(Map<String, Object> input, Context context) {
        String bucketName = System.getenv("BUCKET_NAME");
        String[] objectNames = System.getenv("OBJECT_NAME").split(",");
        LambdaLogger log = context.getLogger();
        AmazonS3Client s3 = new AmazonS3Client().withRegion(Region.getRegion(Regions.AP_SOUTHEAST_2));
        final long start = System.currentTimeMillis();
        final AtomicLong totalSize = new AtomicLong();
        ExecutorService executor = Executors.newCachedThreadPool();
        StringBuffer s = new StringBuffer();
        for (String objectName : objectNames) {
            executor.submit(() -> {
                S3ObjectInputStream is = s3.getObject(bucketName, objectName).getObjectContent();
                try {
                    is.read();
                    long t = System.currentTimeMillis();
                    long ttfb = System.currentTimeMillis() - t;
                    s.append(objectName + " ttfbMs=" + ttfb + "\n");
                    byte[] bytes = new byte[8192];
                    int length;
                    int total = 0;
                    while ((length = is.read(bytes)) != -1) {
                        total += length;
                    }
                    totalSize.addAndGet(total);
                    double rateMBPerSecond = totalSize.doubleValue() / t * 1000 / 1024 / 1024;
                    s.append(objectName + " rateMBPerSecond=" + rateMBPerSecond + "\n");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    try {
                        is.close();
                    } catch (IOException e) {
                    }
                }
            });
        }
        executor.shutdown();
        try {
            executor.awaitTermination(2, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        long t = System.currentTimeMillis() - start;
        double rateMBPerSecond = totalSize.doubleValue() / t * 1000 / 1024 / 1024;
        s.append("overall rateMBPerSecond:" + rateMBPerSecond + "\n");
        return s.toString();
    }

}
