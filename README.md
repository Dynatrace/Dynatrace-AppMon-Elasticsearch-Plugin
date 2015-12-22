# Dynatrace-IBM-WebSphere-Commerce-Fastpack

![images_community/download/attachments/210011839/icon.png](/images_community/download/attachments/210011839/icon.png)

The Dynatrace FastPack for Elasticsearch provides a plugin for Dynatrace which collects various metrics from an Elasticsearch Cluster and sends them to a Dynatrace instance. It also provides a System Profile and a sample Dashboard which visualizes some of the metrics.

Find further information in the [dynaTrace community](https://community.compuwareapm.com/community/display/DL/Elasticsearch+FastPack). 

# Additional information

## Getting started

### Install the plugin

Download the .dtp file and install it on the Dynatrace Server

### Configure the Elasticsearch Monitor

Configure a scheduled task for the Elasticsearch Monitor and define the hostname/port of the Elasticsearch REST interface.

### System profile/Dashboards

The plugin also includes a system profile 'Elasticsearch' and a Dashboard 'Elasticsearch' with some sample Dashlets.

## Enhancing/Building/Development

#### Change it

Create matching Eclipse project files

	./gradlew eclipse

Run unit tests

	./gradlew check jacocoTestReport

This uses the Elasticsearch integration test framework to run local tests of all the plugin functionality. It is 
highly recommended to run these whenever you do code-changes! 

#### Build it

	./gradlew -PdynaTraceVersion=1.0.0.0  plugin

Note: Set the version higher every time you deploy to ensure the new version is loaded in the Dynatrace Server.

#### Deploy it

* The resulting .dtp file can be found in the directory `plugin/dist`
* Use the plugin-dialog in the Dynatrace Client to upload the plugin to the server. 
* Note that a restart of the Server or Collector which is running the plugin might be required 
  in some cases (e.g. when upgrading from a previous version of the plugin).
