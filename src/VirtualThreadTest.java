import java.lang.management.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

// 虚拟线程
public class VirtualThreadTest {
    static class Config{
        //单个任务的最大耗时限制，超过则降级，用当前线程顺序查询。认为线程池、并发出了问题，若认为没有问题，可以把这个数字设置很大
        static final int maxTimeLimitForOneTaskInMillSec = 25_000;
        //总任务数
        static final int taskCount = 100_000;
        //平均多少毫秒，单个task请求
        static int avgIntervalPerTaskMillSec = 10;

        //每个任务多少个子查询 1000
        static final int queryCountPerTask = 20;
        //每个子查询平均耗时, 用于假定普通情况下，一个查询，耗时范围为[0.25,1.25)之间
        static final int avgPerQueryTimeConsumingMillSec = 500;
        //毛刺子查询最大查询耗时， 用于发生毛刺的情况下，假定毛刺耗时范围为[0.25Max,Max)
        static final int maxVeiningQueryTimeConsuming = 4000;
        //每个子查询，平均毛刺概率
        static final double avgRateForVein = 0.10;
        //监控间隔
        static final int intervalOfMonitorMillSec = 5000;
        static final CountDownLatch latchForAllTask = new CountDownLatch(Config.taskCount);
        //模拟web容器的线程池
        static ThreadPoolExecutor webContainerThreadPoolExecutor;
        static {
            webContainerThreadPoolExecutor = new ThreadPoolExecutor(1000, 1000, 30000, TimeUnit.SECONDS,
//                new LinkedBlockingQueue<>(1),
//                    new SynchronousQueue<>(),
                    new ArrayBlockingQueue<>(taskCount),
                    new ThreadPoolExecutor.CallerRunsPolicy());
            webContainerThreadPoolExecutor.prestartAllCoreThreads();
            webContainerThreadPoolExecutor.allowCoreThreadTimeOut(true);
        }

    }

    static ExecutorService myVirtualThreadExecutor;
    public static void initVTE() {
        myVirtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }
    static final ThreadPoolExecutor myNormalThreadPoolExecutor = new ThreadPoolExecutor(10, 500, 30000, TimeUnit.SECONDS
//                ,new LinkedBlockingQueue<>(1),
//                ,new SynchronousQueue<>(),
//                ,new ThreadPoolExecutor.CallerRunsPolicy()
            ,new ArrayBlockingQueue<>(10)
            ,new ThreadPoolExecutor.AbortPolicy()
    );;
    public static void initNTE() {
//        myNormalThreadPoolExecutor = new ThreadPoolExecutor(10, 350, 30000, TimeUnit.SECONDS
////                ,new LinkedBlockingQueue<>(1),
////                ,new SynchronousQueue<>(),
////                ,new ThreadPoolExecutor.CallerRunsPolicy()
//                ,new ArrayBlockingQueue<>(10)
//                ,new ThreadPoolExecutor.AbortPolicy()
//        );
        //1、队列满了，为了防止饿死，降级给调用方，用自己的线程（一般来自web容器）来单线阻塞地程顺序执行。同时预估在队列里，如果很快会被执行，可以继续等待，毕竟并发执行更快。
        //2、不管采用什么拒绝策略，用自己的线程来单线阻塞地程顺序执行，那么当前线程就无法继续添加任务到线程池里，线程池里的本任务相关的子查询，即使继续执行，也没用，抛弃了。
        myNormalThreadPoolExecutor.prestartAllCoreThreads();
        myNormalThreadPoolExecutor.allowCoreThreadTimeOut(true);
    }

    public static void main(String[] args) throws InterruptedException {
//        testAvgExecuteTime();
//        virtualThreadTestMain();
        normalMultiThreadTestMain();
    }

    private static void testAvgExecuteTime() {
        long sum1 = 0,sum2 = 0,sum3 = 0,sum4 = 0,sum5 = 0;
        int calcCount = 10_000_00;
        for (int i = 0; i < calcCount; i++) {
            sum1 += (long) calcQueryTimeForOneTaskSerially();
            sum2 += (long) calcQueryTimeForNonVein();
            sum3 += (long) calcQueryTimeForOneVein();
            sum4 += (long) calcExecuteTimeForOneQuery();
            sum5 += (long) calcQueryTimeForOneTaskCompleteConcurrently();
        }
        double calcCountD = calcCount + 0.0D;
        System.out.println("单线程顺序执行平均耗时："+ sum1 / calcCountD);
        System.out.println("所有只任务完全并发同时执行, 任务完成平均耗时：（即优化为并发方式后的效果) "+ sum5 / calcCountD);
        System.out.println("---------其他耗时----");
        System.out.println("单个子查询毛刺情况下平均耗时："+ sum3 / calcCountD);
        System.out.println("单个子查询非毛刺情况下平均耗时："+ sum2 / calcCountD);
        System.out.println("单个子查询总的平均耗时："+ sum4 / calcCountD);
        System.out.println("-------------");
    }

    private static void virtualThreadTestMain() throws InterruptedException {
        initVTE();
        // 开启一个线程来监控当前的平台线程（系统线程）总数
        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.scheduleAtFixedRate(ThreadStatistic::saveMaxThreadNum, 0, Config.intervalOfMonitorMillSec, TimeUnit.MILLISECONDS);

        long start = System.currentTimeMillis();
        for (int taskIndex = 0; taskIndex < Config.taskCount; taskIndex++) {
            //平均多少毫秒，单个task请求
            Thread.sleep((long) (Math.random() * Config.avgIntervalPerTaskMillSec * 2));
            int finalTaskIndex = taskIndex;
            Config.webContainerThreadPoolExecutor.submit(() ->{
                try {
                    virtualThreadExecuteOneTask(finalTaskIndex);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    Config.latchForAllTask.countDown();
                }
            });
        }
        if (Config.latchForAllTask.await(10000,TimeUnit.SECONDS)) {
            System.out.println("-------------All task done-------------");
        } else {
            System.out.println("************** 总任务 超时");
        }
        System.out.println("max：" + ThreadStatistic.getMaxStatisTic() + " platform thread/os thread");
        System.out.println("all statistic : " + ThreadStatistic.getStatistics().toString());
        System.out.printf("totalMillis：%dms\n", System.currentTimeMillis() - start);
    }

    private static void virtualThreadExecuteOneTask(int taskIndex) throws InterruptedException {
        long start = System.currentTimeMillis();
        CountDownLatch latchForPerTask = new CountDownLatch(Config.queryCountPerTask);
        List<Future<Integer>> futures = new ArrayList<>(Config.queryCountPerTask);
        for (int j = 0; j < Config.queryCountPerTask; j++) {
            futures.add(myVirtualThreadExecutor.submit(() -> {
                // 线程睡眠 n ms，可以等同于模拟业务耗时n ms
                try {
                    //单个查询
//                    TimeUnit.MILLISECONDS.sleep((long) (Math.random() * Config.avgPerQueryTimeConsumingMillSec * 2));
                    TimeUnit.MILLISECONDS.sleep(Config.avgPerQueryTimeConsumingMillSec);
                    return 1;
                } finally {
                    latchForPerTask.countDown();
                }
            }));
        }
        if (!latchForPerTask.await(Config.maxTimeLimitForOneTaskInMillSec,TimeUnit.MILLISECONDS)) {
            System.out.println("************** 超时, task " + taskIndex);
            System.exit(0);
        }
        //聚合结果
        Integer sum = futures.stream().map(integerFuture -> {
            try {
                return integerFuture.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).reduce(Integer::sum).get();
        System.out.println("-------------task "+ taskIndex + " done, return-"+sum+" ,耗时-"+(System.currentTimeMillis() - start)+"-------------");
    }


    private static void normalMultiThreadTestMain() throws InterruptedException {
        initNTE();
        // 开启一个线程来监控当前的平台线程（系统线程）总数
        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.scheduleAtFixedRate(ThreadStatistic::saveMaxThreadNum, 0, Config.intervalOfMonitorMillSec, TimeUnit.MILLISECONDS);

        long start = System.currentTimeMillis();
        for (int taskIndex = 0; taskIndex < Config.taskCount; taskIndex++) {
            //平均多少毫秒，单个task请求
            Thread.sleep((long) (Math.random() * Config.avgIntervalPerTaskMillSec * 2));
            int finalTaskIndex = taskIndex;
            Config.webContainerThreadPoolExecutor.submit(() -> {
                try {
                    normalMultiThreadExecuteOneTask(finalTaskIndex);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    Config.latchForAllTask.countDown();
                }
            });
        }
        if (Config.latchForAllTask.await(10000,TimeUnit.SECONDS)) {
            System.out.println("-------------All task done-------------");
        } else {
            System.out.println("************** 总任务 超时");
        }
        System.out.println("max：" + ThreadStatistic.getMaxStatisTic() + " platform thread/os thread");
        System.out.println("all statistic : " + ThreadStatistic.getStatistics().toString());
        System.out.printf("totalMillis：%dms\n", System.currentTimeMillis() - start);
    }


    private static void normalMultiThreadExecuteOneTask(int taskIndex) throws InterruptedException {
        long start = System.currentTimeMillis();
        CountDownLatch latchForPerTask = new CountDownLatch(Config.queryCountPerTask);
        List<Future<Integer>> futures = new ArrayList<>(Config.queryCountPerTask);
        boolean ifWorkerThreadEnough = true;
        try{
            synchronized (myNormalThreadPoolExecutor) {
                if (myNormalThreadPoolExecutor.getMaximumPoolSize() - myNormalThreadPoolExecutor.getActiveCount() > Config.queryCountPerTask) {
                    //worker还足够执行任务
                    for (int j = 0; j < Config.queryCountPerTask; j++) {
                        futures.add(myNormalThreadPoolExecutor.submit(() -> {
                            // 线程睡眠 n ms，可以等同于模拟业务耗时n ms
                            try {
                                long executeTime = calcExecuteTimeForOneQuery();
                                Thread.sleep(executeTime);
                                return 1;
                            } finally {
                                latchForPerTask.countDown();
                            }
                        }));
                    }
                } else {
                    //worker不够分配任务，交由当前线程执行
                    ifWorkerThreadEnough = false;
                }
            }
        } catch (Exception e) {
//            System.exit(0);
            Thread.sleep(calcQueryTimeForOneTaskSerially());
            System.out.println("-------------队列满了降级task "+ taskIndex + " done, return-"+Config.queryCountPerTask+" ,耗时-"+(System.currentTimeMillis() - start)+"-------------");
            return;
        }
        if (ifWorkerThreadEnough && !latchForPerTask.await(Config.maxTimeLimitForOneTaskInMillSec,TimeUnit.MILLISECONDS)) {
//            System.exit(0);
            Thread.sleep(calcQueryTimeForOneTaskSerially());
            //一般要继续执行，而不是降级，等worker执行完。所以，配置Config.maxTimeLimitForOneTaskInMillSec需要大些
            System.out.println("-------------超时降级task "+ taskIndex + " done, return-"+Config.queryCountPerTask+" ,耗时-"+(System.currentTimeMillis() - start)+"-------------");
            return;
        }
        if (!ifWorkerThreadEnough) {
            Thread.sleep(calcQueryTimeForOneTaskSerially());
            System.out.println("-------------线程worker不够，降级task "+ taskIndex + " done, return-"+Config.queryCountPerTask+" ,耗时-"+(System.currentTimeMillis() - start)+"-------------");
            return;
        }
        //聚合结果
        Integer sum = futures.stream().map(integerFuture -> {
            try {
                return integerFuture.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).reduce(Integer::sum).get();
        System.out.println("-------------task "+ taskIndex + " done, return-"+sum+" ,耗时-"+(System.currentTimeMillis() - start)+"-------------");
    }

    private static long calcExecuteTimeForOneQuery() {
        //单个查询
        //                    TimeUnit.MILLISECONDS.sleep((long) (Math.random() * Config.avgPerQueryTimeConsumingMillSec * 2));
        if (Math.random() - Config.avgRateForVein < 0) {
            //发生毛刺
            long queryTimeForOneVein = calcQueryTimeForOneVein();
            return queryTimeForOneVein;
        } else {
            //非毛刺，普通耗时
            long queryTimeForNonVein = calcQueryTimeForNonVein();
            return queryTimeForNonVein;
        }
    }

    private static long calcQueryTimeForOneTaskSerially() {
        long sleepTime = 0;
        for (int i = 0; i < Config.queryCountPerTask; i++) {
            if (Math.random() - Config.avgRateForVein < 0) {
                sleepTime += calcQueryTimeForOneVein();
            } else {
                sleepTime += calcQueryTimeForNonVein();
            }
        }
        return sleepTime;
    }

    private static long calcQueryTimeForOneTaskCompleteConcurrently() {
        //完全并发的情况下（同时执行），主要看最慢的那个子任务的耗时，考虑到线程切换的耗时，+5毫秒
        long maxExecuteTime = 0;
        for (int i = 0; i < Config.queryCountPerTask; i++) {
            if (Math.random() - Config.avgRateForVein < 0) {
                maxExecuteTime = Math.max(maxExecuteTime, calcQueryTimeForOneVein());
            } else {
                maxExecuteTime = Math.max(maxExecuteTime,calcQueryTimeForNonVein());
            }
        }
        return maxExecuteTime + 5;
    }

    private static long calcQueryTimeForNonVein() {
        return (long) (Math.random() * Config.avgPerQueryTimeConsumingMillSec * (1.25 - 0.25) + Config.avgPerQueryTimeConsumingMillSec * 0.25);
    }

    private static long calcQueryTimeForOneVein() {
        return (long) ((Math.random() * Config.maxVeiningQueryTimeConsuming * (1 - 0.25)) + Config.maxVeiningQueryTimeConsuming * 0.25);
    }
}

class ThreadTest {
    static List<Integer> list = new ArrayList<>();
    public static void main(String[] args) {
        // 开启一个线程来监控当前的平台线程（系统线程）总数
        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            ThreadInfo[] threadInfo = threadBean.dumpAllThreads(false, false);
            saveMaxThreadNum(threadInfo.length);
        }, 1, 1, TimeUnit.SECONDS);

        long start = System.currentTimeMillis();
        ExecutorService executor = Executors.newFixedThreadPool(200);
        for (int i = 0; i < VirtualThreadTest.Config.taskCount; i++) {
            executor.submit(() -> {
                try {
                    // 线程睡眠 10ms，可以等同于模拟业务耗时10ms
                    TimeUnit.MILLISECONDS.sleep(10);
                } catch (InterruptedException e) {
                }
            });
        }
        executor.close();
        System.out.println("max：" + list.get(0) + " platform thread/os thread");
        System.out.printf("totalMillis：%dms\n", System.currentTimeMillis() - start);
    }

    // 保存平台线程的创建的最大总数
    public static List<Integer> saveMaxThreadNum(int num) {
        if (list.isEmpty()) {
            list.add(num);
        } else {
            Integer integer = list.get(0);
            if (num > integer) {
                list.add(0, num);
            }
        }
        return list;
    }
}

class ThreadStatistic {
    static long lastTimeNotDoneTaskCount = -1;
    static class Statistic{
        public double qps;
        public Double lowestQps = 1000_000D;
        public Double fastestQps = 0D;
        public Integer queueCount = 0;
        public long osThreadCount;
        public long totalMemoryInMB;
        public long freeMemoryInMB;
        public long maxMemoryInMB;

//        public BlockingQueue queueDetail;
        public MemoryUsage heapMemoryUsage;
        public MemoryUsage nonHeapMemoryUsage;

        @Override
        public String toString() {
            return "Statistic{" +
                    "qps=" + qps +
                    ", lowestQps=" + lowestQps +
                    ", fastestQps=" + fastestQps +
                    ", queueCount=" + queueCount +
                    ", osThreadCount=" + osThreadCount +
                    ", totalMemoryInMB=" + totalMemoryInMB +
                    ", freeMemoryInMB=" + freeMemoryInMB +
                    ", maxMemoryInMB=" + maxMemoryInMB +
//                    ", queueDetail=" + queueDetail +
                    ", heapMemoryUsage=" + heapMemoryUsage +
                    ", nonHeapMemoryUsage=" + nonHeapMemoryUsage +
                    '}';
        }
    }
    static List<Statistic> statistics = new ArrayList<>(100000);
    static Statistic maxStatisTic = new Statistic();

    public static List<Statistic> getStatistics() {
        return statistics;
    }
    public static Statistic getMaxStatisTic(){
        return maxStatisTic;
    }

    // 保存平台线程的创建的最大总数
    public static void saveMaxThreadNum() {
        int osThreadCount = ManagementFactory.getThreadMXBean().getThreadCount();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapMemoryUsage = memoryMXBean.getNonHeapMemoryUsage();
        long maxMemoryInMB = Runtime.getRuntime().maxMemory() / 1024 / 1024;
        long totalMemoryInMB = Runtime.getRuntime().totalMemory() / 1024 / 1024;
        long freeMemoryInMB = Runtime.getRuntime().freeMemory() / 1024 / 1024;

        Statistic statistic = new Statistic();
        statistic.osThreadCount = osThreadCount;
        statistic.maxMemoryInMB = maxMemoryInMB;
        statistic.totalMemoryInMB = totalMemoryInMB;
        statistic.freeMemoryInMB = freeMemoryInMB;
        statistic.heapMemoryUsage = heapMemoryUsage;
        statistic.nonHeapMemoryUsage = nonHeapMemoryUsage;
        statistics.add(statistic);

        maxStatisTic.osThreadCount = Math.max(maxStatisTic.osThreadCount, osThreadCount);
        maxStatisTic.maxMemoryInMB = Math.max(maxStatisTic.maxMemoryInMB, maxMemoryInMB);
        maxStatisTic.totalMemoryInMB = Math.max(maxStatisTic.totalMemoryInMB, totalMemoryInMB);
        maxStatisTic.freeMemoryInMB = Math.max(maxStatisTic.freeMemoryInMB, freeMemoryInMB);

        long currNotDoneCount = VirtualThreadTest.Config.latchForAllTask.getCount();
        lastTimeNotDoneTaskCount = -1 == lastTimeNotDoneTaskCount ? currNotDoneCount : lastTimeNotDoneTaskCount;
        statistic.qps = (lastTimeNotDoneTaskCount - currNotDoneCount) / (double) VirtualThreadTest.Config.intervalOfMonitorMillSec * 1000D;
        lastTimeNotDoneTaskCount = currNotDoneCount;
        if (Math.abs(statistic.qps) > 0.00001) {
            maxStatisTic.lowestQps = Math.min(maxStatisTic.lowestQps, statistic.qps);
            maxStatisTic.fastestQps = Math.max(maxStatisTic.fastestQps, statistic.qps);
        }
        statistic.lowestQps = null;
        statistic.fastestQps = null;

        BlockingQueue<Runnable> queue = VirtualThreadTest.Config.webContainerThreadPoolExecutor.getQueue();
        statistic.queueCount = queue.size();
//        statistic.queueDetail = queue;
        maxStatisTic.queueCount = Math.max(statistic.queueCount, maxStatisTic.queueCount);

        System.out.println("current max statistic: " + maxStatisTic.toString());
        System.out.println("current statistic: " + statistic.toString());
    }
}