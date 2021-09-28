package simplerpc;

import java.text.DecimalFormat;
import java.util.concurrent.atomic.LongAdder;

/**
 * @author huangli
 * Created on 2021-09-14
 */
public abstract class BenchBase implements Runnable {

    private final int threadCount;
    private final int time;
    private Thread[] threads;
    private volatile boolean stop = false;
    protected LongAdder successCount = new LongAdder();
    protected LongAdder failCount = new LongAdder();

    public BenchBase(int threadCount, int time) {
        this.threadCount = threadCount;
        this.time = time;
    }

    public void init() throws Exception {
    }

    public void shutdown() throws Exception {
    }

    public void start() throws Exception {
        init();
        threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(this);
            threads[i].start();
        }
        Thread.sleep(time);
        stop = true;
        for (Thread t : threads) {
            t.join();
        }
        shutdown();
        long count = successCount.sum();
        double ops = count * 1.0 / time * 1000;
        System.out.println("success count:" + count + ", ops=" + new DecimalFormat(",###").format(ops));

        count = failCount.sum();
        ops = count * 1.0 / time * 1000;
        System.out.println("fail count:" + count + ", ops=" + new DecimalFormat(",###").format(ops));
    }

    @Override
    public void run() {
        while (!stop) {
            test();
        }
    }

    public abstract void test();
}
