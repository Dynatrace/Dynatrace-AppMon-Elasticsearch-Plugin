# Dynatrace-Elasticsearch-Plugin

![testresources/icon.png](/testresources/icon.png)

The Dynatrace FastPack for Elasticsearch provides a plugin for Dynatrace AppMon which collects various metrics from an Elasticsearch Cluster and sends them to a Dynatrace instance. It also provides a System Profile and a sample Dashboard which visualizes some of the metrics.

Find further information in the [dynaTrace community](https://community.compuwareapm.com/community/display/DL/Elasticsearch+FastPack). 

[![Build Status](https://travis-ci.org/Dynatrace/Dynatrace-Elasticsearch-Plugin.svg)](https://travis-ci.org/Dynatrace/Dynatrace-Elasticsearch-Plugin) [![Gradle Status](https://gradleupdate.appspot.com/dynaTrace/Dynatrace-Elasticsearch-Plugin/status.svg?branch=master)](https://gradleupdate.appspot.com/dynaTrace/Dynatrace-Elasticsearch-Plugin/status)

# Additional information

## Prerequisites

* Dynatrace Application Monitoring version: 6.2+
* Elasticsearch 1.3.9 or higher

## Install the plugin

Download the .dtp file from the release-area and install it on the Dynatrace Server

## Configure the Elasticsearch Monitor

Configure the provided scheduled task for the Elasticsearch Monitor and define the hostname/port of the Elasticsearch REST interface. The default port number is 9200 unless it was changed in the Elasticsearch configuration.

## Optional: Inject Agents in Elasticsearch Nodes

If you want additional metrics like CPU usages, JVM memory and other host/process level metrics you can inject the Dynatrace Agent and use the provided Agent Mapping "Elasticsearch", then some of the process/host-level Dashlets in the Dashboard will show additional information.

## System profile/Dashboards

The plugin also includes a system profile 'Elasticsearch' and a Dashboard 'Elasticsearch' with some sample Dashlets.

## Enhancing/Building/Development

### Change it

Create matching Eclipse project files

	./gradlew eclipse

Run unit tests against all supported Elasticsearch versions

	./gradlew check jacocoTestReport && ./checkElasticsearchVersions.sh

This uses the Elasticsearch integration test framework to run local tests of all the plugin functionality. It is 
highly recommended to run these whenever you do code-changes! 

### Build it

	./gradlew -PdynaTraceVersion=1.0.0.<x> plugin

Note: Set the version higher every time you deploy to ensure the new version is loaded in the Dynatrace Server.

### Deploy it

* The resulting .dtp file can be found in the directory `plugin/dist`
* Use the plugin-dialog in the Dynatrace Client to upload the plugin to the server. 
* Note that a restart of the Server or Collector which is running the plugin might be required 
  in some cases (e.g. when upgrading from a previous version of the plugin).
