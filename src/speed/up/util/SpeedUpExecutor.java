package speed.up.util;

import speed.up.util.mock.SpeedUpMockConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 纯加速执行器，通过线程池，并发执行子任务来加速整个任务的执行。基本上，任务的性能都会提高。<br>
 * 即使线程不够了，也会自动降级为同步顺序执行所有子任务，知识加速效果没了。
 * 依据配置，可能有小概率情况，会有副作用，最坏的情况，耗时不会高于串行执行的2倍。
 */
public class SpeedUpExecutor {
    public SpeedUpExecutor(){
    }
    public SpeedUpExecutor(SpeedUpConfig config){
        this.config = config;
    }
    //
    private SpeedUpConfig config;
    private ThreadPoolExecutor mySpeedUpPoolExecutor;
    private ScheduledExecutorService scheduledMonitorExecutorService;
    private ThreadPoolExecutor mySpeedUpPoolExecutor() {
        if (null == mySpeedUpPoolExecutor) {
            //1、队列满了，为了防止饿死，降级给调用方，用自己的线程（一般来自web容器）来单线阻塞地程顺序执行。同时预估在队列里，如果很快会被执行，可以继续等待，毕竟并发执行更快。
            //2、不管采用什么拒绝策略，用自己的线程来单线阻塞地程顺序执行，那么当前线程就无法继续添加任务到线程池里，线程池里的本任务相关的子查询，即使继续执行，也没用，抛弃了。
            mySpeedUpPoolExecutor = new ThreadPoolExecutor(config.getCorePoolSize(), config.getMaximumPoolSize(), config.getKeepAliveTime(), TimeUnit.SECONDS
//                    ,new ArrayBlockingQueue<>(config.getBlockQueueSize()),new ThreadPoolExecutor.AbortPolicy()
                    ,new SynchronousQueue<>(),new ThreadPoolExecutor.AbortPolicy()
            );
//            mySpeedUpPoolExecutor.prestartAllCoreThreads();
            mySpeedUpPoolExecutor.allowCoreThreadTimeOut(true);
            if (null != config && config.isEnableMonitor()) {
                scheduledMonitorExecutorService = Executors.newScheduledThreadPool(1);
                SpeedUpStatisticWrapper statisticWrapper = new SpeedUpStatisticWrapper();
                scheduledMonitorExecutorService.scheduleAtFixedRate(() -> {
                    SpeedUpStatisticWrapper.monitor(statisticWrapper, mySpeedUpPoolExecutor);
                }, config.getMonitorInitDelayInSec(), config.getMonitorInitPeriodInSec(), TimeUnit.SECONDS);
            }
        }
        return mySpeedUpPoolExecutor;
    }
    
    public void alterMySpeedUpPoolExecutor(Integer corePoolSize, Integer maximumPoolSize, Long keepAliveTime, TimeUnit unit) {
        if (corePoolSize != null) {
            mySpeedUpPoolExecutor.setCorePoolSize(corePoolSize);
        }
        if (maximumPoolSize != null) {
            mySpeedUpPoolExecutor.setMaximumPoolSize(maximumPoolSize);
        }
        if (keepAliveTime != null) {
            mySpeedUpPoolExecutor.setKeepAliveTime(keepAliveTime,unit);
        }
    }

    public SpeedUpConfig getConfig() {
        return config;
    }

    public void aggregateExecute(Runnable... runners) throws InterruptedException {
        long start = System.currentTimeMillis();
//        System.out.println("-------------speed up executor start");
        if (!config.isEnable()) {
            Arrays.stream(runners).forEach(Runnable::run);
            System.out.println("speed up executor switch is off,  degrade to execute Serially."  + " done, Time-consuming-"+(System.currentTimeMillis() - start) + "ms");
            return;
        }

        CountDownLatch latchForPerTask = new CountDownLatch(runners.length);
        boolean ifWorkerThreadEnough = true;
        try{
            synchronized (mySpeedUpPoolExecutor()) {
                if (mySpeedUpPoolExecutor().getMaximumPoolSize() - mySpeedUpPoolExecutor().getActiveCount() >
                        runners.length + config.getBufferThreadWorkSizeForInaccuracyOfActiveCount()) {
                    //worker还足够执行任务
                    for (int subTaskIndex = 0; subTaskIndex < runners.length; subTaskIndex++) {
                        int finalSubTaskIndex = subTaskIndex;
                        mySpeedUpPoolExecutor.submit(() -> {
                            // 线程睡眠 n ms，可以等同于模拟业务耗时n ms
                            try {
                                runners[finalSubTaskIndex].run();
                            } finally {
                                latchForPerTask.countDown();
                            }
                        });
                    }
                } else {
                    //worker不够分配任务，交由当前线程执行 (统计不一定准确，可能有几个进队列了)
                    ifWorkerThreadEnough = false;
                }
            }
        } catch (Exception e) {
            //一般不会进来这里, 进来救表示worker 不够了，原因一般是 统计worker知识预估，不一定准确
            e.printStackTrace();
            Arrays.stream(runners).forEach(Runnable::run);
            System.out.println("thread worker is not enough, but it shouldn't. degrade to execute Serially. "+ " done, Time-consuming-"+(System.currentTimeMillis() - start)+"ms");
            return;
        }
        if (!ifWorkerThreadEnough) {
            Arrays.stream(runners).forEach(Runnable::run);
            System.out.println("thread worker is not enough. degrade to execute Serially. "  + " done, Time-consuming-"+(System.currentTimeMillis() - start) + "ms");
            return;
        }
        //worker 足够的情况
        if (!latchForPerTask.await(config.getMaxTimeLimitForOneAggregateExecuteInMillSec(),TimeUnit.MILLISECONDS)) {
            //---超时---
            Arrays.stream(runners).forEach(Runnable::run);
            //一般要继续执行，而不是降级，等worker执行完。所以，配置Config.maxTimeLimitForOneAggregateExecuteInMillSec这个值大些好。
            System.out.println("time out. over than max limit time-" + config.getMaxTimeLimitForOneAggregateExecuteInMillSec()  + "ms, degrade to execute Serially. done, Time-consuming-"+(System.currentTimeMillis() - start) + "ms");
            return;
        }
        //不超时,正常的情况
        System.out.println("aggregate execute end normally Time-consuming-"+(System.currentTimeMillis() - start)+"ms");
    }

    public static void main(String[] args) throws InterruptedException {
        SpeedUpMockConfig mockConfig = new SpeedUpMockConfig();
        testAvgExecuteTime(mockConfig);
        List<Runnable> runnables = new ArrayList<>();
        ThreadPoolExecutor webContainerThreadPoolExecutor = new ThreadPoolExecutor(1000, 1000, 30000, TimeUnit.SECONDS,
//                new LinkedBlockingQueue<>(1),
//                    new SynchronousQueue<>(),
                    new ArrayBlockingQueue<>(mockConfig.taskCount),
                    new ThreadPoolExecutor.CallerRunsPolicy());
        webContainerThreadPoolExecutor.prestartAllCoreThreads();
        webContainerThreadPoolExecutor.allowCoreThreadTimeOut(true);

        for (int i = 0; i < mockConfig.queryCountPerTask; i++) {
            runnables.add(() -> {
                SpeedUpMockConfig.sleep(mockConfig.calcExecuteTimeForOneQuery());
            });
        }
        SpeedUpConfig config = new SpeedUpConfig();
        config.setMonitorInitPeriodInSec(1);
        SpeedUpExecutor executor = new SpeedUpExecutor(config);
        for (int taskIndex = 0; taskIndex < mockConfig.taskCount; taskIndex++) {
            //平均多少毫秒，单个task请求
            Thread.sleep((long) (Math.random() * mockConfig.avgIntervalPerTaskMillSec * 2));
            mockConfig.webContainerThreadPoolExecutor().submit(() -> {
                try {
                    executor.aggregateExecute(runnables.toArray(Runnable[]::new));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    mockConfig.latchForAllTask.countDown();
                }
            });
        }
        if (mockConfig.latchForAllTask.await(10000,TimeUnit.SECONDS)) {
            System.out.println("-------------All task done-------------");
        } else {
            System.out.println("************** 总任务 超时");
        }

//        executor.aggregateExecute(() -> {
//            SpeedUpMockConfig.sleep(mockConfig.calcExecuteTimeForOneQuery());
//        },() -> {
//            SpeedUpMockConfig.sleep(mockConfig.calcExecuteTimeForOneQuery());
//        },() -> {
//            SpeedUpMockConfig.sleep(mockConfig.calcExecuteTimeForOneQuery());
//        });
//
//        executor.aggregateExecute(runnables.toArray(Runnable[]::new));

        int a1 = 1; int b1 = 2; AtomicInteger sum = new AtomicInteger();
        int a2 = 3; int b2 = 4;
        ArrayList<String> strs = new ArrayList<>();
        executor.aggregateExecute(() ->{
            sum.set(a1 + b1);
        },() -> {
            sum.set(a2 + b2);
        },() -> {
            System.out.println("test");
        },() -> {
            strs.add(String.valueOf(a1));
        });
        System.out.println(sum + "  " + strs);
    }

    private static void testAvgExecuteTime(SpeedUpMockConfig mockConfig) {
        long sum1 = 0,sum2 = 0,sum3 = 0,sum4 = 0,sum5 = 0;
        int calcCount = 10_000_00;
        for (int i = 0; i < calcCount; i++) {
            sum1 += (long) mockConfig.calcQueryTimeForOneTaskSerially();
            sum2 += (long) mockConfig.calcQueryTimeForNonVein();
            sum3 += (long) mockConfig.calcQueryTimeForOneVein();
            sum4 += (long) mockConfig.calcExecuteTimeForOneQuery();
            sum5 += (long) mockConfig.calcQueryTimeForOneTaskCompleteConcurrently();
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

}
