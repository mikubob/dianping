package com.hmdp;

import com.hmdp.utils.RedisIdWorker;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
public class ApplicationTest {

    @Resource
    private RedisIdWorker redisIdWorker;

    private ExecutorService es= Executors.newFixedThreadPool(500);

    /**
     * 测试分布式ID生成器性能，以及其的可用性
     */
    @Test
    public void testNextId() throws InterruptedException{
        //使用CountDownLatch让线程同步等待
        CountDownLatch latch = new CountDownLatch(300);
        //创建线程任务
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            //线程执行完毕，计数器减1
            latch.countDown();
        };
        long start = System.currentTimeMillis();
        //创建300个线程，每个线程创建100个id，总计30000个id
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        //线程阻塞，等待所有线程执行完毕才全部唤醒所有线程
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("耗时：" + (end - start));
    }
}
