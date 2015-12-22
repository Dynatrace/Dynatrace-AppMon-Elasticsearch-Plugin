/***************************************************
 * dynaTrace Diagnostics (c) Dynatrace LLC
 *
 * @author: dominik.stadler
 */
package com.dynatrace.diagnostics.plugins.elasticsearch;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.dynatrace.diagnostics.pdk.Monitor;
import com.dynatrace.diagnostics.pdk.MonitorEnvironment;
import com.dynatrace.diagnostics.pdk.MonitorMeasure;
import com.dynatrace.diagnostics.pdk.Status;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;


/**
 * A Monitor which polls the DebugUI of ruxit instances to collect measures about ruxit Agents.
 *
 * @author dominik.stadler
 */
public class ElasticsearchMonitor implements Monitor {
	private static final Logger log = Logger.getLogger(ElasticsearchMonitor.class.getName());

	/************************************** Config properties **************************/
	protected static final String ENV_CONFIG_URL = "url";
	protected static final String ENV_CONFIG_USER = "user";
	protected static final String ENV_CONFIG_PASSWORD = "password";
	protected static final String ENV_CONFIG_TIMEOUT = "timeout";

	/************************************** Metric Groups **************************/
	protected static final String METRIC_GROUP_ELASTICSEARCH = "Elasticsearch Monitor";

	/************************************** Measures **************************/
	protected static final String MSR_NODE_COUNT = "NodeCount";
	protected static final String MSR_DATA_NODE_COUNT = "DataNodeCount";

	protected static final String MSR_ACTIVE_PRIMARY_SHARDS = "ActivePrimaryShards";
	protected static final String MSR_ACTIVE_SHARDS_PERCENT = "ActiveShardsPercent";
	protected static final String MSR_ACTIVE_SHARDS = "ActiveShards";
	protected static final String MSR_RELOCATING_SHARDS = "RelocatingShards";
	protected static final String MSR_INITIALIZING_SHARDS = "InitializingShards";
	protected static final String MSR_UNASSIGNED_SHARDS = "UnassignedShards";
	protected static final String MSR_DELAYED_UNASSIGNED_SHARDS = "DelayedUnassignedShards";

	protected static final String MSR_MEM_INIT_HEAP = "InitHeap";
	protected static final String MSR_MEM_MAX_HEAP = "MaxHeap";
	protected static final String MSR_MEM_INIT_NON_HEAP = "InitNonHeap";
	protected static final String MSR_MEM_MAX_NON_HEAP = "MaxNonHeap";
	protected static final String MSR_MEM_MAX_DIRECT = "MaxDirect";

	protected static final String MSR_INDEX_COUNT = "IndexCount";
	protected static final String MSR_SHARD_COUNT = "ShardCount";

	protected static final String MSR_DOCUMENT_COUNT = "DocCount";
	protected static final String MSR_DELETED_COUNT = "DeletedCount";

	protected static final String MSR_STORE_SIZE = "StoreSize";
	protected static final String MSR_STORE_THROTTLE_TIME = "StoreThrottleTime";
	protected static final String MSR_INDEXING_THROTTLE_TIME = "IndexingThrottleTime";
	protected static final String MSR_INDEXING_CURRNT = "IndexingCurrent";
	protected static final String MSR_DELETE_CURRENT = "DeleteCurrent";
	protected static final String MSR_QUERY_CURRENT = "QueryCurrent";
	protected static final String MSR_FETCH_CURRENT = "FetchCurrent";
	protected static final String MSR_SCROLL_CURRENT = "ScrollCurrent";
	protected static final String MSR_QUERY_CACHE_SIZE = "QueryCacheSize";
	protected static final String MSR_FIELD_DATA_SIZE = "FieldDataSize";
	protected static final String MSR_FIELD_DATA_EVICTIONS = "FieldDataEvictions";
	protected static final String MSR_PERCOLATE_SIZE = "PercolateSize";
	protected static final String MSR_TRANSLOG_SIZE = "TranslogSize";
	protected static final String MSR_REQUEST_CACHE_SIZE = "RequestCacheSize";
	protected static final String MSR_RECOVERY_THROTTLE_TIME = "RecoveryThrottleTime";
	protected static final String MSR_RECOVERY_AS_SOURCE = "RecoveryAsSource";
	protected static final String MSR_RECOVERY_AS_TARGET = "RecoveryAsTarget";

	protected static final String MSR_COMPLETION_SIZE = "CompletionSize";
	protected static final String MSR_SEGMENT_COUNT = "SegmentCount";
	protected static final String MSR_SEGMENT_SIZE = "SegmentSize";
	protected static final String MSR_FILE_DESCRIPTIOR_COUNT = "FileDescriptorCount";
	protected static final String MSR_FILE_SYSTEM_SIZE = "FileSystemSize";
	protected static final String MSR_PERCOLATE_COUNT = "PercolateCount";

	// for easier testing
	protected static final String[] ALL_MEASURES  = new String[] {
			MSR_NODE_COUNT,
			MSR_DATA_NODE_COUNT,
			MSR_ACTIVE_PRIMARY_SHARDS,
			MSR_ACTIVE_SHARDS_PERCENT,
			MSR_ACTIVE_SHARDS,
			MSR_RELOCATING_SHARDS,
			MSR_INITIALIZING_SHARDS,
			MSR_UNASSIGNED_SHARDS,
			MSR_DELAYED_UNASSIGNED_SHARDS,
			MSR_MEM_INIT_HEAP,
			MSR_MEM_MAX_HEAP,
			MSR_MEM_INIT_NON_HEAP,
			MSR_MEM_MAX_NON_HEAP,
			MSR_MEM_MAX_DIRECT,
			MSR_INDEX_COUNT,
			MSR_SHARD_COUNT,
			MSR_DOCUMENT_COUNT,
			MSR_DELETED_COUNT,

			MSR_INDEXING_THROTTLE_TIME,
			MSR_INDEXING_CURRNT,
			MSR_DELETE_CURRENT,
			MSR_QUERY_CURRENT,
			MSR_FETCH_CURRENT,
			MSR_SCROLL_CURRENT,
			MSR_PERCOLATE_SIZE,
			MSR_TRANSLOG_SIZE,
			MSR_REQUEST_CACHE_SIZE,
			MSR_RECOVERY_THROTTLE_TIME,
			MSR_RECOVERY_AS_SOURCE,
			MSR_RECOVERY_AS_TARGET,

			MSR_COMPLETION_SIZE,
			MSR_SEGMENT_COUNT,
			MSR_SEGMENT_SIZE,
			MSR_FILE_DESCRIPTIOR_COUNT,
			MSR_FILE_SYSTEM_SIZE,
			MSR_PERCOLATE_COUNT,
			MSR_STORE_SIZE,
			MSR_STORE_THROTTLE_TIME,
			MSR_QUERY_CACHE_SIZE,
			MSR_FIELD_DATA_SIZE,
			MSR_FIELD_DATA_EVICTIONS,
	};

	/************************************** Variables for Configuration items **************************/

	private String url;
	private String user;
	private String password;
	private long timeout;

	private final ObjectMapper mapper = new ObjectMapper();

	/*
	 * (non-Javadoc)
	 *
	 * @see com.dynatrace.diagnostics.pdk.Monitor#setup(com.dynatrace.diagnostics.pdk.MonitorEnvironment)
	 */
	@Override
	public Status setup(MonitorEnvironment env) throws Exception {
		url = env.getConfigString(ENV_CONFIG_URL);
		if (url == null || url.isEmpty())
			throw new IllegalArgumentException(
					"Parameter <url> must not be empty");

		// normalize URL
		url = StringUtils.removeEnd(url, "/");

		user = env.getConfigString(ENV_CONFIG_USER);
		if(user == null) {
			// to not fail in Apache HTTP Client
			user = "";
		}
		password = env.getConfigPassword(ENV_CONFIG_PASSWORD);
		if(env.getConfigString(ENV_CONFIG_TIMEOUT) != null) {
			timeout = env.getConfigLong(ENV_CONFIG_TIMEOUT);
		} else {
			timeout = 60_000;
		}
		if(timeout < 0 || timeout > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("Timeout needs to be in range [0," + Integer.MAX_VALUE +"]");
		}

		return new Status(Status.StatusCode.Success);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.dynatrace.diagnostics.pdk.Monitor#execute(com.dynatrace.diagnostics.pdk.MonitorEnvironment)
	 */
	@Override
	public Status execute(MonitorEnvironment env) throws Exception {
		log.info("Executing Elasticsearch Monitor for URL: " + url);

		try {
			// retrieve measures for cloud formation numbers in each state
			measureEnvironments(env);
		} catch (Throwable e) {
			// Our plugin functionality does not report Exceptions well...
			log.log(Level.WARNING, "Had throwable while running Elasticsearch Monitor with url " + url + ": " + ExceptionUtils.getStackTrace(e));
			throw new Exception(e);
		}

		return new Status(Status.StatusCode.Success);
	}

	private void measureEnvironments(MonitorEnvironment env) throws IOException {
		//final Collection<Instance> environments = getEnvironments();

		// walk all the different regions that were specified
		Measure nodeCount = new Measure();
		Measure dataNodeCount = new Measure();

		Measure activePrimaryShards = new Measure();
		Measure activeShardsPercent = new Measure();
		Measure activeShards = new Measure();
		Measure relocatingShards = new Measure();
		Measure initializingShards = new Measure();
		Measure unassignedShards = new Measure();
		Measure delayedUnassignedShards = new Measure();

		// JVM Memory
		Measure initHeap = new Measure("Node");
		Measure maxHeap = new Measure("Node");
		Measure initNonHeap = new Measure("Node");
		Measure maxNonHeap = new Measure("Node");
		Measure maxDirect = new Measure("Node");

		// Index/Shards
		Measure indexCount = new Measure();
		Measure shardsPerState = new Measure("State");

		// Documents
		Measure documentCount = new Measure();
		Measure deletedCount = new Measure();

		// Stats
		Measure storeSizePerNode = new Measure("Node");
		Measure storeThrottleTimePerNode = new Measure("Node");
		Measure indexingThrottleTimePerNode = new Measure("Node");
		Measure indexingCurrentPerNode = new Measure("Node");
		Measure deleteCurrentPerNode = new Measure("Node");
		Measure queryCurrentPerNode = new Measure("Node");
		Measure fetchCurrentPerNode = new Measure("Node");
		Measure scrollCurrentPerNode = new Measure("Node");
		Measure queryCacheSizePerNode = new Measure("Node");
		Measure fieldDataSizePerNode = new Measure("Node");
		Measure percolateSizePerNode = new Measure("Node");
		Measure translogSizePerNode = new Measure("Node");
		Measure requestCacheSizePerNode = new Measure("Node");
		Measure recoveryThrottleTimePerNode = new Measure("Node");
		Measure recoveryAsSourcePerNode = new Measure("Node");
		Measure recoveryAsTargetPerNode = new Measure("Node");

		Measure fieldDataSize = new Measure();
		Measure fieldDataEvictions = new Measure();
		Measure queryCachePerState = new Measure("State");
		Measure completionSize = new Measure();
		Measure segmentCount = new Measure();
		Measure segmentSizePerState = new Measure("State");
		Measure fileDescPerStat = new Measure("Stat");
		Measure fileSystemPerStat = new Measure("Stat");
		Measure percolatePerState = new Measure("State");

		final CredentialsProvider credsProvider = new BasicCredentialsProvider();
		credsProvider.setCredentials(
                new AuthScope(null, -1),
                new UsernamePasswordCredentials(user, password));

		RequestConfig reqConfig = RequestConfig.custom()
			    .setSocketTimeout((int)timeout)
			    .setConnectTimeout((int)timeout)
			    .setConnectionRequestTimeout((int)timeout)
			    .build();

		// configure the builder for HttpClients
		HttpClientBuilder builder = HttpClients.custom()
		        .setDefaultCredentialsProvider(credsProvider)
				.setDefaultRequestConfig(reqConfig);

		try (CloseableHttpClient client = builder.build()) {
			retrieveClusterHealth(client, nodeCount, dataNodeCount, activePrimaryShards, activeShardsPercent, activeShards,
					relocatingShards, initializingShards, unassignedShards, delayedUnassignedShards);

			Map<String, String> nodeIdToName = retrieveNodeHealth(client, initHeap, maxHeap, initNonHeap, maxNonHeap, maxDirect);

			retrieveClusterState(client, nodeIdToName, indexCount, shardsPerState,
					fieldDataSize, fieldDataEvictions, queryCachePerState,
					completionSize, segmentCount, segmentSizePerState,
					fileDescPerStat, fileSystemPerStat, percolatePerState, documentCount, deletedCount);

			//retrieveIndexCounts(client, documentCountPerIndex, deletedCountPerIndex);

			retrieveNodeStats(client, storeSizePerNode, storeThrottleTimePerNode, indexingThrottleTimePerNode, indexingCurrentPerNode,
					deleteCurrentPerNode, queryCurrentPerNode, fetchCurrentPerNode, scrollCurrentPerNode, queryCacheSizePerNode,
					fieldDataSizePerNode, percolateSizePerNode, translogSizePerNode, requestCacheSizePerNode, recoveryThrottleTimePerNode,
					recoveryAsSourcePerNode, recoveryAsTargetPerNode);
		}

		// retrieve and set the measurements
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_NODE_COUNT, env, nodeCount);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_DATA_NODE_COUNT, env, dataNodeCount);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_ACTIVE_PRIMARY_SHARDS, env, activePrimaryShards);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_ACTIVE_SHARDS, env, activeShards);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_ACTIVE_SHARDS_PERCENT, env, activeShardsPercent);

		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_RELOCATING_SHARDS, env, relocatingShards);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_INITIALIZING_SHARDS, env, initializingShards);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_UNASSIGNED_SHARDS, env, unassignedShards);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_DELAYED_UNASSIGNED_SHARDS, env, delayedUnassignedShards);

		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_MEM_INIT_HEAP, env, initHeap);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_MEM_MAX_HEAP, env, maxHeap);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_MEM_INIT_NON_HEAP, env, initNonHeap);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_MEM_MAX_NON_HEAP, env, maxNonHeap);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_MEM_MAX_DIRECT, env, maxDirect);

		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_INDEX_COUNT, env, indexCount);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_SHARD_COUNT, env, shardsPerState);

		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_DOCUMENT_COUNT, env, documentCount);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_DELETED_COUNT, env, deletedCount);

		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_STORE_SIZE, env, storeSizePerNode);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_STORE_THROTTLE_TIME, env, storeThrottleTimePerNode);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_INDEXING_THROTTLE_TIME, env, indexingThrottleTimePerNode);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_INDEXING_CURRNT, env, indexingCurrentPerNode);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_DELETE_CURRENT, env, deleteCurrentPerNode);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_QUERY_CURRENT, env, queryCurrentPerNode);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_FETCH_CURRENT, env, fetchCurrentPerNode);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_SCROLL_CURRENT, env, scrollCurrentPerNode);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_QUERY_CACHE_SIZE, env, queryCacheSizePerNode);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_FIELD_DATA_SIZE, env, fieldDataSizePerNode);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_PERCOLATE_SIZE, env, percolateSizePerNode);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_TRANSLOG_SIZE, env, translogSizePerNode);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_REQUEST_CACHE_SIZE, env, requestCacheSizePerNode);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_RECOVERY_THROTTLE_TIME, env, recoveryThrottleTimePerNode);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_RECOVERY_AS_SOURCE, env, recoveryAsSourcePerNode);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_RECOVERY_AS_TARGET, env, recoveryAsTargetPerNode);

		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_FIELD_DATA_SIZE, env, fieldDataSize);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_FIELD_DATA_EVICTIONS, env, fieldDataEvictions);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_QUERY_CACHE_SIZE, env, queryCachePerState);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_COMPLETION_SIZE, env, completionSize);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_SEGMENT_COUNT, env, segmentCount);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_SEGMENT_SIZE, env, segmentSizePerState);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_FILE_DESCRIPTIOR_COUNT, env, fileDescPerStat);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_FILE_SYSTEM_SIZE, env, fileSystemPerStat);
		writeMeasure(METRIC_GROUP_ELASTICSEARCH, MSR_PERCOLATE_COUNT, env, percolatePerState);
	}

	/* does not work reliably and seems to be a costly operation
	private void retrieveIndexCounts(CloseableHttpClient client, Measure documentCount, Measure deletedCount) throws IOException {
		String json = simpleGet(client, url + "/_cat/indices");

		String [] indexes = json.split("[\\r\\n]");
		for(String index : indexes) {
			String[] items = index.split("\\s+");

			// green open visit-4               12 1 16039976    0  18.7gb   9.3gb
			String name = items[2];
			long docCount = Long.parseLong(items[5]);
			long delCount = Long.parseLong(items[6]);

			documentCount.addValue(docCount);
			documentCount.addDynamicMeasure(name, docCount);

			deletedCount.addValue(delCount);
			deletedCount.addDynamicMeasure(name, delCount);
		}
	}*/

	private void retrieveClusterState(CloseableHttpClient client, Map<String,String> nodeIdToName,
			Measure indexCount, Measure shardsPerState,
			Measure fieldDataSize, Measure fieldDataEvictions, Measure queryCachePerState,
			Measure completionSize, Measure segmentCount, Measure segmentSizePerState,
			Measure fileDescPerStat, Measure fileSystemPerStat, Measure percolatePerState,
			Measure documentCount, Measure deletedCount) throws IOException {
		String json = simpleGet(client, url + "/_cluster/stats");
		JsonNode clusterStats = mapper.readTree(json);

		JsonNode index = clusterStats.get("indices");

		indexCount.setValue(index.get("count").asDouble());

		JsonNode shards = index.get("shards");
		shardsPerState.setValue(shards.get("total").asDouble());
		shardsPerState.addDynamicMeasure("primary", shards.get("primaries").asDouble());
		shardsPerState.addDynamicMeasure("replicationFactor", shards.get("replication").asDouble());

		JsonNode docs = index.get("docs");
		documentCount.setValue(docs.get("count").asDouble());
		deletedCount.setValue(docs.get("deleted").asDouble());

		/*JsonNode store = index.get("store");
		storeSize.setValue(store.get("size_in_bytes").asDouble());
		storeThrottleTime.setValue(store.get("throttle_time_in_millis").asDouble());*/

		JsonNode fielddata = index.get("fielddata");
		fieldDataSize.setValue(fielddata.get("memory_size_in_bytes").asDouble());
		fieldDataEvictions.setValue(fielddata.get("evictions").asDouble());

		JsonNode query_cache = index.get("query_cache");
		queryCachePerState.setValue(query_cache.get("memory_size_in_bytes").asDouble());
		addDynamicMeasure(queryCachePerState, query_cache, "total_count");
		addDynamicMeasure(queryCachePerState, query_cache, "hit_count");
		addDynamicMeasure(queryCachePerState, query_cache, "miss_count");
		addDynamicMeasure(queryCachePerState, query_cache, "cache_size");
		addDynamicMeasure(queryCachePerState, query_cache, "cache_count");
		addDynamicMeasure(queryCachePerState, query_cache, "evictions");

		JsonNode completion = index.get("completion");
		completionSize.setValue(completion.get("size_in_bytes").asDouble());

		JsonNode segments = index.get("segments");
		segmentCount.setValue(segments.get("count").asDouble());

		addDynamicMeasure(segmentSizePerState, segments, "count");
		addDynamicMeasure(segmentSizePerState, segments, "memory_in_bytes");
		addDynamicMeasure(segmentSizePerState, segments, "terms_memory_in_bytes");
		addDynamicMeasure(segmentSizePerState, segments, "stored_fields_memory_in_bytes");
		addDynamicMeasure(segmentSizePerState, segments, "term_vectors_memory_in_bytes");
		addDynamicMeasure(segmentSizePerState, segments, "norms_memory_in_bytes");
		addDynamicMeasure(segmentSizePerState, segments, "doc_values_memory_in_bytes");
		addDynamicMeasure(segmentSizePerState, segments, "index_writer_memory_in_bytes");
		addDynamicMeasure(segmentSizePerState, segments, "index_writer_max_memory_in_bytes");
		addDynamicMeasure(segmentSizePerState, segments, "version_map_memory_in_bytes");
		addDynamicMeasure(segmentSizePerState, segments, "fixed_bit_set_memory_in_bytes");

		JsonNode percolate = index.get("percolate");
		percolatePerState.setValue(percolate.get("current").asDouble());
		addDynamicMeasure(percolatePerState, percolate, "total");
		addDynamicMeasure(percolatePerState, percolate, "time_in_millis");
		addDynamicMeasure(percolatePerState, percolate, "current");
		addDynamicMeasure(percolatePerState, percolate, "memory_size_in_bytes");
		// not a double: addDynamicMeasure(percolatePerState, percolate, "memory_size");
		addDynamicMeasure(percolatePerState, percolate, "queries");


		JsonNode nodes = clusterStats.get("nodes");

		JsonNode process = nodes.get("process");

		JsonNode fileDesc = process.get("open_file_descriptors");
		fileDescPerStat.setValue(fileDesc.get("max").asDouble());
		addDynamicMeasure(fileDescPerStat, fileDesc, "min");
		addDynamicMeasure(fileDescPerStat, fileDesc, "max");
		addDynamicMeasure(fileDescPerStat, fileDesc, "avg");

		JsonNode fs = nodes.get("fs");
		// this was missing in tests sometimes
		if(fs != null) {
			JsonNode free = fs.get("free_in_bytes");
			if(free != null) {
				fileSystemPerStat.setValue(free.asDouble());
			}
			addDynamicMeasure(fileSystemPerStat, fs, "total_in_bytes");
			addDynamicMeasure(fileSystemPerStat, fs, "free_in_bytes");
			addDynamicMeasure(fileSystemPerStat, fs, "available_in_bytes");
		}
	}

	private void addDynamicMeasure(Measure measure, JsonNode jsonNode, String jsonMeasure) {
		JsonNode value = jsonNode.get(jsonMeasure);
		if(value != null) {
			measure.addDynamicMeasure(jsonMeasure, value.asDouble());
		}
	}

	private Map<String,String> retrieveNodeHealth(CloseableHttpClient client, Measure initHeap, Measure maxHeap,
			Measure initNonHeap, Measure maxNonHeap, Measure maxDirect) throws IOException {
		Map<String,String> nodeIdToName = new HashMap<>();

		String json = simpleGet(client, url + "/_nodes");
		JsonNode nodeHealth = mapper.readTree(json);

		Iterator<Map.Entry<String,JsonNode>> nodes = nodeHealth.get("nodes").fields();
		while(nodes.hasNext()) {
			Map.Entry<String,JsonNode> node = nodes.next();
			String nodeName = checkNotNull(node.getValue().get("name").asText());
			nodeIdToName.put(node.getKey(), nodeName);

			JsonNode mem = node.getValue().get("jvm").get("mem");

			initHeap.addValue(mem.get("heap_init_in_bytes").asLong());
			initHeap.addDynamicMeasure(nodeName, mem.get("heap_init_in_bytes").asLong());

			maxHeap.addValue(mem.get("heap_max_in_bytes").asLong());
			maxHeap.addDynamicMeasure(nodeName, mem.get("heap_max_in_bytes").asLong());

			initNonHeap.addValue(mem.get("non_heap_init_in_bytes").asLong());
			initNonHeap.addDynamicMeasure(nodeName, mem.get("non_heap_init_in_bytes").asLong());

			maxNonHeap.addValue(mem.get("non_heap_max_in_bytes").asLong());
			maxNonHeap.addDynamicMeasure(nodeName, mem.get("non_heap_max_in_bytes").asLong());

			maxDirect.addValue(mem.get("direct_max_in_bytes").asLong());
			maxDirect.addDynamicMeasure(nodeName, mem.get("direct_max_in_bytes").asLong());
		}

		return nodeIdToName;
	}

	private void retrieveNodeStats(CloseableHttpClient client, Measure storeSizePerNode, Measure storeThrottleTimePerNode,
			Measure indexingThrottleTimePerNode, Measure indexingCurrentPerNode, Measure deleteCurrentPerNode,
			Measure queryCurrentPerNode, Measure fetchCurrentPerNode, Measure scrollCurrentPerNode,
			Measure queryCacheSizePerNode, Measure fieldDataSizePerNode, Measure percolateSizePerNode,
			Measure translogSizePerNode, Measure requestCacheSizePerNode, Measure recoveryThrottleTimePerNode,
			Measure recoveryAsSourcePerNode, Measure recoveryAsTargetPerNode) throws IOException {
		String json = simpleGet(client, url + "/_nodes/stats");
		JsonNode nodeHealth = mapper.readTree(json);

		Iterator<Map.Entry<String,JsonNode>> nodes = nodeHealth.get("nodes").fields();
		while(nodes.hasNext()) {
			Map.Entry<String,JsonNode> node = nodes.next();
			String nodeName = checkNotNull(node.getValue().get("name").asText());

			JsonNode store = node.getValue().get("indices").get("store");

			storeSizePerNode.addValue(store.get("size_in_bytes").asLong());
			storeSizePerNode.addDynamicMeasure(nodeName, store.get("size_in_bytes").asLong());

			storeThrottleTimePerNode.addValue(store.get("throttle_time_in_millis").asLong());
			storeThrottleTimePerNode.addDynamicMeasure(nodeName, store.get("throttle_time_in_millis").asLong());

			JsonNode indexing = node.getValue().get("indices").get("indexing");

			indexingThrottleTimePerNode.addValue(indexing.get("throttle_time_in_millis").asLong());
			indexingThrottleTimePerNode.addDynamicMeasure(nodeName, indexing.get("throttle_time_in_millis").asLong());

			indexingCurrentPerNode.addValue(indexing.get("index_current").asLong());
			indexingCurrentPerNode.addDynamicMeasure(nodeName, indexing.get("index_current").asLong());

			deleteCurrentPerNode.addValue(indexing.get("delete_current").asLong());
			deleteCurrentPerNode.addDynamicMeasure(nodeName, indexing.get("delete_current").asLong());

			JsonNode search = node.getValue().get("indices").get("search");

			queryCurrentPerNode.addValue(search.get("query_current").asLong());
			queryCurrentPerNode.addDynamicMeasure(nodeName, search.get("query_current").asLong());

			fetchCurrentPerNode.addValue(search.get("fetch_current").asLong());
			fetchCurrentPerNode.addDynamicMeasure(nodeName, search.get("fetch_current").asLong());

			scrollCurrentPerNode.addValue(search.get("scroll_current").asLong());
			scrollCurrentPerNode.addDynamicMeasure(nodeName, search.get("scroll_current").asLong());

			JsonNode queryCache = node.getValue().get("indices").get("query_cache");

			queryCacheSizePerNode.addValue(queryCache.get("memory_size_in_bytes").asLong());
			queryCacheSizePerNode.addDynamicMeasure(nodeName, queryCache.get("memory_size_in_bytes").asLong());

			JsonNode fieldData = node.getValue().get("indices").get("fielddata");

			fieldDataSizePerNode.addValue(fieldData.get("memory_size_in_bytes").asLong());
			fieldDataSizePerNode.addDynamicMeasure(nodeName, fieldData.get("memory_size_in_bytes").asLong());

			JsonNode percolate = node.getValue().get("indices").get("percolate");

			percolateSizePerNode.addValue(percolate.get("memory_size_in_bytes").asLong());
			percolateSizePerNode.addDynamicMeasure(nodeName, percolate.get("memory_size_in_bytes").asLong());

			JsonNode translog = node.getValue().get("indices").get("translog");

			translogSizePerNode.addValue(translog.get("size_in_bytes").asLong());
			translogSizePerNode.addDynamicMeasure(nodeName, translog.get("size_in_bytes").asLong());

			JsonNode requestCache = node.getValue().get("indices").get("request_cache");

			requestCacheSizePerNode.addValue(requestCache.get("memory_size_in_bytes").asLong());
			requestCacheSizePerNode.addDynamicMeasure(nodeName, requestCache.get("memory_size_in_bytes").asLong());

			JsonNode recovery = node.getValue().get("indices").get("recovery");

			recoveryThrottleTimePerNode.addValue(recovery.get("throttle_time_in_millis").asLong());
			recoveryThrottleTimePerNode.addDynamicMeasure(nodeName, recovery.get("throttle_time_in_millis").asLong());

			recoveryAsSourcePerNode.addValue(recovery.get("current_as_source").asLong());
			recoveryAsSourcePerNode.addDynamicMeasure(nodeName, recovery.get("current_as_source").asLong());

			recoveryAsTargetPerNode.addValue(recovery.get("current_as_target").asLong());
			recoveryAsTargetPerNode.addDynamicMeasure(nodeName, recovery.get("current_as_target").asLong());
		}
	}

	private void retrieveClusterHealth(CloseableHttpClient client, Measure nodeCount, Measure dataNodeCount, Measure activePrimaryShards, Measure activeShardsPercent, Measure activeShards,
			Measure relocatingShards, Measure initializingShards, Measure unassignedShards, Measure delayedUnassignedShards) throws IOException {
		String json = simpleGet(client, url + "/_cluster/health");
		JsonNode clusterHealth = mapper.readTree(json);

		nodeCount.setValue(clusterHealth.get("number_of_nodes").asLong());
		dataNodeCount.setValue(clusterHealth.get("number_of_data_nodes").asLong());
		activePrimaryShards.setValue(clusterHealth.get("active_primary_shards").asLong());
		activeShardsPercent.setValue(clusterHealth.get("active_shards_percent_as_number").asDouble());
		activeShards.setValue(clusterHealth.get("active_shards").asLong());
		relocatingShards.setValue(clusterHealth.get("relocating_shards").asLong());
		initializingShards.setValue(clusterHealth.get("initializing_shards").asLong());
		unassignedShards.setValue(clusterHealth.get("unassigned_shards").asLong());
		delayedUnassignedShards.setValue(clusterHealth.get("delayed_unassigned_shards").asLong());
	}

	/*private void retrievePSGInformation(Measure psgCount, Measure psgCountByEnvironment,
			Measure psgCountByOs, Measure psgCountByVersion, Measure psgCountByInstallerVersion,
			Instance environment, DebugUIAccess download) throws IOException {
		if(log.isLoggable(Level.FINE)) {
			log.fine("Retrieving private security gateway information for environment: " + environment);
		}
		List<Base> psgs = download.getSecurityGateways(environment);
		for(Base base : psgs) {
			String privateCollector = base.get("privateCollector");
			if(!privateCollector.equalsIgnoreCase("true")) {
				continue;
			}

			psgCount.addValue(1);

			psgCountByEnvironment.addValue(1);
			psgCountByEnvironment.addDynamicMeasure(environment.name(), 1);

			psgCountByOs.addValue(1);
			psgCountByOs.addDynamicMeasure(base.get("osInfo"), 1);

			psgCountByVersion.addValue(1);
			psgCountByVersion.addDynamicMeasure(base.get("buildVersion"), 1);

			psgCountByInstallerVersion.addValue(1);
			psgCountByInstallerVersion.addDynamicMeasure(base.get("productVersion"), 1);
		}
	}
*/

	/**
	 *
	 * @param env
	 * @param value
	 * @param dynamicMeasures
	 */
	protected void writeMeasure(String group, String name, MonitorEnvironment env, Measure value) {
		Collection<MonitorMeasure> measures = env.getMonitorMeasures(group, name);
		if (measures != null) {
			if (log.isLoggable(Level.INFO)) {
				log.info("Setting measure '" + name + "' to value " + value.getValue() +
						(value.getDynamicMeasureName() == null ? "" :
							", dynamic: " + value.getDynamicMeasureName() + ": " + value.getDynamicMeasures()) +
						", measures: " + measures);
			}
			for (MonitorMeasure measure : measures) {
				measure.setValue(value.getValue());

				if(value.getDynamicMeasures().size() > 0) {
					// TODO: somehow we need to write this once more, why is this necessary?!?
					Measure copyMeasure = new Measure();
					copyMeasure.setValue(value.getValue());
					writeMeasure(group, name, env, copyMeasure);

				    // for this subscribed measure we want to create a dynamic measure
					for(Map.Entry<String, Double> dynamic : value.getDynamicMeasures().entrySet()) {
						Preconditions.checkNotNull(value.getDynamicMeasureName(), "Had null as dynamic measure name for measure %s and dynamic measures %s", measure, value.getDynamicMeasures());
						Preconditions.checkNotNull(dynamic.getKey(), "Had null as dynamic measure key for measure %s and dynamic measures %s", measure, value.getDynamicMeasures());

						MonitorMeasure dynamicMeasure = env.createDynamicMeasure(measure, value.getDynamicMeasureName(), dynamic.getKey());
						dynamicMeasure.setValue(dynamic.getValue());
					}
				}
			}
		} else {
			log.warning("Could not find measure " + name + "@" + group + ", tried to report value: " + value);
		}
	}

	public String simpleGet(CloseableHttpClient httpClient, String url) throws IOException {
		// Required to avoid two requests instead of one: See http://stackoverflow.com/questions/20914311/httpclientbuilder-basic-auth
		AuthCache authCache = new BasicAuthCache();
		BasicScheme basicAuth = new BasicScheme();

		// Generate BASIC scheme object and add it to the local auth cache
		URL cacheUrl = new URL(url);
		HttpHost targetHost = new HttpHost(cacheUrl.getHost(), cacheUrl.getPort(), cacheUrl.getProtocol());
		authCache.put(targetHost, basicAuth);

		// Add AuthCache to the execution context
		HttpClientContext context = HttpClientContext.create();
		//context.setCredentialsProvider(credsProvider);
		context.setAuthCache(authCache);

		final HttpGet httpGet = new HttpGet(url);
		try (CloseableHttpResponse response = httpClient.execute(targetHost, httpGet, context)) {
			int statusCode = response.getStatusLine().getStatusCode();
			if(statusCode != 200) {
				String msg = "Had HTTP StatusCode " + statusCode + " for request: " + url + ", response: " + response.getStatusLine().getReasonPhrase();
				log.warning(msg);

				throw new IOException(msg);
			}
		    HttpEntity entity = response.getEntity();

		    try {
		    	return IOUtils.toString(entity.getContent());
		    } finally {
			    // ensure all content is taken out to free resources
			    EntityUtils.consume(entity);
		    }
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.dynatrace.diagnostics.pdk.Monitor#teardown(com.dynatrace.diagnostics.pdk.MonitorEnvironment)
	 */
	@Override
	public void teardown(MonitorEnvironment env) throws Exception {
		// nothing to do here
	}

}
