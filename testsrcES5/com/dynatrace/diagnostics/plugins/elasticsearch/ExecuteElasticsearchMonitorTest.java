/***************************************************
 * dynaTrace Diagnostics (c) Dynatrace LLC
 *
 * @author: dominik.stadler
 */
package com.dynatrace.diagnostics.plugins.elasticsearch;

import com.carrotsearch.randomizedtesting.annotations.SeedDecorators;
import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import com.dynatrace.diagnostics.global.PluginInstanceConfig;
import com.dynatrace.diagnostics.global.PluginPropertyInstanceConfig;
import com.dynatrace.diagnostics.global.PluginPropertyTypeConfig;
import com.dynatrace.diagnostics.global.PluginTypeConfig;
import com.dynatrace.diagnostics.pdk.MonitorMeasure;
import com.dynatrace.diagnostics.sdk.HostImpl;
import com.dynatrace.diagnostics.sdk.MonitorEnvironment30Impl;
import com.dynatrace.diagnostics.sdk.MonitorMeasure30Impl;
import com.dynatrace.diagnostics.sdk.MonitorMeasureKey;
import com.dynatrace.diagnostics.sdk.types.BooleanType;
import com.dynatrace.diagnostics.sdk.types.LongType;
import com.dynatrace.diagnostics.sdk.types.StringType;
import org.dstadler.commons.testing.MemoryLeakVerifier;
import org.elasticsearch.ESNetty3IntegTestCase;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.test.ESIntegTestCase.ClusterScope;
import org.elasticsearch.test.ESIntegTestCase.Scope;
import org.elasticsearch.test.SecurityManagerWorkaroundSeedDecorator;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;
import java.util.logging.Logger;

import static com.dynatrace.diagnostics.plugins.elasticsearch.ElasticsearchMonitor.*;
import static org.elasticsearch.cluster.metadata.IndexMetaData.SETTING_NUMBER_OF_REPLICAS;
import static org.elasticsearch.cluster.metadata.IndexMetaData.SETTING_NUMBER_OF_SHARDS;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.junit.Assert.*;

/**
 * Simple test program which invokes the Elasticsearch Monitor with
 * useful values and prints out the results.
 *
 * This is used to manually unit-test the Elasticsearch Monitor.
 *
 * @author dominik.stadler
 */
@ClusterScope(scope = Scope.SUITE, numDataNodes = 1)
//Ruxit Agent sometimes does not stop quickly enough, remove this when https://dev-jira.emea.cpwr.corp/browse/APM-49234 is fixed
@ThreadLeakScope(com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope.Scope.NONE)
// very ugly workaround to disable the SecurityManager that Elasticsearch 2.2.0 and higher injects in Tests, it is cumbersome
// to set up the security policy everywhere and causes lots of test-failures and strange side-effects, e.g.
// https://issues.gradle.org/browse/GRADLE-2170, which hangs junit test runs in Gradle as a result
@SeedDecorators(value = SecurityManagerWorkaroundSeedDecorator.class)
public class ExecuteElasticsearchMonitorTest extends ESNetty3IntegTestCase {
	private static final Logger log = Logger.getLogger(ExecuteElasticsearchMonitorTest.class.getName());

	// which URL do we use in test
	private static final Long PORT = 19300L;
	private static final String PROTOCOL = "http";
	private static final String URL = "localhost";

	private static MemoryLeakVerifier verifier = new MemoryLeakVerifier();

	@AfterClass
	public static void tearDownClass() throws Exception {
		log.info("Tear down class...");
		ESIntegTestCase.afterClass();

		verifier.assertGarbageCollected();
		log.info("Tear down class done");
	}

	@Override
    protected Settings nodeSettings(int nodeOrdinal) {
		log.info("Node settings...");
        return Settings.builder()
        		.put("http.enabled", "true")
        		// changing the port made the test flaky...
        		//.put("http.port", "19200")
                .put(super.nodeSettings(nodeOrdinal))
                .build();
    }

    @Override
    public Settings indexSettings() {
		log.info("Index settings...");
        Settings.Builder builder = Settings.builder();
        builder.put(SETTING_NUMBER_OF_SHARDS, 1);
        builder.put(SETTING_NUMBER_OF_REPLICAS, 0);
        return builder.build();
    }

    @Test
	public void testMain() throws Exception {
        log.info("Starting test-run of Elasticsearch Monitor");

		ElasticsearchMonitor monitor = new ElasticsearchMonitor();
		MonitorEnvironment30Impl env = prepareMonitorEnvironment();

		// now start executing the plugin
		monitor.setup(env);

		log.info("First run without any data in indexes");
		executeAndCheck(monitor, env, false);

		log.info("Preparing data in Elasticsearch node");
		initializeIndex();

		log.info("Second run with data in indexes");
		executeAndCheck(monitor, env, true);

		monitor.teardown(env);

		verifier.addObject(monitor);
		verifier.addObject(env);
		verifier.addObject(client());
		verifier.addObject(indexSettings());
		verifier.addObject(cluster());
	}

	private void executeAndCheck(ElasticsearchMonitor monitor, MonitorEnvironment30Impl env, boolean data) throws Exception {
		monitor.execute(env);

		logMeasures(env);

        Set<String> remainingMeasures = new HashSet<>(Arrays.asList(ElasticsearchMonitor.ALL_MEASURES));

        // we can expect some measure exactly in this test as we start a local node
		for(MonitorMeasure measure : env.getMonitorMeasures()) {
			assertNotNull(measure);
			assertNotNull(measure.getMetricName());

			Number measurement = ((MonitorMeasure30Impl)measure).getMeasurement();
			assertNotNull("Did not get a measurement for " + measure.getMetricName(), measurement);

			long value = measurement.longValue();
			switch(measure.getMetricName()) {
				// sometimes this is 1, sometimes more, is this because of randomized testing?
				case MSR_NODE_COUNT:
					if(value < 1 || value >= 5) {
						Assert.assertEquals("Should have one data and potentially one client node in tests", 2, value);
					}
					break;

				// some measures are 1 in our tests
				case MSR_DATA_NODE_COUNT:
					Assert.assertEquals("Had " + value + " for " + measure.getMetricName(), 1, value);
					break;

				case MSR_ACTIVE_PRIMARY_SHARDS:
				case MSR_ACTIVE_SHARDS:
					Assert.assertEquals("Had " + value + " for " + measure.getMetricName(), data ? 1 : 0, value);
					break;

				case MSR_ACTIVE_SHARDS_PERCENT:
					Assert.assertEquals("Had " + value + " for " + measure.getMetricName(), 100, value);
					break;

				// some measures are 0 in our tests
				case MSR_RELOCATING_SHARDS:
				case MSR_INITIALIZING_SHARDS:
				case MSR_UNASSIGNED_SHARDS:
				case MSR_DELAYED_UNASSIGNED_SHARDS:
				case MSR_STORE_THROTTLE_TIME:
				case MSR_QUERY_CACHE_SIZE:
				case MSR_COMPLETION_SIZE:
				case MSR_SEGMENT_SIZE:
				case MSR_PERCOLATE_COUNT:
				case MSR_FIELD_DATA_EVICTIONS:
				case MSR_INDEXING_THROTTLE_TIME:
				case MSR_INDEXING_CURRENT:
				case MSR_DELETE_CURRENT:
				case MSR_QUERY_CURRENT:
				case MSR_FETCH_CURRENT:
				case MSR_SCROLL_CURRENT:
				case MSR_REQUEST_CACHE_SIZE:
				case MSR_RECOVERY_THROTTLE_TIME:
				case MSR_RECOVERY_AS_SOURCE:
				case MSR_RECOVERY_AS_TARGET:
					Assert.assertEquals("Had " + value + " for " + measure.getMetricName(), 0, value);
					break;

				// these should have some value
				case MSR_MEM_INIT_HEAP:
				case MSR_MEM_MAX_HEAP:
				case MSR_MEM_INIT_NON_HEAP:
				case MSR_MEM_MAX_DIRECT:
					assertTrue("Had " + value + " for " + measure.getMetricName(), value > 0);
					break;

				case MSR_INDEX_COUNT:
				case MSR_SHARD_COUNT:
				case MSR_TRANSLOG_SIZE:
				case MSR_SEGMENT_COUNT:
                case MSR_STORE_SIZE:
                case MSR_DOCUMENT_COUNT_PER_SECOND:
					assertTrue("Had " + value + " for " + measure.getMetricName(), value > (data ? 0 : -1));
					break;

				case MSR_DOCUMENT_COUNT:
					Assert.assertEquals("Had " + value + " for " + measure.getMetricName(), data ? 12 : 0, value);
					break;

				// some might be zero or higher depending on timing
				case MSR_DELETED_COUNT:
                case MSR_DELETED_COUNT_PER_SECOND:
				case MSR_MEM_MAX_NON_HEAP:
				case MSR_FIELD_DATA_SIZE:
				case MSR_FILE_SYSTEM_SIZE:
					assertTrue("Had " + value + " for " + measure.getMetricName(), value >= 0);
					break;

				// these are negative, probably they were not computed yet after startup
				case MSR_FILE_DESCRIPTOR_COUNT:
				case MSR_FILE_DESCRIPTOR_LIMIT:
					assertTrue("Had " + value + " for " + measure.getMetricName(), value != 0);
					break;

                // in 5.0.0-alpha1, this is returned as 0 whilte it was -1 in 2.x and below
				case MSR_PERCOLATE_SIZE:
                    break;

				default:
					fail("Unexpected measure found: " + measure + ", value: " + ((MonitorMeasure30Impl)measure).getMeasurement());
			}

            assertTrue("Metric " + measure.getMetricName() + " was not found in the list of remaining measures, it seems it was sent twice",
                    remainingMeasures.remove(measure.getMetricName()));
		}

        assertTrue("Had remaining measures that were not received: " + remainingMeasures, remainingMeasures.isEmpty());
		Assert.assertEquals("Expected to find all measures, but had: " + env.getMonitorMeasures().size(),
                ElasticsearchMonitor.ALL_MEASURES.length, env.getMonitorMeasures().size());
	}

	private MonitorEnvironment30Impl prepareMonitorEnvironment() {
		PluginInstanceConfig pluginConfig = new PluginInstanceConfig();

		pluginConfig.setKey("elasticsearch");

		{
			PluginPropertyInstanceConfig propertyProtocol = new PluginPropertyInstanceConfig(ElasticsearchMonitor.ENV_CONFIG_PROTOCOL);
			propertyProtocol.setSourceTypeId(StringType.TYPE_ID);
			propertyProtocol.setValue(PROTOCOL);
			pluginConfig.addPluginPropertyConfig(propertyProtocol);

			PluginPropertyInstanceConfig propertyPort = new PluginPropertyInstanceConfig(ElasticsearchMonitor.ENV_CONFIG_PORT);
			propertyPort.setSourceTypeId(LongType.TYPE_ID);
			propertyPort.setValue(PORT.toString());
			pluginConfig.addPluginPropertyConfig(propertyPort);

			PluginPropertyInstanceConfig useFullConfiguration = new PluginPropertyInstanceConfig(ElasticsearchMonitor.ENV_CONFIG_USE_FULL_URL_CONFIGURATION);
			useFullConfiguration.setSourceTypeId(BooleanType.TYPE_ID);
			useFullConfiguration.setValue(Boolean.FALSE.toString());
			pluginConfig.addPluginPropertyConfig(useFullConfiguration);


		}

		PluginTypeConfig pluginTypeConfig = new PluginTypeConfig();
		pluginTypeConfig.setKey("elasticsearch");

		{
			PluginPropertyTypeConfig propertyProtocol = new PluginPropertyTypeConfig(ElasticsearchMonitor.ENV_CONFIG_PROTOCOL, new StringType("some",
					""));
			propertyProtocol.setSourceTypeId(StringType.TYPE_ID);
			propertyProtocol.setType(StringType.TYPE_ID);
			propertyProtocol.setValue(PROTOCOL);
			pluginTypeConfig.addPluginPropertyConfig(propertyProtocol);

			PluginPropertyTypeConfig propertyPort = new PluginPropertyTypeConfig(ElasticsearchMonitor.ENV_CONFIG_PORT, new LongType(0L,
					9200L));
			propertyPort.setSourceTypeId(LongType.TYPE_ID);
			propertyPort.setType(LongType.TYPE_ID);
			propertyPort.setValue(PORT.toString());
			pluginTypeConfig.addPluginPropertyConfig(propertyPort);

			PluginPropertyTypeConfig useFullConfiguration = new PluginPropertyTypeConfig(ElasticsearchMonitor.ENV_CONFIG_USE_FULL_URL_CONFIGURATION,new BooleanType(Boolean.FALSE,Boolean.FALSE));
			useFullConfiguration.setSourceTypeId(BooleanType.TYPE_ID);
			useFullConfiguration.setValue(Boolean.FALSE.toString());
			pluginTypeConfig.addPluginPropertyConfig(useFullConfiguration);
		}

		// register all measure groups to have them available during the test

		MonitorEnvironment30Impl env = new MonitorEnvironment30Impl(new HostImpl(URL), pluginConfig, pluginTypeConfig, false, null);
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

    private void initializeIndex() throws Exception {

        // Create a new lookup index
        String useractionMapping = XContentFactory.jsonBuilder().startObject().startObject("useraction")
                .startArray("dynamic_templates").startObject()
                .startObject("string_fields").startObject("mapping")
                .field("index", "not_analyzed").field("type", "string")
                .endObject().field("match", "*").field("match_mapping_type", "string").endObject()
                .endObject().endArray().endObject().endObject()
                .string();
        System.out.println(useractionMapping);
        assertAcked(prepareCreate("useraction-1")
                .addMapping("useraction", useractionMapping));

        List<IndexRequestBuilder> indexBuilders = new ArrayList<>();
        // Index stock records:
        indexBuilders.add(client().prepareIndex("useraction-1", "useraction", "01").setSource("{\"tenantId\":\"1\",\"visitId\":\"DIILLIIJVEKBSIHNKFHIOODHEIKOHBIE\",\"visitorId\":\"1446039792325SDCWTHL21NQVBSN1WSMQC1IZUTUYNNGC\",\"visitType\":\"RUXIT_SYNTHETIC\",\"bounce\":false,\"newVisitor\":false,\"name\":\"Loadingofpage/Account/LogOn\",\"type\":\"Load\",\"startTime\":1446043792643,\"endTime\":1446043792826,\"duration\":183,\"applicationId\":\"APPLICATION-EA7C4B59F27D43EB\",\"browserFamilyId\":\"BROWSER-FB7D321A347B39E2\",\"browserFamilyName\":\"unknown\",\"browserTypeId\":\"BROWSER-3467622E56FC8523\",\"browserTypeName\":\"Other\",\"browserMajorVersionId\":null,\"browserMajorVersionName\":null,\"geolocationContinentId\":\"GEOLOCATION-6919A3D55EFC157F\",\"geolocationContinentCode\":\"AS\",\"geolocationContinentName\":\"Asia\",\"geolocationCountryId\":\"GEOLOCATION-E4C0ABA51DD28418\",\"geolocationCountryCode\":\"CN\",\"geolocationCountryName\":\"China\",\"geolocationRegionId\":\"GEOLOCATION-60D58ED8EC851A8F\",\"geolocationRegionCode\":\"30\",\"geolocationRegionName\":\"Guangdong\",\"geolocationCityId\":\"GEOLOCATION-6431182A0E291631\",\"geolocationCityName\":\"Guangzhou\",\"osFamilyId\":null,\"osFamilyName\":null,\"osVersionId\":null,\"osVersionName\":null,\"userActionsEncoded\":\"search.html~Load~1446718595096~1446718595096~0~APM_SERVER-0000000011111111~^review.html~Load~1446718586501~1446718781962~195461~APM_SERVER-0000000011111111~^\",\"geoLocationsEncoded\":\"BR-SP~Brazil~REGION~^BR~Brazil~COUNTRY~^SA~South America~CONTINENT~^~*~WORLD~^\"}"));
        indexBuilders.add(client().prepareIndex("useraction-1", "useraction", "02").setSource("{\"tenantId\":\"1\",\"visitId\":\"DIILLIIJVEKBSIHNKFHIOODHEIKOHBIE\",\"visitorId\":\"1446039792325SDCWTHL21NQVBSN1WSMQC1IZUTUYNNGC\",\"visitType\":\"RUXIT_SYNTHETIC\",\"bounce\":false,\"newVisitor\":false,\"name\":\"Loadingofpage/Account/LogOut\",\"type\":\"Load\",\"startTime\":1446043822472,\"endTime\":1446043826666,\"duration\":4194,\"applicationId\":\"APPLICATION-EA7C4B59F27D43EB\",\"browserFamilyId\":\"BROWSER-FB7D321A347B39E2\",\"browserFamilyName\":\"unknown\",\"browserTypeId\":\"BROWSER-3467622E56FC8523\",\"browserTypeName\":\"Other\",\"browserMajorVersionId\":null,\"browserMajorVersionName\":null,\"geolocationContinentId\":\"GEOLOCATION-6919A3D55EFC157F\",\"geolocationContinentCode\":\"AS\",\"geolocationContinentName\":\"Asia\",\"geolocationCountryId\":\"GEOLOCATION-E4C0ABA51DD28418\",\"geolocationCountryCode\":\"CN\",\"geolocationCountryName\":\"China\",\"geolocationRegionId\":\"GEOLOCATION-60D58ED8EC851A8F\",\"geolocationRegionCode\":\"30\",\"geolocationRegionName\":\"Guangdong\",\"geolocationCityId\":\"GEOLOCATION-6431182A0E291631\",\"geolocationCityName\":\"Guangzhou\",\"osFamilyId\":null,\"osFamilyName\":null,\"osVersionId\":null,\"osVersionName\":null,\"userActionsEncoded\":\"orange.html~Load~1446718580541~1446718580541~0~APM_SERVER-0000000011111111~^review.html~Load~1446718307302~1446718730431~423129~APM_SERVER-0000000011111111~^\",\"geoLocationsEncoded\":\"OC~Oceania~CONTINENT~^~*~WORLD~^\"}"));
        indexBuilders.add(client().prepareIndex("useraction-1", "useraction", "03").setSource("{\"tenantId\":\"1\",\"visitId\":\"DIILLIIJVEKBSIHNKFHIOODHEIKOHBIE\",\"visitorId\":\"1446039792325SDCWTHL21NQVBSN1WSMQC1IZUTUYNNGC\",\"visitType\":\"RUXIT_SYNTHETIC\",\"bounce\":false,\"newVisitor\":false,\"name\":\"Loadingofpage/\",\"type\":\"Load\",\"startTime\":1446043789433,\"endTime\":1446043789734,\"duration\":301,\"applicationId\":\"APPLICATION-EA7C4B59F27D43EB\",\"browserFamilyId\":\"BROWSER-FB7D321A347B39E2\",\"browserFamilyName\":\"unknown\",\"browserTypeId\":\"BROWSER-3467622E56FC8523\",\"browserTypeName\":\"Other\",\"browserMajorVersionId\":null,\"browserMajorVersionName\":null,\"geolocationContinentId\":\"GEOLOCATION-6919A3D55EFC157F\",\"geolocationContinentCode\":\"AS\",\"geolocationContinentName\":\"Asia\",\"geolocationCountryId\":\"GEOLOCATION-E4C0ABA51DD28418\",\"geolocationCountryCode\":\"CN\",\"geolocationCountryName\":\"China\",\"geolocationRegionId\":\"GEOLOCATION-60D58ED8EC851A8F\",\"geolocationRegionCode\":\"30\",\"geolocationRegionName\":\"Guangdong\",\"geolocationCityId\":\"GEOLOCATION-6431182A0E291631\",\"geolocationCityName\":\"Guangzhou\",\"osFamilyId\":null,\"osFamilyName\":null,\"osVersionId\":null,\"osVersionName\":null,\"geoLocationsEncoded\":\"~Waltham~CITY~^MA~Massachusetts~REGION~^US~United States~COUNTRY~^NA~North America~CONTINENT~^~*~WORLD~^\"}"));
        indexBuilders.add(client().prepareIndex("useraction-1", "useraction", "04").setSource("{\"tenantId\":\"1\",\"visitId\":\"LKMOJDKJKLDOCKMKDGPKNKGCNHFDNFHO\",\"visitorId\":\"1446043713336D851DKTXTZQDQ3DAWMAAJ3LHBAJHVITZ\",\"visitType\":\"RUXIT_SYNTHETIC\",\"bounce\":false,\"newVisitor\":true,\"name\":\"Loadingofpage/Journey\",\"type\":\"Load\",\"startTime\":1446043756644,\"endTime\":1446043762352,\"duration\":5708,\"applicationId\":\"APPLICATION-EA7C4B59F27D43EB\",\"browserFamilyId\":\"BROWSER-4EDCE9C819871D91\",\"browserFamilyName\":\"IE\",\"browserTypeId\":\"BROWSER-44C8EFE87C847CA6\",\"browserTypeName\":\"DesktopBrowser\",\"browserMajorVersionId\":\"BROWSER-926C6A1AC9843DFE\",\"browserMajorVersionName\":\"IE10\",\"geolocationContinentId\":\"GEOLOCATION-5A3E928A9D35F3C4\",\"geolocationContinentCode\":\"AF\",\"geolocationContinentName\":\"Africa\",\"geolocationCountryId\":\"GEOLOCATION-8A71846AFEB3AEB9\",\"geolocationCountryCode\":\"ZA\",\"geolocationCountryName\":\"SouthAfrica\",\"geolocationRegionId\":null,\"geolocationRegionCode\":null,\"geolocationRegionName\":null,\"geolocationCityId\":null,\"geolocationCityName\":null,\"osFamilyId\":\"OS-C2CE1ED9011FA680\",\"osFamilyName\":\"Windows\",\"osVersionId\":\"OS-BCEC462F37BD3F1D\",\"osVersionName\":\"Windows8\"}"));
        indexBuilders.add(client().prepareIndex("useraction-1", "useraction", "05").setSource("{\"tenantId\":\"1\",\"visitId\":\"LKMOJDKJKLDOCKMKDGPKNKGCNHFDNFHO\",\"visitorId\":\"1446043713336D851DKTXTZQDQ3DAWMAAJ3LHBAJHVITZ\",\"visitType\":\"RUXIT_SYNTHETIC\",\"bounce\":false,\"newVisitor\":true,\"name\":\"Loadingofpage/Journey\",\"type\":\"Load\",\"startTime\":1446043756644,\"endTime\":1446043762352,\"duration\":5708,\"applicationId\":\"APPLICATION-EA7C4B59F27D43EB\",\"browserFamilyId\":\"BROWSER-4EDCE9C819871D91\",\"browserFamilyName\":\"IE\",\"browserTypeId\":\"BROWSER-44C8EFE87C847CA6\",\"browserTypeName\":\"DesktopBrowser\",\"browserMajorVersionId\":\"BROWSER-926C6A1AC9843DFE\",\"browserMajorVersionName\":\"IE10\",\"geolocationContinentId\":\"GEOLOCATION-5A3E928A9D35F3C4\",\"geolocationContinentCode\":\"AF\",\"geolocationContinentName\":\"Africa\",\"geolocationCountryId\":\"GEOLOCATION-8A71846AFEB3AEB9\",\"geolocationCountryCode\":\"ZA\",\"geolocationCountryName\":\"SouthAfrica\",\"geolocationRegionId\":null,\"geolocationRegionCode\":null,\"geolocationRegionName\":null,\"geolocationCityId\":null,\"geolocationCityName\":null,\"osFamilyId\":\"OS-C2CE1ED9011FA680\",\"osFamilyName\":\"Windows\",\"osVersionId\":\"OS-BCEC462F37BD3F1D\",\"osVersionName\":\"Windows8\"}"));
        indexBuilders.add(client().prepareIndex("useraction-1", "useraction", "06").setSource("{\"tenantId\":\"1\",\"visitId\":\"INENFEODAJCPHIHCABEAAGFFHPPFBPEH\",\"visitorId\":\"14460435933245T4LZ513LKICD6DFCUU9DJM2A0P01W47\",\"visitType\":\"RUXIT_SYNTHETIC\",\"bounce\":false,\"newVisitor\":true,\"name\":\"Loadingofpage/Account/LogOn\",\"type\":\"Load\",\"startTime\":1446043612586,\"endTime\":1446043612791,\"duration\":205,\"applicationId\":\"APPLICATION-EA7C4B59F27D43EB\",\"browserFamilyId\":\"BROWSER-FF8EAE6885DE87B3\",\"browserFamilyName\":\"Chrome\",\"browserTypeId\":\"BROWSER-44C8EFE87C847CA6\",\"browserTypeName\":\"DesktopBrowser\",\"browserMajorVersionId\":\"BROWSER-B82CE0AD1FD3292B\",\"browserMajorVersionName\":\"Chrome38\",\"geolocationContinentId\":\"GEOLOCATION-970B6D0A98F55995\",\"geolocationContinentCode\":\"NA\",\"geolocationContinentName\":\"NorthAmerica\",\"geolocationCountryId\":\"GEOLOCATION-D39825E84010E068\",\"geolocationCountryCode\":\"US\",\"geolocationCountryName\":\"UnitedStates\",\"geolocationRegionId\":\"GEOLOCATION-100C570E97C312CA\",\"geolocationRegionCode\":\"CA\",\"geolocationRegionName\":\"California\",\"geolocationCityId\":\"GEOLOCATION-9543C4589B60AB94\",\"geolocationCityName\":\"PaloAlto\",\"osFamilyId\":\"OS-C2CE1ED9011FA680\",\"osFamilyName\":\"Windows\",\"osVersionId\":\"OS-026191052160B96A\",\"osVersionName\":\"Windows8.1\"}"));
        indexBuilders.add(client().prepareIndex("useraction-1", "useraction", "07").setSource("{\"tenantId\":\"1\",\"visitId\":\"INENFEODAJCPHIHCABEAAGFFHPPFBPEH\",\"visitorId\":\"14460435933245T4LZ513LKICD6DFCUU9DJM2A0P01W47\",\"visitType\":\"RUXIT_SYNTHETIC\",\"bounce\":false,\"newVisitor\":true,\"name\":\"Loadingofpage/Booking\",\"type\":\"Load\",\"startTime\":1446043632717,\"endTime\":1446043639262,\"duration\":6545,\"applicationId\":\"APPLICATION-EA7C4B59F27D43EB\",\"browserFamilyId\":\"BROWSER-FF8EAE6885DE87B3\",\"browserFamilyName\":\"Chrome\",\"browserTypeId\":\"BROWSER-44C8EFE87C847CA6\",\"browserTypeName\":\"DesktopBrowser\",\"browserMajorVersionId\":\"BROWSER-B82CE0AD1FD3292B\",\"browserMajorVersionName\":\"Chrome38\",\"geolocationContinentId\":\"GEOLOCATION-970B6D0A98F55995\",\"geolocationContinentCode\":\"NA\",\"geolocationContinentName\":\"NorthAmerica\",\"geolocationCountryId\":\"GEOLOCATION-D39825E84010E068\",\"geolocationCountryCode\":\"US\",\"geolocationCountryName\":\"UnitedStates\",\"geolocationRegionId\":\"GEOLOCATION-100C570E97C312CA\",\"geolocationRegionCode\":\"CA\",\"geolocationRegionName\":\"California\",\"geolocationCityId\":\"GEOLOCATION-9543C4589B60AB94\",\"geolocationCityName\":\"PaloAlto\",\"osFamilyId\":\"OS-C2CE1ED9011FA680\",\"osFamilyName\":\"Windows\",\"osVersionId\":\"OS-026191052160B96A\",\"osVersionName\":\"Windows8.1\"}"));
        indexBuilders.add(client().prepareIndex("useraction-1", "useraction", "08").setSource("{\"tenantId\":\"1\",\"visitId\":\"INENFEODAJCPHIHCABEAAGFFHPPFBPEH\",\"visitorId\":\"14460435933245T4LZ513LKICD6DFCUU9DJM2A0P01W47\",\"visitType\":\"RUXIT_SYNTHETIC\",\"bounce\":false,\"newVisitor\":true,\"name\":\"Loadingofpage/Journey\",\"type\":\"Load\",\"startTime\":1446043615691,\"endTime\":1446043620357,\"duration\":4666,\"applicationId\":\"APPLICATION-EA7C4B59F27D43EB\",\"browserFamilyId\":\"BROWSER-FF8EAE6885DE87B3\",\"browserFamilyName\":\"Chrome\",\"browserTypeId\":\"BROWSER-44C8EFE87C847CA6\",\"browserTypeName\":\"DesktopBrowser\",\"browserMajorVersionId\":\"BROWSER-B82CE0AD1FD3292B\",\"browserMajorVersionName\":\"Chrome38\",\"geolocationContinentId\":\"GEOLOCATION-970B6D0A98F55995\",\"geolocationContinentCode\":\"NA\",\"geolocationContinentName\":\"NorthAmerica\",\"geolocationCountryId\":\"GEOLOCATION-D39825E84010E068\",\"geolocationCountryCode\":\"US\",\"geolocationCountryName\":\"UnitedStates\",\"geolocationRegionId\":\"GEOLOCATION-100C570E97C312CA\",\"geolocationRegionCode\":\"CA\",\"geolocationRegionName\":\"California\",\"geolocationCityId\":\"GEOLOCATION-9543C4589B60AB94\",\"geolocationCityName\":\"PaloAlto\",\"osFamilyId\":\"OS-C2CE1ED9011FA680\",\"osFamilyName\":\"Windows\",\"osVersionId\":\"OS-026191052160B96A\",\"osVersionName\":\"Windows8.1\"}"));
        indexBuilders.add(client().prepareIndex("useraction-1", "useraction", "09").setSource("{\"tenantId\":\"1\",\"visitId\":\"JAOAFJJHFJFIOIPFOGKAEHPGDKMGDGJA\",\"visitorId\":\"1446044073349GT206UWGPS02EDB7K985QKPMBIKCUD2R\",\"visitType\":\"RUXIT_SYNTHETIC\",\"bounce\":false,\"newVisitor\":false,\"name\":\"Loadingofpage/Report\",\"type\":\"Load\",\"startTime\":1446044252100,\"endTime\":1446044258310,\"duration\":6210,\"applicationId\":\"APPLICATION-EA7C4B59F27D43EB\",\"browserFamilyId\":\"BROWSER-FF8EAE6885DE87B3\",\"browserFamilyName\":\"Chrome\",\"browserTypeId\":\"BROWSER-44C8EFE87C847CA6\",\"browserTypeName\":\"DesktopBrowser\",\"browserMajorVersionId\":\"BROWSER-B82CE0AD1FD3292B\",\"browserMajorVersionName\":\"Chrome38\",\"geolocationContinentId\":\"GEOLOCATION-970B6D0A98F55995\",\"geolocationContinentCode\":\"NA\",\"geolocationContinentName\":\"NorthAmerica\",\"geolocationCountryId\":\"GEOLOCATION-D39825E84010E068\",\"geolocationCountryCode\":\"US\",\"geolocationCountryName\":\"UnitedStates\",\"geolocationRegionId\":\"GEOLOCATION-A6153C0BB4FECE91\",\"geolocationRegionCode\":\"MO\",\"geolocationRegionName\":\"Missouri\",\"geolocationCityId\":\"GEOLOCATION-62F2889D258D8C59\",\"geolocationCityName\":\"StLouis\",\"osFamilyId\":\"OS-C2CE1ED9011FA680\",\"osFamilyName\":\"Windows\",\"osVersionId\":\"OS-026191052160B96A\",\"osVersionName\":\"Windows8.1\"}"));
        indexBuilders.add(client().prepareIndex("useraction-1", "useraction", "10").setSource("{\"tenantId\":\"1\",\"visitId\":\"JAOAFJJHFJFIOIPFOGKAEHPGDKMGDGJA\",\"visitorId\":\"1446044073349GT206UWGPS02EDB7K985QKPMBIKCUD2R\",\"visitType\":\"RUXIT_SYNTHETIC\",\"bounce\":false,\"newVisitor\":false,\"name\":\"Loadingofpage/\",\"type\":\"Load\",\"startTime\":1446044230768,\"endTime\":1446044230988,\"duration\":220,\"applicationId\":\"APPLICATION-EA7C4B59F27D43EB\",\"browserFamilyId\":\"BROWSER-FF8EAE6885DE87B3\",\"browserFamilyName\":\"Chrome\",\"browserTypeId\":\"BROWSER-44C8EFE87C847CA6\",\"browserTypeName\":\"DesktopBrowser\",\"browserMajorVersionId\":\"BROWSER-B82CE0AD1FD3292B\",\"browserMajorVersionName\":\"Chrome38\",\"geolocationContinentId\":\"GEOLOCATION-970B6D0A98F55995\",\"geolocationContinentCode\":\"NA\",\"geolocationContinentName\":\"NorthAmerica\",\"geolocationCountryId\":\"GEOLOCATION-D39825E84010E068\",\"geolocationCountryCode\":\"US\",\"geolocationCountryName\":\"UnitedStates\",\"geolocationRegionId\":\"GEOLOCATION-A6153C0BB4FECE91\",\"geolocationRegionCode\":\"MO\",\"geolocationRegionName\":\"Missouri\",\"geolocationCityId\":\"GEOLOCATION-62F2889D258D8C59\",\"geolocationCityName\":\"StLouis\",\"osFamilyId\":\"OS-C2CE1ED9011FA680\",\"osFamilyName\":\"Windows\",\"osVersionId\":\"OS-026191052160B96A\",\"osVersionName\":\"Windows8.1\"}"));
        indexBuilders.add(client().prepareIndex("useraction-1", "useraction", "11").setSource("{}"));
        indexBuilders.add(client().prepareIndex("useraction-1", "useraction", "12").setSource("{\"visitorId\":\"1446044073349GT206UWGPS02EDB7K985QKPMBIKCUD2R\"}"));

        indexRandom(true, indexBuilders);
    }
}
