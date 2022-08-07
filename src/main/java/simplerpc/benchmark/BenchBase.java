package simplerpc.benchmark;

import java.text.DecimalFormat;
import java.util.concurrent.atomic.LongAdder;

/**
 * @author huangli
 * Created on 2021-09-14
 */
public abstract class BenchBase {

    protected final int threadCount;
    private final long testTime;
    private final long warmupTime;
    private Thread[] threads;
    protected volatile boolean stop = false;
    protected LongAdder successCount = new LongAdder();
    protected LongAdder failCount = new LongAdder();

    public BenchBase(int threadCount, long testTime) {
        this(threadCount, testTime, 5000);
    }

    public BenchBase(int threadCount, long testTime, long warmupTime) {
        this.threadCount = threadCount;
        this.testTime = testTime;
        this.warmupTime = warmupTime;
    }

    public void init() throws Exception {
    }

    public void shutdown() throws Exception {
    }

    public void start() throws Exception {
        init();
        threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            int threadIndex = i;
            threads[i] = new Thread(() -> run(threadIndex));
            threads[i].start();
        }
        Thread.sleep(warmupTime);
        long warmupCount = successCount.sum();
        long warmupFailCount = failCount.sum();
        Thread.sleep(testTime);
        stop = true;
        long sc = successCount.sum() - warmupCount;
        long fc = failCount.sum() - warmupFailCount;
        for (Thread t : threads) {
            t.join();
        }
        shutdown();

        double ops = sc * 1.0 / testTime * 1000;
        System.out.println("success sc:" + sc + ", ops=" + new DecimalFormat(",###").format(ops));

        ops = fc * 1.0 / testTime * 1000;
        System.out.println("fail sc:" + fc + ", ops=" + new DecimalFormat(",###").format(ops));
    }

    public void run(int threadIndex) {
        while (!stop) {
            test(threadIndex);
        }
    }

    public abstract void test(int threadIndex);
}
