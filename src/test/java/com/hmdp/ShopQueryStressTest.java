package com.hmdp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

/**
 * 专门针对店铺查询接口的压力测试类
 * 针对ShopController中的不同接口进行专项压力测试
 */
public class ShopQueryStressTest {
    private static final DecimalFormat DF = new DecimalFormat("#.##");

    // 测试配置
    public static class ShopTestConfig {
        public final String baseUrl;
        public final String endpoint;
        public final int totalRequests;
        public final int concurrentThreads;
        public final int shopId; // 特定测试的店铺ID
        public final boolean enableCacheWarmup; // 是否启用缓存预热
        
        public ShopTestConfig(String baseUrl, String endpoint, int totalRequests, 
                             int concurrentThreads, int shopId, boolean enableCacheWarmup) {
            this.baseUrl = baseUrl;
            this.endpoint = endpoint;
            this.totalRequests = totalRequests;
            this.concurrentThreads = concurrentThreads;
            this.shopId = shopId;
            this.enableCacheWarmup = enableCacheWarmup;
        }
    }

    // 统计数据
    public static class ShopTestStats {
        public final AtomicInteger successCount = new AtomicInteger(0);
        public final AtomicInteger errorCount = new AtomicInteger(0);
        public final AtomicLong totalTime = new AtomicLong(0);
        public final List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>());
        public final Map<Integer, AtomicInteger> statusCodeCounts = 
            Map.of(200, new AtomicInteger(0), 404, new AtomicInteger(0), 500, new AtomicInteger(0));
        public final AtomicLong maxResponseTime = new AtomicLong(0);
        public final AtomicLong minResponseTime = new AtomicLong(Long.MAX_VALUE);
        public final AtomicLong cacheHitCount = new AtomicLong(0); // 缓存命中计数
        public final AtomicLong cacheMissCount = new AtomicLong(0); // 缓存未命中计数
        public final AtomicInteger timeoutCount = new AtomicInteger(0);
        public final AtomicInteger connectionErrorCount = new AtomicInteger(0);
    }

    public static void main(String[] args) throws InterruptedException {
        // 测试不同的店铺查询接口
        System.out.println("开始店铺查询接口专项压力测试...\n");
        
        // 测试单个店铺查询接口
        testSingleShopQuery();
        
        // 测试店铺列表查询接口
        testShopListQuery();
        
        // 测试按类型查询接口
        testShopByTypeQuery();
        
        System.out.println("\n所有测试完成！");
    }

    /**
     * 测试单个店铺查询接口
     */
    private static void testSingleShopQuery() throws InterruptedException {
        System.out.println("🔍 测试单个店铺查询接口: /shop/{id}");
        
        ShopTestConfig config = new ShopTestConfig(
            "http://localhost:8081", 
            "/shop/", 
            1500,           // 1500次请求
            75,             // 75个并发线程
            1,              // 测试店铺ID为1
            true            // 启用缓存预热
        );
        
        runShopQueryTest(config);
    }

    /**
     * 测试店铺列表查询接口
     */
    private static void testShopListQuery() throws InterruptedException {
        System.out.println("\n🔍 测试店铺列表查询接口: /shop/of/name");
        
        ShopTestConfig config = new ShopTestConfig(
            "http://localhost:8081", 
            "/shop/of/name", 
            1000,           // 1000次请求
            50,             // 50个并发线程
            1,              // 不需要特定ID，但保留参数结构
            true            // 启用缓存预热
        );
        
        // 使用带参数的URL进行测试
        runShopQueryWithParamsTest(config, Map.of("name", "奶茶", "current", "1"));
    }

    /**
     * 测试按类型查询接口
     */
    private static void testShopByTypeQuery() throws InterruptedException {
        System.out.println("\n🔍 测试按类型查询接口: /shop/of/type");
        
        ShopTestConfig config = new ShopTestConfig(
            "http://localhost:8081", 
            "/shop/of/type", 
            1200,           // 1200次请求
            60,             // 60个并发线程
            1,              // 类型ID为1
            true            // 启用缓存预热
        );
        
        // 使用带参数的URL进行测试
        runShopQueryWithParamsTest(config, Map.of("typeId", "1", "current", "1"));
    }

    /**
     * 运行店铺查询测试（单个店铺）
     */
    private static void runShopQueryTest(ShopTestConfig config) throws InterruptedException {
        String fullUrl = config.baseUrl + config.endpoint + config.shopId;
        
        if (config.enableCacheWarmup) {
            System.out.println("  🔄 执行缓存预热...");
            warmupCache(fullUrl);
        }

        System.out.println("  🚀 开始发送 " + config.totalRequests + " 个并发请求...");
        
        ExecutorService executor = Executors.newFixedThreadPool(config.concurrentThreads);
        CountDownLatch latch = new CountDownLatch(config.totalRequests);
        
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();
                
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .GET()
                .timeout(java.time.Duration.ofSeconds(10))
                .build();

        long start = System.currentTimeMillis();
        ShopTestStats stats = new ShopTestStats();

        for (int i = 0; i < config.totalRequests; i++) {
            executor.submit(() -> {
                try {
                    long reqStart = System.currentTimeMillis();
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    long reqEnd = System.currentTimeMillis();
                    
                    int statusCode = response.statusCode();
                    long reqTime = reqEnd - reqStart;
                    
                    // 更新统计信息
                    updateShopStats(stats, reqTime, statusCode);
                    
                    // 模拟缓存命中/未命中的判断（基于响应时间）
                    if (reqTime < 50) {
                        stats.cacheHitCount.incrementAndGet(); // 假设快速响应是缓存命中
                    } else {
                        stats.cacheMissCount.incrementAndGet(); // 较慢响应可能是缓存未命中
                    }
                    
                } catch (java.net.http.HttpTimeoutException e) {
                    stats.timeoutCount.incrementAndGet();
                    stats.errorCount.incrementAndGet();
                } catch (Exception e) {
                    stats.connectionErrorCount.incrementAndGet();
                    stats.errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
            
            // 控制请求发送频率
            if (i % 10 == 0) {
                Thread.sleep(5); // 每发送10个请求暂停5毫秒
            }
        }

        latch.await(3, TimeUnit.MINUTES);
        executor.shutdown();
        
        long end = System.currentTimeMillis();
        printShopTestResults(stats, start, end, config, null);
    }

    /**
     * 运行带参数的店铺查询测试
     */
    private static void runShopQueryWithParamsTest(ShopTestConfig config, Map<String, String> params) throws InterruptedException {
        StringBuilder urlBuilder = new StringBuilder(config.baseUrl + config.endpoint);
        urlBuilder.append("?");
        for (Map.Entry<String, String> param : params.entrySet()) {
            urlBuilder.append(param.getKey()).append("=").append(param.getValue()).append("&");
        }
        String fullUrl = urlBuilder.toString();
        fullUrl = fullUrl.substring(0, fullUrl.length() - 1); // 移除最后一个&符号
        
        if (config.enableCacheWarmup) {
            System.out.println("  🔄 执行缓存预热...");
            warmupCache(fullUrl);
        }

        System.out.println("  🚀 开始发送 " + config.totalRequests + " 个并发请求...");
        
        ExecutorService executor = Executors.newFixedThreadPool(config.concurrentThreads);
        CountDownLatch latch = new CountDownLatch(config.totalRequests);
        
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();
                
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .GET()
                .timeout(java.time.Duration.ofSeconds(10))
                .build();

        long start = System.currentTimeMillis();
        ShopTestStats stats = new ShopTestStats();

        for (int i = 0; i < config.totalRequests; i++) {
            executor.submit(() -> {
                try {
                    long reqStart = System.currentTimeMillis();
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    long reqEnd = System.currentTimeMillis();
                    
                    int statusCode = response.statusCode();
                    long reqTime = reqEnd - reqStart;
                    
                    // 更新统计信息
                    updateShopStats(stats, reqTime, statusCode);
                    
                    // 模拟缓存命中/未命中的判断
                    if (reqTime < 50) {
                        stats.cacheHitCount.incrementAndGet();
                    } else {
                        stats.cacheMissCount.incrementAndGet();
                    }
                    
                } catch (java.net.http.HttpTimeoutException e) {
                    stats.timeoutCount.incrementAndGet();
                    stats.errorCount.incrementAndGet();
                } catch (Exception e) {
                    stats.connectionErrorCount.incrementAndGet();
                    stats.errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
            
            // 控制请求发送频率
            if (i % 10 == 0) {
                Thread.sleep(5); // 每发送10个请求暂停5毫秒
            }
        }

        latch.await(3, TimeUnit.MINUTES);
        executor.shutdown();
        
        long end = System.currentTimeMillis();
        printShopTestResults(stats, start, end, config, params);
    }

    /**
     * 更新店铺测试统计
     */
    private static void updateShopStats(ShopTestStats stats, long responseTime, int statusCode) {
        stats.responseTimes.add(responseTime);
        stats.totalTime.addAndGet(responseTime);

        // 记录最大最小响应时间
        stats.maxResponseTime.set(Math.max(stats.maxResponseTime.get(), responseTime));
        if (responseTime < stats.minResponseTime.get()) {
            stats.minResponseTime.set(responseTime);
        }

        // 统计状态码
        if (stats.statusCodeCounts.containsKey(statusCode)) {
            stats.statusCodeCounts.get(statusCode).incrementAndGet();
        }

        // 分类成功/失败
        if (statusCode >= 200 && statusCode < 300) {
            stats.successCount.incrementAndGet();
        } else {
            stats.errorCount.incrementAndGet();
        }
    }

    /**
     * 执行缓存预热
     */
    private static void warmupCache(String url) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(java.time.Duration.ofSeconds(5))
                    .build();

            // 发送多个预热请求
            IntStream.range(0, 20).forEach(i -> {
                try {
                    client.send(request, HttpResponse.BodyHandlers.ofString());
                } catch (Exception e) {
                    // 忽略预热过程中的异常
                }
            });
            
            Thread.sleep(1000); // 预热后等待1秒让系统稳定
        } catch (Exception e) {
            System.out.println("    预热请求出现异常: " + e.getMessage());
        }
    }

    /**
     * 打印店铺测试结果
     */
    private static void printShopTestResults(ShopTestStats stats, long startTime, long endTime, 
                                           ShopTestConfig config, Map<String, String> params) {
        Collections.sort(stats.responseTimes);
        
        long totalRequests = stats.successCount.get() + stats.errorCount.get();
        long avgTime = stats.responseTimes.size() > 0 ? stats.totalTime.get() / stats.responseTimes.size() : 0;
        double successRate = totalRequests > 0 ? (double) stats.successCount.get() / totalRequests * 100 : 0;
        
        // 计算百分位数
        long p50 = getPercentile(stats.responseTimes, 0.50);
        long p90 = getPercentile(stats.responseTimes, 0.90);
        long p95 = getPercentile(stats.responseTimes, 0.95);
        long p99 = getPercentile(stats.responseTimes, 0.99);
        
        // 计算吞吐量
        long testDuration = endTime - startTime;
        double throughput = testDuration > 0 ? (double) totalRequests / (testDuration / 1000.0) : 0;
        
        // 计算缓存命中率
        long totalCacheOps = stats.cacheHitCount.get() + stats.cacheMissCount.get();
        double cacheHitRate = totalCacheOps > 0 ? (double) stats.cacheHitCount.get() / totalCacheOps * 100 : 0;
        
        System.out.println("  📊 测试结果摘要:");
        System.out.printf("    • 总耗时: %s%n", formatTime(testDuration));
        System.out.printf("    • 总请求数: %d%n", totalRequests);
        System.out.printf("    • 成功率: %s%% (%d 成功 / %d 失败)%n", 
                         DF.format(successRate), stats.successCount.get(), stats.errorCount.get());
        System.out.printf("    • 平均吞吐量: %s req/s%n", DF.format(throughput));
        
        System.out.println("  ⏱️  响应时间统计:");
        System.out.printf("    • 平均响应时间: %d ms%n", avgTime);
        System.out.printf("    • 最小响应时间: %d ms%n", 
                         stats.minResponseTime.get() == Long.MAX_VALUE ? 0 : stats.minResponseTime.get());
        System.out.printf("    • 最大响应时间: %d ms%n", stats.maxResponseTime.get());
        System.out.printf("    • P50响应时间: %d ms%n", p50);
        System.out.printf("    • P90响应时间: %d ms%n", p90);
        System.out.printf("    • P95响应时间: %d ms%n", p95);
        System.out.printf("    • P99响应时间: %d ms%n", p99);
        
        if (totalCacheOps > 0) {
            System.out.println("  💾 缓存性能:");
            System.out.printf("    • 缓存命中率: %s%% (%d 命中 / %d 未命中)%n", 
                             DF.format(cacheHitRate), stats.cacheHitCount.get(), stats.cacheMissCount.get());
        }
        
        System.out.println("  🚨 错误分析:");
        System.out.printf("    • 超时错误: %d%n", stats.timeoutCount.get());
        System.out.printf("    • 连接错误: %d%n", stats.connectionErrorCount.get());
        
        // 性能评估
        System.out.println("  📈 性能评估:");
        if (avgTime < 50 && successRate > 95) {
            System.out.println("    • 评级: ⭐⭐⭐⭐⭐ 卓越 - 响应迅速且稳定");
        } else if (avgTime < 100 && successRate > 90) {
            System.out.println("    • 评级: ⭐⭐⭐⭐ 优秀 - 性能良好");
        } else if (avgTime < 200 && successRate > 80) {
            System.out.println("    • 评级: ⭐⭐⭐ 良好 - 满足基本需求");
        } else if (avgTime < 500 && successRate > 70) {
            System.out.println("    • 评级: ⭐⭐ 一般 - 需要优化");
        } else {
            System.out.println("    • 评级: ⭐ 较差 - 需要紧急优化");
        }
        
        // 优化建议
        System.out.println("  💡 优化建议:");
        if (avgTime > 200) {
            System.out.println("    • 响应时间较长，考虑优化数据库查询或添加缓存");
        }
        if (successRate < 95) {
            System.out.println("    • 成功率偏低，检查服务器日志查找错误原因");
        }
        if (cacheHitRate < 70) {
            System.out.println("    • 缓存命中率较低，可考虑优化缓存策略");
        }
        if (throughput < 50) {
            System.out.println("    • 吞吐量偏低，可考虑优化代码逻辑或增加服务器资源");
        }
    }

    /**
     * 计算百分位数值
     */
    private static long getPercentile(List<Long> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(percentile * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }

    /**
     * 格式化时间显示
     */
    private static String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long mins = seconds / 60;
        seconds = seconds % 60;
        return mins + "m " + seconds + "s";
    }
}