package com.redhat.example.jdg;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;

public class RemoteClientMain {

	private static final String APP_NAME = "datagrid-service";
	private static final String SVC_DNS_NAME = APP_NAME;
	private static final String USER = "user";
	private static final String PASSWORD = "password";

	public static void main(String args[]) throws Exception {
		
		ConfigurationBuilder cfg =
		         ClientConfiguration.create(SVC_DNS_NAME, APP_NAME, USER, PASSWORD);

		final RemoteCacheManager mgr = new RemoteCacheManager(cfg.build());

		System.out.println("### Test start!");
		long t1 = System.currentTimeMillis();

		// Test configurations
		final long count = Integer.parseInt(System.getProperty("count", "10000"));
		final int thread = Integer.parseInt(System.getProperty("thread", "100"));

		final int interval_sec = 1;
		final AtomicInteger success = new AtomicInteger();
		final AtomicInteger success_prev = new AtomicInteger();

		ThreadPoolExecutor pool = new ThreadPoolExecutor(thread, thread, 0, TimeUnit.SECONDS,
				new LinkedBlockingQueue());
		ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);

		class Task implements Runnable {
			int n;
			int retry = 40;

			String cacheName = "default";

			Task(int n) {
				this.n = n;
			}

			public void run() {
				RemoteCache<String, Object> cache = mgr.getCache(cacheName);

				String key = null;
				Object val = null;
				for (long i = 1; i <= count; i++) {
					for (int j = 0; j < retry; j++) {
						try {
							// for normal test.
							key = String.format("%010d", i) // count
									+ String.format("%030d", n); // threads
							val = String.format("%01000d", i);

							cache.put(key, val);

							success.addAndGet(1);

							break; // exit retry loop
						} catch (Exception e) {
							System.out.printf("### Failed: retry=%d, key=%s , ex=%s%n", j, key, e.toString());
							e.printStackTrace();
						}
					} // retry loop
				}
			}
		}

		for (int n = 0; n < thread; n++) {
			pool.execute(new Task(n));
		}
		timer.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				System.out.printf(time() + " $$$ Throughput: %f (tps), %f (%%put)%n",
						(success.get() - success_prev.get()) / (double) interval_sec,
						(double) success.get() * 100 / (count * thread));
				success_prev.set(success.get());
			}

		}, interval_sec, interval_sec, TimeUnit.SECONDS);
		pool.shutdown();
		pool.awaitTermination(60 * 10, TimeUnit.SECONDS);
		timer.shutdown();

		long t2 = System.currentTimeMillis();
		System.out.printf("### Thread: %,d, Count: %,d, Total: %,d%n", thread, count, thread * count);
		System.out.printf("### Success     : %,d puts (no exception occured).%n", success.get());
		System.out.printf("### Test ends: %,d (msec) for %,d puts.%n", t2 - t1, count * thread);
		System.out.printf("### Throughput: %f (tps/thread)%n", ((double) count * 1000) / (t2 - t1));
		System.out.printf("### Throughput: %f (tps)%n", ((double) count * thread * 1000) / (t2 - t1));

		// System.out.println("### Clearing cache.");
		// cache.clear();

	}

	static String time() {
		final String dateformat = "HH:mm:ss";
		final SimpleDateFormat fmt = new SimpleDateFormat(dateformat);
		return fmt.format(new Date());
	}

}
