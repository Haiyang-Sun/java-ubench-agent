/*
 * Copyright 2016 Charles University in Prague
 * Copyright 2016 Vojtech Horky
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cz.cuni.mff.d3s.perf;

import java.io.*;
import java.util.List;

import org.junit.*;

public class MultiContextThreadTest {
	public static class Runner extends Thread {
		public static volatile int BLACKHOLE = 0;
		
		public static void action() {
			for (int i = 0; i < 1000; i++) {
				BLACKHOLE += i;
			}
		}
		
		@Override
		public void run() {
			action();
		}
	}
	
	public static class ThreadWrapper extends Thread {
		private final int threadCount;
		
		public ThreadWrapper(int count) {
			threadCount = count;
		}
		
		@Override
		public void run() {
			Thread[] threads = new Thread[threadCount];
			for (int i = 0; i < threads.length; i++) {
				threads[i] = new Runner();
			}
			
			for (Thread t : threads) {
				t.start();
			}
			
			for (int i = 0; i < 5; i++) {
				Runner.action();
			}
			
			for (Thread t : threads) {
				try {
					t.join();
				} catch (InterruptedException e) {
					
				}
			}
		}
	}
	
	@Before
	public void warmUp() {
		for (int i = 0; i < 100; i++) {
			runThreads(2);
		}
	}
	
	@Test
	public void threadNoInheritance() throws IOException, InterruptedException {
		final String[] events = new String[] { "PAPI_TOT_INS" };
		long ctx = MultiContextBenchmark.init(2, events);
		
		MultiContextBenchmark.start(ctx);
		runThreads(1);
		MultiContextBenchmark.stop(ctx);
		
		MultiContextBenchmark.start(ctx);
		runThreads(100);
		MultiContextBenchmark.stop(ctx);
		

		List<long[]> results = MultiContextBenchmark.getResults(ctx).getData();
		long resultOneThread = results.get(0)[0];
		long resultManyThreads = results.get(1)[0];
		
		MultiContextTestUtils.assertGreaterThan("Instruction count must be positive (one thread)", 0, resultOneThread);
		MultiContextTestUtils.assertGreaterThan("Instruction count must be positive (many threads)", 0, resultManyThreads);
		
		double orderOfMagnitudeOneThread = Math.log10(resultOneThread);
		double orderOfMagnitudeManyThreads = Math.log10(resultManyThreads);
		
		Assert.assertEquals(orderOfMagnitudeOneThread, orderOfMagnitudeManyThreads, 0.25);
	}
	
	@Test
	public void threadInheritance() throws IOException, InterruptedException {
		final String[] events = new String[] { "PAPI_TOT_INS" };
		long ctx = MultiContextBenchmark.init(2, events, MultiContextBenchmark.THREAD_INHERIT);
		
		MultiContextBenchmark.start(ctx);
		runThreads(1);
		MultiContextBenchmark.stop(ctx);
		
		MultiContextBenchmark.start(ctx);
		runThreads(1000);
		MultiContextBenchmark.stop(ctx);
		

		List<long[]> results = MultiContextBenchmark.getResults(ctx).getData();
		long resultOneThread = results.get(0)[0];
		long resultManyThreads = results.get(1)[0];
		
		MultiContextTestUtils.assertGreaterThan("Instruction count must be positive (one thread)", 0, resultOneThread);
		MultiContextTestUtils.assertGreaterThan("Instruction count must be positive (many threads)", 0, resultManyThreads);
		
		double orderOfMagnitudeOneThread = Math.log10(resultOneThread);
		double orderOfMagnitudeManyThreads = Math.log10(resultManyThreads);
		
		Assert.assertEquals(orderOfMagnitudeOneThread + 3, orderOfMagnitudeManyThreads, 1);
	}
	
	// The following two tests assert that we do not accumulate the
	// results. Because the number of instructions varies across executions
	// (JIT, GC etc.) we again use logarithmic scaling to assert that
	// we are at the same order of magnitude.
	//
	// We then assert that the first execution was either slower than the
	// last one (i.e. assume warm-up) or that they are both at the
	// same order of magnitude. Definitely, if the value is accumulated
	// from previous runs, the order-of-magnitude-test would reveal this.
	//
	// There are two tests, in the first one we collect the results right
	// after the execution (i.e. the internal buffer is set to size 1)
	// in the second we collect all results at once.
	
	private void accumulationOneByOneInner(int loops, String event, int... options) {
		final String[] events = new String[] { event };
		
		long ctx = MultiContextBenchmark.init(1, events, options);
		
		double first = Double.NEGATIVE_INFINITY;
		double last = Double.POSITIVE_INFINITY;
		
		for (int i = 0; i < loops; i++) {
			MultiContextBenchmark.start(ctx);
			runThreads(10);
			MultiContextBenchmark.stop(ctx);
			
			long[] results = MultiContextBenchmark.getResults(ctx).getData().iterator().next();
			
			if (i == loops - 1) {
				last = Math.log10(results[0]);
			}
			if (i == 0) {
				first = Math.log10(results[0]);
			}
		}
		
		if (last > first) {
			Assert.assertEquals(first, last, 0.5);
		}
	}
	
	private void accumulationAllAtOnceInner(int loops, String event, int... options) {
		final String[] events = new String[] { event };
		long ctx = MultiContextBenchmark.init(loops, events, options);
		
		for (int i = 0; i < loops; i++) {
			MultiContextBenchmark.start(ctx);
			runThreads(10);
			MultiContextBenchmark.stop(ctx);
		}
		
		List<long[]> results = MultiContextBenchmark.getResults(ctx).getData();
		double first = Math.log10(results.get(0)[0]);
		double last = Math.log10(results.get(loops - 1)[0]);
				
		if (last > first) {
			Assert.assertEquals(first, last, 0.5);
		}
	}
	
	@Test
	public void noAccumulationWithoutInheritanceOneByOne() {
		accumulationOneByOneInner(100, "PAPI_TOT_INS");
	}
	
	@Test
	public void noAccumulationWithInheritanceOneByOne() {
		accumulationOneByOneInner(100, "PAPI_TOT_INS", MultiContextBenchmark.THREAD_INHERIT);
	}
	
	@Test
	public void noAccumulationWithoutInheritanceAllAtOnce() {
		accumulationAllAtOnceInner(100, "PAPI_TOT_INS");
	}
	
	@Test
	public void noAccumulationWithInheritanceAllAtOnce() {
		accumulationAllAtOnceInner(100, "PAPI_TOT_INS", MultiContextBenchmark.THREAD_INHERIT);
	}
	
	
	private void runThreads(int threadCount) {
		Thread wrapper = new ThreadWrapper(threadCount);
		wrapper.start();
		try {
			wrapper.join();
		} catch (InterruptedException e) {
		}
	}
}
