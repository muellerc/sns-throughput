package org.example;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.crt.AwsCrtAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsAsyncClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.RandomStringUtils;

public class App 
{
    private AtomicInteger sendCount = new AtomicInteger();
    private AtomicInteger receiveCount = new AtomicInteger();

    private SnsAsyncClient snsClient;

    private String MESSAGE;
    private String topicArn;

    private LinkedBlockingQueue<CompletableFuture<PublishResponse>> outstandingResponses =
            new LinkedBlockingQueue<>(2000);

    private App(String topicArn, int packageSize) throws InterruptedException{

        this.topicArn = topicArn;
        MESSAGE = RandomStringUtils.randomAlphabetic(packageSize);
        registerShutdownHook(sendCount, receiveCount, System.currentTimeMillis());
        registerResponseProcessor();

        System.out.println("building client for " + topicArn);
        snsClient
                = SnsAsyncClient.builder()
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .httpClient(AwsCrtAsyncHttpClient.builder()
                        .maxConcurrency(200)
                        .build())
                .region(Region.US_EAST_1)
                .build();
    }

    private  void execute() throws InterruptedException{
        new Thread(new Runnable() {
            @Override
            public void run() {
                Map < String, MessageAttributeValue > attributes = null;
                MessageAttributeValue.Builder attributeProtoType = MessageAttributeValue.builder().dataType("String");
                PublishRequest.Builder requestPrototype = PublishRequest.builder().topicArn(topicArn);

                while(true) {
                    attributes = new HashMap<>();
                    attributes.put(
                            "count",
                            attributeProtoType.stringValue(String.valueOf(sendCount.incrementAndGet())).build());

                    CompletableFuture<PublishResponse> futureResponse = snsClient.publish(
                            requestPrototype
                                    .messageAttributes(attributes)
                                    .message(MESSAGE)
                                    .build()
                    );

                    try {
                        outstandingResponses.offer(futureResponse, 5, TimeUnit.SECONDS);
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    private void registerResponseProcessor(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true){
                    try{
                        CompletableFuture<PublishResponse> future = outstandingResponses.take();
                        PublishResponse response = future.get();
                        if (response.sdkHttpResponse().statusCode() == 200){
                            receiveCount.incrementAndGet();
                        }
                        else{
                            System.out.println(response);
                        }
                    }
                    catch (Exception e){
                        throw new RuntimeException(e);
                    }
                }
            }
        }).start();
    }

    private void registerShutdownHook(final AtomicInteger sendCount, final AtomicInteger receiveCount, final long start){
        Thread shutdown = new Thread(new Runnable() {
            long s = start;

            @Override
            public void run() {
                long duration = System.currentTimeMillis() - s;
                int currentSendCount = sendCount.get();
                int currentReceiveCount = receiveCount.get();
                System.out.println(
                        String.format("\nTopicArn %s\n========\nMessages sent: %d \nSeconds: %f \nRate %f/sec\n\nMessages received: %d \nSeconds: %f \nRate %f/sec",
                                topicArn,
                                currentSendCount,
                                duration/1000.0,
                                currentSendCount / (duration / 1000.0),
                                currentReceiveCount,
                                duration/1000.0,
                                currentReceiveCount / (duration / 1000.0)));
            }
        });

        Runtime.getRuntime().addShutdownHook(shutdown);
    }

    public static void main( String[] args ) throws InterruptedException
    {
        int packageSize = Integer.parseInt(args[0]);

        for (int i = 1; i< args.length; i++){
            System.out.println(args[i]);
            new App(args[i], packageSize).execute();
        }
    }
}
