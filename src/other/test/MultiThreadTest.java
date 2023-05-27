package other.test;

import java.util.List;
import java.util.concurrent.*;



public class MultiThreadTest {
    public static final int taskCount = 1000_000;
    public static void main(String[] args) throws InterruptedException {
        main2();
//        main3();
    }

    private static void main1() throws InterruptedException {
        ExecutorService executorVPT = Executors.newVirtualThreadPerTaskExecutor();
        for (int i = 0; i < taskCount; i++) {
            int finalI = i;
            executorVPT.submit(() -> {
                System.out.println("VPT" + finalI);
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return finalI;
            });
        }
        Thread.sleep(7000);
        System.out.println("--------------------done-------------------");

        Thread.sleep(100000);
    }

    private static void main2() throws InterruptedException {
        long start = System.currentTimeMillis();
        List<Future<Integer>> futures;
        int count;
        CountDownLatch latch;
        ThreadPoolExecutor executor = new ThreadPoolExecutor(2000, 1000000, 3000, TimeUnit.SECONDS,
//                new LinkedBlockingQueue<>(1),
                new SynchronousQueue<>(),
                new ThreadPoolExecutor.CallerRunsPolicy());

//        executor.prestartAllCoreThreads();
        executor.allowCoreThreadTimeOut(true);

        count = taskCount;
        latch = new CountDownLatch(count);
        for (int i = 0; i < count; i++) {
            int finalI = i;
            executor.submit(() -> {
                try {
                    System.out.println(finalI);
                    Thread.sleep(70000);
                    System.out.println("after sleep" + finalI);
                    return 1;
                } finally {
                    latch.countDown();
                }
            });
        }

        if (latch.await(1000, TimeUnit.SECONDS)) {
            System.out.println("--------------------all done-------------------");
        };
        System.out.println("end time-consuming-"+(System.currentTimeMillis() - start)+"ms");
        Thread.sleep(10000000);
    }

    private static void main3() throws InterruptedException {
        long start = System.currentTimeMillis();
        List<Future<Integer>> futures;
        int count;
        CountDownLatch latch;
        ExecutorService executor =  Executors.newVirtualThreadPerTaskExecutor();


        count = taskCount;
        latch = new CountDownLatch(count);
        for (int i = 0; i < count; i++) {
            int finalI = i;
            executor.submit(() -> {
                try {
                    System.out.println(finalI);
                    Thread.sleep(3000);
                    System.out.println("after sleep" + finalI);
                    return 1;
                } finally {
                    latch.countDown();
                }
            });
        }

        if (latch.await(1000, TimeUnit.SECONDS)) {
            System.out.println("--------------------all done-------------------");
        };
        System.out.println("end time-consuming-"+(System.currentTimeMillis() - start)+"ms");
        Thread.sleep(10000000);
    }
}
