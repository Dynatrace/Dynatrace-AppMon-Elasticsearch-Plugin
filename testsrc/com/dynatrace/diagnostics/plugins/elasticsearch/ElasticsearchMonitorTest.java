/***************************************************
 * dynaTrace Diagnostics (c) Dynatrace LLC
 *
 * @author: dominik.stadler
 */
package com.dynatrace.diagnostics.plugins.elasticsearch;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.fail;

import java.util.Collection;
import java.util.Collections;

import org.junit.Test;

import com.dynatrace.diagnostics.pdk.MonitorEnvironment;
import com.dynatrace.diagnostics.pdk.MonitorMeasure;


/**
 *
 * @author dominik.stadler
 */
public class ElasticsearchMonitorTest {
	@Test
	public void testSetupEmptyConf() throws Exception {
		ElasticsearchMonitor monitor = new ElasticsearchMonitor();

		MonitorEnvironment env = createStrictMock(MonitorEnvironment.class);

		expect(env.getConfigString(ElasticsearchMonitor.ENV_CONFIG_URL)).andReturn("");

		replay(env);

		try {
			monitor.setup(env);
			fail("Should catch exception");
		} catch (@SuppressWarnings("unused") IllegalArgumentException e) {
			// expected
		}

		verify(env);
	}

	@Test
	public void testSetupNullConf() throws Exception {
		ElasticsearchMonitor monitor = new ElasticsearchMonitor();

		MonitorEnvironment env = createStrictMock(MonitorEnvironment.class);

		expect(env.getConfigString(ElasticsearchMonitor.ENV_CONFIG_URL)).andReturn(null);

		replay(env);

		try {
			monitor.setup(env);
			fail("Should catch exception");
		} catch (@SuppressWarnings("unused") IllegalArgumentException e) {
			// expected
		}

		verify(env);
	}

	@Test
	public void testSetupInvalidConf() throws Exception {
		ElasticsearchMonitor monitor = new ElasticsearchMonitor();

		MonitorEnvironment env = createStrictMock(MonitorEnvironment.class);

		expect(env.getConfigString(ElasticsearchMonitor.ENV_CONFIG_URL)).andReturn("invalid");
		expect(env.getConfigString(ElasticsearchMonitor.ENV_CONFIG_USER)).andReturn("invalid");
		expect(env.getConfigPassword(ElasticsearchMonitor.ENV_CONFIG_PASSWORD)).andReturn("invalid");
		expect(env.getConfigString(ElasticsearchMonitor.ENV_CONFIG_TIMEOUT)).andReturn(null);

		replay(env);

		// does not check the URL here...
		monitor.setup(env);

		verify(env);
	}

	@Test
	public void testSetupNullParam() throws Exception {
		ElasticsearchMonitor monitor = new ElasticsearchMonitor();

		MonitorEnvironment env = createNiceMock(MonitorEnvironment.class);

		replay(env);
		try {
			monitor.setup(env);
			fail("Should catch exception");
		} catch (@SuppressWarnings("unused") IllegalArgumentException e) {
			// expected
		}
		verify(env);

		reset(env);

		expect(env.getConfigString(ElasticsearchMonitor.ENV_CONFIG_URL)).andReturn("http://localhost:9200");
		replay(env);
		monitor.setup(env);
		verify(env);

		reset(env);
	}

	@Test
	public void testWriteMeasureNoMeasures() {
		ElasticsearchMonitor monitor = new ElasticsearchMonitor();
		MonitorEnvironment env = createStrictMock(MonitorEnvironment.class);
		expect(env.getMonitorMeasures("testgroup", "testname")).andReturn(null);
		replay(env);

		monitor.writeMeasure("testgroup", "testname", env, new Measure(2));

		verify(env);
	}

	@Test
	public void testWriteMeasureWithMeasures() {
		ElasticsearchMonitor monitor = new ElasticsearchMonitor();

		MonitorEnvironment env = createStrictMock(MonitorEnvironment.class);
		MonitorMeasure measure = createStrictMock(MonitorMeasure.class);

		Collection<MonitorMeasure> measures = Collections.singleton(measure);

		expect(env.getMonitorMeasures("testgroup", "testname")).andReturn(measures);

		measure.setValue(2);

		replay(env, measure);

		monitor.writeMeasure("testgroup", "testname", env, new Measure(2));

		verify(env, measure);
	}

	@Test
	public void testWriteMeasureWithMeasuresWithDynamic() {
		ElasticsearchMonitor monitor = new ElasticsearchMonitor();

		MonitorEnvironment env = createStrictMock(MonitorEnvironment.class);
		MonitorMeasure measure = createStrictMock(MonitorMeasure.class);
		MonitorMeasure dynMeasure = createStrictMock(MonitorMeasure.class);

		Collection<MonitorMeasure> measures = Collections.singleton(measure);

		expect(env.getMonitorMeasures("testgroup", "testname")).andReturn(measures).times(2);

		measure.setValue(2);
		expectLastCall().times(2);
		expect(env.createDynamicMeasure(measure, "dyna1", "occ1")).andReturn(dynMeasure);
		dynMeasure.setValue(43);

		replay(env, measure, dynMeasure);

		Measure value = new Measure("dyna1", 2);
		value.addDynamicMeasure("occ1", 43);

		monitor.writeMeasure("testgroup", "testname", env, value);

		verify(env, measure, dynMeasure);
	}
}
