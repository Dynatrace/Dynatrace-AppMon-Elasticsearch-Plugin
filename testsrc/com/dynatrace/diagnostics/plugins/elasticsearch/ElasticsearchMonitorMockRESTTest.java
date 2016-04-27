/***************************************************
 * dynaTrace Diagnostics (c) Dynatrace LLC
 *
 * @author: dominik.stadler
 */
package com.dynatrace.diagnostics.plugins.elasticsearch;

import com.dynatrace.diagnostics.global.PluginInstanceConfig;
import com.dynatrace.diagnostics.global.PluginPropertyInstanceConfig;
import com.dynatrace.diagnostics.global.PluginPropertyTypeConfig;
import com.dynatrace.diagnostics.global.PluginTypeConfig;
import com.dynatrace.diagnostics.pdk.MonitorMeasure;
import com.dynatrace.diagnostics.sdk.MonitorEnvironment30Impl;
import com.dynatrace.diagnostics.sdk.MonitorMeasure30Impl;
import com.dynatrace.diagnostics.sdk.MonitorMeasureKey;
import com.dynatrace.diagnostics.sdk.types.StringType;
import org.dstadler.commons.http.NanoHTTPD;
import org.dstadler.commons.testing.MemoryLeakVerifier;
import org.dstadler.commons.testing.MockRESTServer;
import org.junit.AfterClass;
import org.junit.Test;

import java.util.Map;
import java.util.logging.Logger;

import static com.dynatrace.diagnostics.plugins.elasticsearch.ElasticsearchMonitor.MSR_DOCUMENT_COUNT_PER_SECOND;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Simple test program which invokes the Elasticsearch Monitor with
 * a Mock-REST-Server to simulate various situations that we would
 * like to test.
 *
 * @author dominik.stadler
 */
public class ElasticsearchMonitorMockRESTTest {
	private static final Logger log = Logger.getLogger(ElasticsearchMonitorMockRESTTest.class.getName());

    private static final String DOC1000_RESPONSE = "{\"indices\":{\"docs\":{\"count\":1000}}}";
    private static final String DOC500_RESPONSE = "{\"indices\":{\"docs\":{\"count\":500}}}";

    private static MemoryLeakVerifier verifier = new MemoryLeakVerifier();

	@AfterClass
	public static void tearDownClass() throws Exception {
		verifier.assertGarbageCollected();
		log.info("Tear down class done");
	}

    @Test
	public void testDocPerSecondNotNegative() throws Exception {
        log.info("Starting test-run of Elasticsearch Monitor");

        ElasticsearchMonitor monitor = new ElasticsearchMonitor();

        runWithResponse(monitor, DOC500_RESPONSE, 0, 0.01);
        Thread.sleep(1000);
        runWithResponse(monitor, DOC1000_RESPONSE, 500, 120);   // high uncertainty needed here as it depends on timing
        Thread.sleep(1000);
        runWithResponse(monitor, DOC500_RESPONSE, 0, 0.01);   // no negative value should be returned on lower measure than before
        Thread.sleep(1000);
        runWithResponse(monitor, DOC1000_RESPONSE, 500, 120);   // high uncertainty needed here as it depends on timing
        Thread.sleep(1000);
        runWithResponse(monitor, DOC1000_RESPONSE, 0, 0.01);

        verifier.addObject(monitor);
    }

    private void runWithResponse(ElasticsearchMonitor monitor, String response, double expectedValue, double uncertainty) throws Exception {
        try (MockRESTServer server = new MockRESTServer(NanoHTTPD.HTTP_OK, "application/json", response)) {
            MonitorEnvironment30Impl env = prepareMonitorEnvironment("http://localhost:" + server.getPort());

            // now start executing the plugin
            monitor.setup(env);

            // the derived measure is zero initially
            executeAndCheck(monitor, env, expectedValue, uncertainty);

            monitor.teardown(env);

            verifier.addObject(env);
            verifier.addObject(server);
        }
    }

    private void executeAndCheck(ElasticsearchMonitor monitor, MonitorEnvironment30Impl env, double expectedValue, double uncertainty) throws Exception {
		monitor.execute(env);

		logMeasures(env);

		// we can expect some measure exactly in this test as we start a local node
		int found = 0;
		for(MonitorMeasure measure : env.getMonitorMeasures()) {
			assertNotNull(measure);
			assertNotNull(measure.getMetricName());

			Number measurement = ((MonitorMeasure30Impl)measure).getMeasurement();
			assertNotNull("Did not get a measurement for " + measure.getMetricName(), measurement);

			double value = measurement.doubleValue();
			switch(measure.getMetricName()) {
                case MSR_DOCUMENT_COUNT_PER_SECOND:
					assertEquals("Had " + value + " for " + measure.getMetricName() + ", allowed uncertainty: " + uncertainty, expectedValue, value, uncertainty);
					found++;
					break;
			}
		}
		assertEquals("Expected to find the measure exactly once, but had: " + found, 1, found);
	}

	private MonitorEnvironment30Impl prepareMonitorEnvironment(String URL) {
		PluginInstanceConfig pluginConfig = new PluginInstanceConfig();
		pluginConfig.setKey("elasticsearch");

		{
			PluginPropertyInstanceConfig environment = new PluginPropertyInstanceConfig(ElasticsearchMonitor.ENV_CONFIG_URL);
			environment.setSourceTypeId(StringType.TYPE_ID);
			environment.setValue(URL);
			pluginConfig.addPluginPropertyConfig(environment);
		}

		PluginTypeConfig pluginTypeConfig = new PluginTypeConfig();
		pluginTypeConfig.setKey("elasticsearch");

		{
			PluginPropertyTypeConfig environmentType = new PluginPropertyTypeConfig(ElasticsearchMonitor.ENV_CONFIG_URL, new StringType("some",
					""));
			environmentType.setSourceTypeId(StringType.TYPE_ID);
			environmentType.setType(StringType.TYPE_ID);
			environmentType.setValue(URL);
			pluginTypeConfig.addPluginPropertyConfig(environmentType);
		}

		// register all measure groups to have them available during the test
		MonitorEnvironment30Impl env = new MonitorEnvironment30Impl(null, pluginConfig, pluginTypeConfig, false, null);
		{
			for(String measure : ElasticsearchMonitor.ALL_MEASURES) {
				createMeasure(env, ElasticsearchMonitor.METRIC_GROUP_ELASTICSEARCH, measure);
			}
		}
		return env;
	}

	private static void logMeasures(MonitorEnvironment30Impl env) {
		log.info("Found " + env.getMonitorMeasures().size() + " resulting measures");
		for(MonitorMeasure measure : env.getMonitorMeasures()) {
			log.info("Found resulting measure: " + measure + ", value: " + ((MonitorMeasure30Impl)measure).getMeasurement());
		}
		Map<MonitorMeasureKey, MonitorMeasure30Impl> dynamicMeasures = env.getDynamicMeasures();
		for(MonitorMeasureKey key : dynamicMeasures.keySet()) {
			MonitorMeasure30Impl dynamicMeasure = dynamicMeasures.get(key);
			log.info("Found dynamic measure for: " + dynamicMeasure.getMetricGroupName() + ": " +
					key.getTags() + ": " + dynamicMeasure.getMeasurement());
		}
	}

	private static void createMeasure(MonitorEnvironment30Impl env, String group, String name) {
		MonitorMeasure30Impl measure;
		measure = new MonitorMeasure30Impl();
		measure.setMetricGroupName(group);
		measure.setMetricName(name);
		env.internalGetMeasures().add(measure);
	}
}
