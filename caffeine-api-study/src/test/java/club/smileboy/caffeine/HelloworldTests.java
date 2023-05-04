package club.smileboy.caffeine;

import com.github.benmanes.caffeine.cache.*;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * @author FLJ
 * @date 2022/9/6
 * @time 9:48
 * @Description hello world test
 */
public class HelloworldTests {

    /**
     * 基于容量以及 过期时间的缓存处理 ...
     */
    @Test
    public void helloworld() throws InterruptedException {

        Cache<Object, Object> cache = Caffeine.newBuilder()
                .expireAfterWrite(2, TimeUnit.SECONDS)
                .maximumSize(5)
                /**'
                 * 你可以发现,移除也并非按照 LRU 的形式进行移除,随机移除一个 ...
                 */
                .removalListener(new RemovalListener<Object, Object>() {
                    @Override
                    public void onRemoval(@Nullable Object key, @Nullable Object value, RemovalCause removalCause) {
                        System.out.printf("key: %s , value: %s, removalCause: %s%n", key,value,removalCause);
                    }
                })

                // 由此可见这个抛弃监听器是异步处理的 ...
//                .evictionListener(new RemovalListener<Object, Object>() {
//                    /**
//                     *
//                     * @param o key
//                     * @param o2 value
//                     * @param removalCause 移除原因 ...
//                     */
//                    public void onRemoval(@Nullable Object o, @Nullable Object o2, RemovalCause removalCause) {
//                        System.out.println(String.format("one: %s  two: %s   , removalCause: %s", o, o2, removalCause));
//                    }
//                })
                .build();

        // 查找缓存，如果缓存不存在则生成缓存元素,  如果无法生成则返回null
        Object o = cache.get(new String("3"), this::createExpensiveGraph);
        System.out.println(o);

        // 通过原子计算的方式写入缓存(避免和其他写入竞争, 应该是cas 自旋锁) ...
        System.out.println(cache.get(4, this::createExpensiveGraph));

        System.out.println(cache.get(5, this::createExpensiveGraph));
        System.out.println(cache.get(6, this::createExpensiveGraph));
        System.out.println(cache.get(7, this::createExpensiveGraph));
        System.out.println(cache.get(8, this::createExpensiveGraph));
        System.out.println(cache.get(9, this::createExpensiveGraph));

        // 放入一个对象 ...
        cache.put(10,200);
        cache.put(20,30);
        System.out.println("get value ...... ");

//        System.out.println(cache.get(4, key -> null));
//        cache.get(5,key -> null);

        // 如果延迟读取,数据已经被清理掉了 ...
//        TimeUnit.SECONDS.sleep(1);
        // 获取一个缓存值(如果存在) ..
        System.out.println(String.format("if key 4 present,value %s", cache.getIfPresent(4)));
        // 第一次可能存在,因为可能还没有清除掉(第二次就被清除掉了) ...
        System.out.println(String.format("if key 4 present,value %s", cache.getIfPresent(4)));
        System.out.println(String.format("if key 5 present,value %s", cache.getIfPresent(5)));



        // 让Key 9 无效 ...
        cache.invalidate(9);

        // 这样直接报错 ...
//        Object o1 = cache.get("100", key -> {
//            throw new NullPointerException();
//        });

//        System.out.println("构建缓存的过程中出现了问题: result" + o1);


        // 清理掉
        cache.cleanUp();


        // 也可以通过暴露的map进行处理 ..
        ConcurrentMap<Object, Object> concurrentMap = cache.asMap();
        concurrentMap.put("3",33333);

        Object o2 = concurrentMap.get("3");
        System.out.println(o2);

        TimeUnit.SECONDS.sleep(2);

        Object o3 = concurrentMap.get("3");

        System.out.println(o3);

    }

    @org.springframework.lang.Nullable
    private Object createExpensiveGraph(Object key) {

        // 如果是数字
        if(key instanceof Integer || int.class.isAssignableFrom(key.getClass())) {
            return Integer.parseInt(key.toString()) * 2;
        }
        // 无法生成
        return null;
    }

    /**
     * 自动加载测试
     *
     * LoadingCache 它的能力等价于 Cache + CacheLoader ...
     *
     * 总结,如果批量key 获取效率更高,可以重写CacheLoader 的loadAll 进行处理 ....
     *
     * 对不存在的key 进行 自动加载 ...
     *
     * 我们通过复写loadAll(可以进行批量的数据缓存加载) ....
     */
    public static class AutoLoadCacheTests {

        @Test
        public void test() throws InterruptedException {

            // 手动加载和自动加载的区别是,在get的过程中,如果不存在,自动根据条件获取缓存数据进行缓存 ...
            LoadingCache<Object, Object> cache = Caffeine.newBuilder()
                    .maximumSize(5)
//                    .removalListener(new RemovalListener<Object, Object>() {
//                        @Override
//                        public void onRemoval(@Nullable Object o, @Nullable Object o2, RemovalCause removalCause) {
//                            System.out.printf("remove key: %s, value: %s,removalCause is: %s%n", o,o2,removalCause);
//                        }
//                    })
                    .evictionListener(new RemovalListener<Object, Object>() {
                        @Override
                        public void onRemoval(@Nullable Object o, @Nullable Object o2, RemovalCause removalCause) {
                            System.out.printf("remove key: %s, value: %s,removalCause is: %s%n", o,o2,removalCause);
                        }
                    })
                    .expireAfterWrite(2, TimeUnit.SECONDS)
                    .build(new CacheLoader<Object, Object>() {
                        @Override
                        public @Nullable Object load(Object o) throws Exception {
                            System.out.println("single load ...");
                            // 单个加载 ...
                            return AutoLoadCacheTests.this.createExpensiveGraph(o);
                        }

                        @Override
                        public Map<?, ?> loadAll(Set<?> keys) throws Exception {
                            System.out.println("load All,load keys count: " + keys.size());

                            // 批量处理 ....

                            // 根据Key的hash值,进行统一处理 ...
                            // 或者根据 前缀进行统一处理 ....

                            LinkedHashMap<Object, Object> hashMap = new LinkedHashMap<>();

                            for (Object key : keys) {
                                if (key.toString().startsWith("number:")) {
                                    String number = key.toString().substring(7);
                                    int i = Integer.parseInt(number);
                                    hashMap.put(key,i * i);
                                }
                                else if(key.toString().startsWith("string:")) {
                                    hashMap.put(key,key + "-autoload-string");
                                }

                                else {
                                    hashMap.put(key,key + "-autoload-unknown");
                                }
                            }

                            return hashMap;
                        }
                    });


            // 自动加载缓存
            System.out.println(cache.get("3"));

            Thread thread = new Thread(() -> {

                long count = 0;
                while((count = cache.estimatedSize()) > 0) {
                    try {
                        System.out.printf("size %s%n", count);
                        TimeUnit.MILLISECONDS.sleep(200);
                    }catch (InterruptedException e) {
                        System.out.println("被打断了,结束后台线程");
                        return;
                    }
                }
                System.out.println("自然结束 !!!");
            });

            thread.setDaemon(true);

            thread.start();

            cache.put("4","4444");

            for (int i = 0; i < 10; i++) {
                cache.get(i);
            }



            // 这里本来过期了2秒,如果我们一直不去访问,那么一直无法得到过期事件 ..
            TimeUnit.SECONDS.sleep(2);


            System.out.println("over");
//            TimeUnit.SECONDS.sleep(3);

            // 无效所有 ... (手动触发这些事件) ..
//            cache.invalidateAll();

            // 可以发现,事件通知还是惰性的 ...
            System.out.println(cache.get(1));

            cache.put("number:1","1");
            cache.put("number:2","2");

            Map<Object, Object> all = cache.getAll(Arrays.asList("number:1", "number:2", "string:3", "4", 5));

            all.forEach((key,value) -> {
                System.out.printf("acquire key: %s,acquire value: %s%n", key,value);
            });

            System.out.println("再次尝试获取");
            all = cache.getAll(Arrays.asList("number:1", "number:2", "string:3", "4", 5));
            all.forEach((key,value) -> {
                System.out.printf("acquire key: %s,acquire value: %s%n", key,value);
            });

        }

        /**
         * 创建 value 的过程
         * @param key key
         * @return 获取value / null(无法创建缓存) ...
         */
        public Object createExpensiveGraph(Object key) {
            if(key instanceof Integer || int.class.isAssignableFrom(key.getClass())) {
                int i = new Random(System.currentTimeMillis()).nextInt();
                return Math.abs(i) / ((Integer.parseInt(key.toString()) == 0)  ? 1 : Integer.parseInt(key.toString()));
            }
            return key + "auto-load";
        };
    }

    /**
     * 手动异步加载
     *
     * 加载动作也是异步的 ....
     *
     * 所有动作都是异步的 ....
     *
     * 但是你可以通过synchronous 进行同步接口调用 ... (synchronous()方法给 Cache提供了阻塞直到异步缓存生成完毕的能力。)
     *
     * 同样,它为Cache 提供了一个异步的能力,通过向executor上生成缓存元素并返回 CompletableFuture, 给出了当下流行的 响应式编程模型中利用缓存的能力 ...
     *
     * 默认线程池是   ForkJoinPool.commonPool(), 但是 我们可以提供自定义的执行器 ..
     * 通过Caffeine.executor进行设置 ...
     */
    public static class AsyncCacheTests {

        @Test
        public void test() throws InterruptedException {


            AsyncCache<Object, Object> asyncCache = Caffeine.newBuilder()
                    .expireAfterWrite(2, TimeUnit.SECONDS)
                    // 可以设置使用的执行器 ....
//                    .executor()
                    .maximumSize(5)
                    .evictionListener(new RemovalListener<Object, Object>() {
                        @Override
                        public void onRemoval(@Nullable Object o, @Nullable Object o2, RemovalCause removalCause) {
                            System.out.printf("remove key: %s, value: %s,removalCause is: %s%n", o, o2, removalCause);
                        }
                    })
                    .buildAsync();

            System.out.println("acquire key " + 3);
            System.out.println(asyncCache.getIfPresent(3));


            asyncCache.get(4,this::generateCache).whenCompleteAsync((value,throwable) -> {
                if(throwable != null) {
                    throwable.printStackTrace();
                    System.out.println("cache generate error !!!");
                    return ;
                }

                System.out.printf("generate cache value is %s%n", value);
            });

            CompletableFuture<Object> ifPresent = asyncCache.getIfPresent(4);
            if(ifPresent != null) {
                System.out.println("有值,但是是一个异步的future ...");
                ifPresent.whenComplete((value, error) -> {
                    if(error != null) {
                        error.printStackTrace();
                        System.out.println("value is null !!!");
                        return ;
                    }
                    System.out.println("value is " + value);
                });
            }
            else {
                System.out.println("result is null !!!");
            }


            // 线程不断往里面填充值 ...
            new Thread(() -> {
                for (int i = 0; i < 10; i++) {

                    try {
                        TimeUnit.MILLISECONDS.sleep(10 * i);
                        asyncCache.get(i,this::generateCache);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                System.out.println("生产结束,退出 !!!");

            }).start();


            // 变成同步的方式,直接放入 ...
            // 但是这把阻塞的负担放在了当前线程上 ....
            asyncCache.synchronous().put(5,5);
            System.out.println("synchronous input");
            asyncCache.synchronous().put(5,6);
            System.out.println("synchronous input");
            asyncCache.synchronous().put(5,7);
            System.out.println("synchronous input");
            asyncCache.synchronous().put(5,8);

            Optional.ofNullable(asyncCache.getIfPresent(5))
                            .ifPresent(future -> {
                                future.whenComplete((value,error) -> {
                                    if(error != null) {
                                        error.printStackTrace();
                                        System.out.println("result is null");
                                        return ;
                                    }
                                    System.out.println("last value is " + value);
                                });
                            });


            TimeUnit.SECONDS.sleep(3);

            System.out.println("estimated size is " + asyncCache.synchronous().estimatedSize());
            // 每一次调用会清理 ...
//            asyncCache.getIfPresent(5);
//            asyncCache.synchronous().put(5,5);
            asyncCache.put(5,CompletableFuture.supplyAsync(() -> {
                try {
                    TimeUnit.MILLISECONDS.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                return 5;
            }));

        }

        // 默认都需要 200毫秒
        public Object generateCache(Object key) {

            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if(key instanceof Integer) {
                return (Integer) key * 100;
            }

            return key + "-auto-generated !!!";
        }

    }


    /**
     * 自动异步Cache,是AsyncCache  + AsyncCacheLoader 的能力 ...
     *
     * 对于CacheLoader 的选择取决于 ...
     */
    public static class AutoAsyncCacheTests {
        @Test
        public void test() throws InterruptedException {

            Thread.setDefaultUncaughtExceptionHandler((t, e) -> System.out.println("出现异常,但是处理 ..."));

            ForkJoinWorkerThread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread t, Throwable e) {
                    System.out.println("出现异常,但是不处理");
                }
            });

            // 让fork join  pool 的异常可控

            System.setProperty("java.util.concurrent.ForkJoinPool.common.exceptionHandler",ForkJoinWorkerThreadExceptionHandler.class.getName());






            AsyncLoadingCache<Object, Object> asyncLoadingCache = Caffeine.newBuilder()
                    .maximumSize(5)
                    .expireAfterWrite(2, TimeUnit.SECONDS)

                    // 将同步动作异步执行
                    // 也就是如下代码将会异步执行 ...
//                    .buildAsync(new CacheLoader<Object, Object>() {
//                        @Override
//                        public @Nullable Object load(Object o) throws Exception {
//                            return null;
//                        }
//                    })
                    // 你可以选择构建一个异步缓存元素操作 ...
                    .buildAsync(new AsyncCacheLoader<Object, Object>() {
                        @Override
                        public CompletableFuture<?> asyncLoad(Object o, Executor executor) throws Exception {
                            // 如何执行取决于你 ...
                            return CompletableFuture.supplyAsync(() -> {
                                if (o instanceof Integer) {

                                    try {
                                        TimeUnit.SECONDS.sleep(3);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }

                                    // 这种发生异常的情况 ...
                                    if(((Integer) o).equals(123)) {
                                        throw new NullPointerException();
                                    }

                                    return (Integer) o * 10000;
                                }

                                return o + "-autogenerate-unknown";
                            }, executor);
                        }
                    });

            // 这个应该很快
            CompletableFuture<Object> future = asyncLoadingCache.get("123");
            future.whenComplete((value,error) -> {
                if(error != null) {
                    error.printStackTrace();
                    System.out.println("generate error !!!");
                    return;
                }

                System.out.println("generate value is " + value);
            });


            // 这个需要三秒 ...
            CompletableFuture<Object> future1 = asyncLoadingCache.get(123);
            if(future1 == null) {
                System.out.println(" 123 生成失败 !!");
            }else {
                System.out.println("123 生成中 ....");
                // 先捕获异常 ... 然后处理 ..
                future1.exceptionally(new Function<Throwable, Object>() {
                    @Override
                    public Object apply(Throwable throwable) {
                        if(throwable != null) {
//                            throwable.printStackTrace();
                            System.out.println("123 生成失败,由于异常 ....");
                        }
                        return null;
                    }
                });
            }

            System.out.println("等待4秒");
            TimeUnit.SECONDS.sleep(4);
            System.out.println("等待结束");

            // 三秒之后,重新设置 ..
            asyncLoadingCache.synchronous().put(123,123);


            // 这里是同步 ...
            Optional.ofNullable(asyncLoadingCache.getIfPresent(123))
                    .ifPresent(value -> {
                        // 这样是异步
                        value.thenAcceptAsync(System.out::println);
//                        value.thenAccept(System.out::println);
                    });

            System.out.println("over");
            TimeUnit.SECONDS.sleep(1);
        }
    }

    @Test
    public void captureException() throws InterruptedException {

        CompletableFuture.runAsync(() -> {
            System.out.println("异常即将抛出");
            throw new NullPointerException();
        })
                .exceptionally((error) -> null);

        TimeUnit.SECONDS.sleep(2);


        // 记录并暴露一个异常 ...

        System.Logger logger = System.getLogger("club.smileboy.async.test");

        logger.log(System.Logger.Level.INFO,"error",new NullPointerException());

    }
}
