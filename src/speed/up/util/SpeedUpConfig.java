package speed.up.util;

import java.util.concurrent.TimeUnit;

public class SpeedUpConfig {
    private boolean enable = true;
    /**io 密集型的，建议corePoolSize 和 maximumPoolSize一样大。 因为空闲时会回收。 cpu密集型，两者都设小点，不大于CPU核心数为好。**/
    private int corePoolSize = 300;
    /**io 密集型的，建议corePoolSize 和 maximumPoolSize一样大。 因为空闲时会回收。 cpu密集型，两者都设小点，不大于CPU核心数为好。**/
    private int maximumPoolSize = 300;
    private long keepAliveTime = 60;
    /**是否使用同步队列，否则使用ArrayBlockingQueue*/
    private boolean useSynchronousQueue = true;
    /** 针对io密集型情况:<br>
     * useSynchronousQueue为true时，blockQueueSizeForArray设置是无效的。但需要配置 bufferThreadWorkSizeForInaccuracyOfActiveCount，一般5即可。<br>
     * useSynchronousQueue为false时需要设置。 blockQueueSizeForArray一般设置为接近，但大于一个任务可拆分成子任务的数值，比如一个task的子任务数的2倍 <br><br>
     *
     * 这两个配置，都是 因统计工作线程不准确，认为还有空闲的工作线程能足够被分配去执行子任务，但实际上空闲线程不足，有部分子任务没有被分配工作线程。<br>
     * 对应的场景都是超高并发，实际qps远大于预估时，才可能触发这种有部分子任务被分配工作线程了，而部分没有情况。 设置这两参数能微调性能，对这种极端情况，有一定效果。<br><br>
     *
     * 1.采用同步队列的情况（即队列的size是空），遇到上述极端场景时，预留少量几个worker作为buffer，就能极大概率避免有些任务在线程池被分配了工作线程，在执行了，而有些子任务放不进线程池，没分到工作线程，
     * 而降级为用调用方线程来串行执行所有子任务，而导致这个任务变慢。浪费了分配了子任务的工作线程，白执行了这部分的子任务。 但buffer不能设置太大，使有效工作线程减少太多<br>
     *
     * 2、使用有界队列时，遇到上述极端场景时，若能等待一定时间（maxTimeLimitForOneAggregateExecuteInMillSec），等所有子任务执行完，则响应更快。
     * 但队列不能太大，大了有可能会饿死。导致整体更慢.所以需要一个合适的大小。(虽然理论上一般占用的最大的队列，不会大于子任务数)<br>
     * */
    private int bufferThreadWorkSizeForInaccuracyOfActiveCount = 5;
    /**见配置bufferThreadWorkSizeForInaccuracyOfActiveCount说明*/
    private int blockQueueSizeForArray = 50;


    /**单个任务的最大耗时限制，超过则降级，用当前线程顺序查询。认为线程池、并发出了问题，若认为没有问题，可以把这个数字设置很大*/
    private long maxTimeLimitForOneAggregateExecuteInMillSec = 20_000;
    // ---监控配置---
    private boolean enableMonitor = true;
    private int monitorInitDelayInSec = 1;
    private int monitorInitPeriodInSec = 60 * 10;

    private boolean allowCoreThreadTimeOut = true;
    private TimeUnit timeUnit = TimeUnit.SECONDS;


    public int getCorePoolSize() {
        return corePoolSize;
    }

    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    public void setMaximumPoolSize(int maximumPoolSize) {
        this.maximumPoolSize = maximumPoolSize;
    }

    public long getKeepAliveTime() {
        return keepAliveTime;
    }

    public void setKeepAliveTime(long keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    public long getMaxTimeLimitForOneAggregateExecuteInMillSec() {
        return maxTimeLimitForOneAggregateExecuteInMillSec;
    }

    public void setMaxTimeLimitForOneAggregateExecuteInMillSec(long maxTimeLimitForOneAggregateExecuteInMillSec) {
        this.maxTimeLimitForOneAggregateExecuteInMillSec = maxTimeLimitForOneAggregateExecuteInMillSec;
    }

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public boolean isEnableMonitor() {
        return enableMonitor;
    }

    public void setEnableMonitor(boolean enableMonitor) {
        this.enableMonitor = enableMonitor;
    }

    public int getMonitorInitDelayInSec() {
        return monitorInitDelayInSec;
    }

    public void setMonitorInitDelayInSec(int monitorInitDelayInSec) {
        this.monitorInitDelayInSec = monitorInitDelayInSec;
    }

    public int getMonitorInitPeriodInSec() {
        return monitorInitPeriodInSec;
    }

    public void setMonitorInitPeriodInSec(int monitorInitPeriodInSec) {
        this.monitorInitPeriodInSec = monitorInitPeriodInSec;
    }

    public int getBlockQueueSizeForArray() {
        return blockQueueSizeForArray;
    }

    public int getBufferThreadWorkSizeForInaccuracyOfActiveCount() {
        return bufferThreadWorkSizeForInaccuracyOfActiveCount;
    }

    public void setBufferThreadWorkSizeForInaccuracyOfActiveCount(int bufferThreadWorkSizeForInaccuracyOfActiveCount) {
        this.bufferThreadWorkSizeForInaccuracyOfActiveCount = bufferThreadWorkSizeForInaccuracyOfActiveCount;
    }

    public boolean isUseSynchronousQueue() {
        return useSynchronousQueue;
    }

    public void setUseSynchronousQueue(boolean useSynchronousQueue) {
        this.useSynchronousQueue = useSynchronousQueue;
    }

    public void setBlockQueueSizeForArray(int blockQueueSizeForArray) {
        this.blockQueueSizeForArray = blockQueueSizeForArray;
    }

    public boolean isAllowCoreThreadTimeOut() {
        return allowCoreThreadTimeOut;
    }

    public void setAllowCoreThreadTimeOut(boolean allowCoreThreadTimeOut) {
        this.allowCoreThreadTimeOut = allowCoreThreadTimeOut;
    }
}
