/***************************************************
 * dynaTrace Diagnostics (c) Dynatrace LLC
 *
 * @author: dominik.stadler
 */
package com.dynatrace.diagnostics.plugins.elasticsearch;

import com.dynatrace.diagnostics.pdk.MonitorEnvironment;
import com.dynatrace.diagnostics.pdk.MonitorMeasure;
import com.dynatrace.diagnostics.pdk.PluginEnvironment;
import org.dstadler.commons.http.NanoHTTPD;
import org.dstadler.commons.testing.MockRESTServer;
import org.dstadler.commons.testing.TestHelpers;
import org.junit.Test;
import com.dynatrace.diagnostics.sdk.HostImpl;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.fail;


/**
 *
 * @author dominik.stadler
 */
public class ElasticsearchMonitorTest {
    // the minimal amount of response that we expect to make the plugin run without NPE
    private static final String MINIMAL_RESPONSE = "{\"nodes\":{}," +
            "\"indices\": {}}";
    private static final String TEST_RESPONSE = "{\"nodes\":{\"node\":{},\"node\":{\"jvm\":{},\"indices\":{}}}," +
            "\"indices\": {}}";

	private static final Logger log = Logger.getLogger(ElasticsearchMonitorTest.class.getName());

    @Test
	public void testSetupEmptyConf() throws Exception {
		ElasticsearchMonitor monitor = new ElasticsearchMonitor();

		MonitorEnvironment env = createStrictMock(MonitorEnvironment.class);

		expect(env.getConfigLong(ElasticsearchMonitor.ENV_CONFIG_PORT)).andReturn(0L);
		expect(env.getConfigString(ElasticsearchMonitor.ENV_CONFIG_PROTOCOL)).andReturn("");
		expect(env.getHost()).andReturn(new HostImpl(""));

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


		expect(env.getConfigLong(ElasticsearchMonitor.ENV_CONFIG_PORT)).andReturn(0L);
		expect(env.getConfigString(ElasticsearchMonitor.ENV_CONFIG_PROTOCOL)).andReturn("");
		expect(env.getHost()).andReturn(new HostImpl(""));

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

		expectSetup(env,"http", "invalid",-1L);

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

		expect(env.getConfigString(ElasticsearchMonitor.ENV_CONFIG_PROTOCOL)).andReturn("http");
		expect(env.getConfigLong(ElasticsearchMonitor.ENV_CONFIG_PORT)).andReturn(9200L);
		expect(env.getHost()).andReturn(new HostImpl("localhost"));

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

	@Test
	public void testPluginMinimalREST() throws Exception {
        runRESTTest(MINIMAL_RESPONSE);
	}

    @Test
    public void testPluginREST() throws Exception {
        runRESTTest(TEST_RESPONSE);
    }

    private void runRESTTest(String response) throws Exception {
        ElasticsearchMonitor monitor = new ElasticsearchMonitor();

        // return empty for all requests to ensure we handle all missing items gracefully
        try (MockRESTServer server = new MockRESTServer(NanoHTTPD.HTTP_OK, "application/json", response)) {
            MonitorEnvironment env = prepareMonitoringEnvironment(monitor, server);
            monitor.execute(env);

            verify(env);
        }
    }

    private void expectSetup(MonitorEnvironment env, String protocol,String url, Long port) {
		expect(env.getConfigLong(ElasticsearchMonitor.ENV_CONFIG_PORT)).andReturn(port);
		expect(env.getConfigString(ElasticsearchMonitor.ENV_CONFIG_PROTOCOL)).andReturn(protocol);
		expect(env.getHost()).andReturn(new HostImpl(url));
		expect(env.getConfigString(ElasticsearchMonitor.ENV_CONFIG_USER)).andReturn("invalid");
		expect(env.getConfigPassword(ElasticsearchMonitor.ENV_CONFIG_PASSWORD)).andReturn("invalid");
		expect(env.getConfigString(ElasticsearchMonitor.ENV_CONFIG_TIMEOUT)).andReturn(null);

        MonitorMeasure measure = createStrictMock(MonitorMeasure.class);
        Collection<MonitorMeasure> measures = Collections.singleton(measure);
        expect(env.getMonitorMeasures(anyString(), anyString())).andReturn(measures).anyTimes();
	}

    @Test
    public void testRESTTestInvalidJSON() throws Exception {
        ElasticsearchMonitor monitor = new ElasticsearchMonitor();

        // return empty for all requests to ensure we handle all missing items gracefully
        try (MockRESTServer server = new MockRESTServer(NanoHTTPD.HTTP_OK, "application/json", "something that is not json {")) {
            MonitorEnvironment env = prepareMonitoringEnvironment(monitor, server);
            try {
                monitor.execute(env);
                fail("Expected an exception here");
            } catch (Exception e) {
                // expected here
                TestHelpers.assertContains(e, "JsonParseException");
            }

            verify(env);
        }
    }

    @Test
    public void testRESTTestHTTPErrorCode() throws Exception {
        ElasticsearchMonitor monitor = new ElasticsearchMonitor();

        // return empty for all requests to ensure we handle all missing items gracefully
        try (MockRESTServer server = new MockRESTServer(NanoHTTPD.HTTP_INTERNALERROR, "application/json", "{\"error\":\"something\"}")) {
            MonitorEnvironment env = prepareMonitoringEnvironment(monitor, server);
            try {
                monitor.execute(env);
                fail("Expected an exception here");
            } catch (Exception e) {
                // expected here
                TestHelpers.assertContains(e, "HTTP StatusCode 500");
            }

            verify(env);
        }
    }

    private MonitorEnvironment prepareMonitoringEnvironment(ElasticsearchMonitor monitor, MockRESTServer server) throws Exception {
        MonitorEnvironment env = createStrictMock(MonitorEnvironment.class);
        expectSetup(env, "http","localhost",  Long.valueOf(server.getPort()));



        replay(env);

        monitor.setup(env);
        return env;
    }
}
