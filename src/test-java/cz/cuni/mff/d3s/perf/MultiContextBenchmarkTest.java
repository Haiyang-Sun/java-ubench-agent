/*
 * Copyright 2014 Charles University in Prague
 * Copyright 2014 Vojtech Horky
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

import java.util.*;

import org.junit.*;


public class MultiContextBenchmarkTest {
	private static final String[] SMOKE_TEST_EVENTS = { "SYS_WALLCLOCK" };
	private static final int SMOKE_TEST_SLEEP_MILLIS = 100;
	private static final int LOOPS = 5;
	
	@Test
	public void getResultsSmokeTest() throws InterruptedException {
		long ctx = MultiContextBenchmark.init(1, SMOKE_TEST_EVENTS);
		MultiContextBenchmark.start(ctx);
		Thread.sleep(SMOKE_TEST_SLEEP_MILLIS);
		MultiContextBenchmark.stop(ctx);
		
		
		BenchmarkResults results = MultiContextBenchmark.getResults(ctx);
		Assert.assertNotNull("getResults() may not return null", results);
		
		String[] eventNames = results.getEventNames();
		Assert.assertNotNull("getEventNames() may not return null", eventNames);
		Assert.assertArrayEquals(SMOKE_TEST_EVENTS, eventNames);
		
		List<long[]> data = results.getData();
		Assert.assertNotNull("getData() may not return null", data);
		
		Assert.assertEquals("we collected single sample only", 1, data.size());
		
		long[] numbers = data.get(0);
		Assert.assertNotNull("the array with results ought not to be null", numbers);
		Assert.assertEquals("we collected single event only", 1, numbers.length);
		
		Assert.assertTrue("sample " + numbers[0] + " is not in range",
			(numbers[0] > SMOKE_TEST_SLEEP_MILLIS*1000*1000/2)
			&& (numbers[0] < SMOKE_TEST_SLEEP_MILLIS*1000*1000*10));
	}
	
	@Test
	public void resetWorks() throws InterruptedException {
		long ctx = MultiContextBenchmark.init(LOOPS, SMOKE_TEST_EVENTS);
		for (int i = 0; i < LOOPS; i++) {
			MultiContextBenchmark.start(ctx);
			Thread.sleep(SMOKE_TEST_SLEEP_MILLIS);
			MultiContextBenchmark.stop(ctx);
		}
		
		MultiContextBenchmark.reset(ctx);
		
		for (int i = 0; i < LOOPS / 2; i++) {
			MultiContextBenchmark.start(ctx);
			Thread.sleep(SMOKE_TEST_SLEEP_MILLIS);
			MultiContextBenchmark.stop(ctx);
		}
		
		BenchmarkResults results = MultiContextBenchmark.getResults(ctx);
		List<long[]> data = results.getData();
		
		Assert.assertEquals(LOOPS / 2, data.size());
	}
	
	private static final String[] columns = {
		"SYS_WALLCLOCK",
		"JVM_COMPILATIONS",
		"forced-context-switch",
		"PAPI_L1_DCM",
		"PAPI_L1_DCM",
	};
	
	public static void main(String[] args) {
		long ctx = MultiContextBenchmark.init(LOOPS, columns);
		for (int i = 0; i < LOOPS; i++) {
			MultiContextBenchmark.start(ctx);
			MultiContextBenchmark.stop(ctx);
		}
		
		long ctx2 = MultiContextBenchmark.init(LOOPS, columns);	
		
		for (int i = 0; i < LOOPS; i++) {
			long before = System.nanoTime();
			MultiContextBenchmark.start(ctx2);
			long start = System.nanoTime();
			System.out.printf("This is loop %d", i);
			long end = System.nanoTime();
			MultiContextBenchmark.stop(ctx2);
			long after = System.nanoTime();
			long duration = end - start;
			long start_measurement_duration = start - before;
			long end_measurement_duration = after - end;
			System.out.printf(" [took %dus, measurement %dns and %dns].\n",
					duration / 1000, start_measurement_duration,
					end_measurement_duration);
		}
		
		BenchmarkResultsPrinter.table(MultiContextBenchmark.getResults(ctx2), System.out);
		// BenchmarkResultsPrinter.toCsv(Benchmark.getResults(), System.out, ",", true);

		System.exit(0);
	}
}
