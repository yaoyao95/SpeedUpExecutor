import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class Main {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
//        main1();
        main2();
    }
    private static void main1() throws InterruptedException, ExecutionException {
        long start = System.currentTimeMillis();
        List<Future<Integer>> futures;
        int count;
        CountDownLatch latch;
        ThreadPoolExecutor executor = new ThreadPoolExecutor(2000, 200000, 10, TimeUnit.SECONDS,
//                new LinkedBlockingQueue<>(1),
                new SynchronousQueue<>(),
                new ThreadPoolExecutor.CallerRunsPolicy());
//        try (executor) {
        executor.prestartAllCoreThreads();
        executor.allowCoreThreadTimeOut(true);
        futures = new ArrayList<>(100);
        count = 200000;
        latch = new CountDownLatch(count);
        for (int i = 0; i < count; i++) {
            int finalI = i;
            futures.add(executor.submit(() -> {
                try {
                    System.out.println(finalI);
                    Thread.sleep(3000);
                    return 1;
                } finally {
                    latch.countDown();
                }
            }));
        }
//        }
        latch.await(10, TimeUnit.SECONDS);
        int sum = 0;
        for (int i = 0; i < count; i++) {
            Future<Integer> future = futures.get(i);
            sum = sum + future.get();
        }
        System.out.println("end time-consuming-"+(System.currentTimeMillis() - start)+"ms");
    }
    public static void main2() throws ExecutionException, InterruptedException {
        long start = System.currentTimeMillis();
        List<Future<Integer>> futures;
        int count;
        CountDownLatch latch;
        ThreadPoolExecutor executor = new ThreadPoolExecutor(2000, 200000, 30, TimeUnit.SECONDS,
//                new LinkedBlockingQueue<>(1),
                new SynchronousQueue<>(),
                new ThreadPoolExecutor.CallerRunsPolicy());

        executor.prestartAllCoreThreads();
        executor.allowCoreThreadTimeOut(true);
        count = 200000;
        latch = new CountDownLatch(count);
        for (int i = 0; i < count; i++) {
            int finalI = i;
            executor.submit(() -> {
                try {
                    System.out.println(finalI);
                    Thread.sleep(3000);
                    return 1;
                } finally {
                    latch.countDown();
                }
            });
        }

        if (latch.await(10, TimeUnit.SECONDS)) {
            System.out.println("all done");
        };
        System.out.println("end time-consuming-"+(System.currentTimeMillis() - start)+"ms");
        Thread.sleep(10000000);
    }
}