package io.prometheus.wls.rest.domain;
/*
 * Copyright (c) 2017 Oracle and/or its affiliates
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
 */

import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.HashMap;
import java.util.Map;

import static io.prometheus.wls.rest.domain.ExporterConfigTest.QueryHierarchyMatcher.hasQueryFor;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * @author Russell Gold
 */
public class ExporterConfigTest {
    private static final String EXPECTED_HOST = "somehost";
    private static final int EXPECTED_PORT = 3456;
    private static final String SERVLET_CONFIG = "---\n" +
            "host: " + EXPECTED_HOST + "\n" +
            "port: " + EXPECTED_PORT + "\n" +
            "queries:\n" +
            "- applicationRuntimes:\n" +
            "    key: name\n" +
            "    componentRuntimes:\n" +
            "      type: WebAppComponentRuntime\n" +
            "      prefix: webapp_config_\n" +
            "      key: name\n" +
            "      values: [deploymentState, type, contextRoot, sourceInfo, openSessionsHighCount, openSessionsCurrentCount, sessionsOpenedTotalCount]\n" +
            "      servlets:\n" +
            "        prefix: weblogic_servlet_\n" +
            "        key: servletName\n" +
            "        values: [invocationTotalCount, executionTimeTotal]\n";
    private static final Map<String, Object> NULL_MAP = null;

    private Map<String,Object> yamlConfig = new HashMap<>();

    @Test
    public void whenYamlConfigEmpty_returnNonNullConfiguration() throws Exception {
        assertThat(ExporterConfig.loadConfig(NULL_MAP), notNullValue());
    }

    @Test
    public void whenYamlConfigEmpty_returnDefaultConfiguration() throws Exception {
        ExporterConfig config = ExporterConfig.loadConfig(NULL_MAP);

        assertThat(config.getMetricsNameSnakeCase(), equalTo(false));
    }

    @Test
    public void whenYamlConfigEmpty_queryReturnsEmptyArray() throws Exception {
        ExporterConfig config = ExporterConfig.loadConfig(NULL_MAP);

        assertThat(config.getQueries(), emptyArray());
    }

    @Test
    public void whenSpecified_readSnakeCaseSettingFromYaml() throws Exception {
        yamlConfig.put(ExporterConfig.SNAKE_CASE, true);

        ExporterConfig config = ExporterConfig.loadConfig(yamlConfig);

        assertThat(config.getMetricsNameSnakeCase(), is(true));
     }

    @Test
    public void whenSpecified_readHostAndPortFromYaml() throws Exception {
        yamlConfig.put(ExporterConfig.HOST, EXPECTED_HOST);
        yamlConfig.put(ExporterConfig.PORT, EXPECTED_PORT);

        ExporterConfig config = ExporterConfig.loadConfig(yamlConfig);

        assertThat(config.getHost(), equalTo(EXPECTED_HOST));
        assertThat(config.getPort(), equalTo(EXPECTED_PORT));
    }

    @Test
    public void whenNotSpecified_useDefaultHostAndPort() throws Exception {
        ExporterConfig config = ExporterConfig.loadConfig(yamlConfig);

        assertThat(config.getHost(), equalTo(ExporterConfig.DEFAULT_HOST));
        assertThat(config.getPort(), equalTo(ExporterConfig.DEFAULT_PORT));
    }

    @Test
    public void whenNotSpecified_querySyncConfigurationIsNull() throws Exception {
        ExporterConfig config = ExporterConfig.loadConfig(yamlConfig);

        assertThat(config.getQuerySyncConfiguration(), nullValue());
    }

    @Test(expected = ConfigurationException.class)
    public void whenQuerySyncDefinedWithoutProperties_throwException() throws Exception {
        loadFromString(CONFIG_WITH_MISSING_SYNC_PROPERTIES);
    }

    private static final String CONFIG_WITH_MISSING_SYNC_PROPERTIES =
            "query_sync:";


    @Test(expected = ConfigurationException.class)
    public void whenQuerySyncDefinedWithoutUrl_throwException() throws Exception {
        loadFromString(CONFIG_WITH_MISSING_SYNC_URL);
    }

    private static final String CONFIG_WITH_MISSING_SYNC_URL =
            "query_sync:\n  interval: 3";


    @Test
    public void whenQuerySyncDefined_returnIt() throws Exception {
        ExporterConfig config = loadFromString(CONFIG_WITH_SYNC_SPEC);
        final QuerySyncConfiguration syncConfiguration = config.getQuerySyncConfiguration();

        assertThat(syncConfiguration.getUrl(), equalTo("http://sync:8999/"));
        assertThat(syncConfiguration.getRefreshInterval(), equalTo(3L));
    }

    private static final String CONFIG_WITH_SYNC_SPEC =
            "query_sync:\n  url: http://sync:8999/\n  interval: 3";


    @Test
    public void whenQuerySyncDefinedWithoutInterval_useDefault() throws Exception {
        ExporterConfig config = loadFromString(CONFIG_WITH_SYNC_URL);
        final QuerySyncConfiguration syncConfiguration = config.getQuerySyncConfiguration();

        assertThat(syncConfiguration.getUrl(), equalTo("http://sync:8999/"));
        assertThat(syncConfiguration.getRefreshInterval(), equalTo(10L));
    }

    private static final String CONFIG_WITH_SYNC_URL =
            "query_sync:\n  url: http://sync:8999/\n";

    @Test
    public void whenSpecified_readQueriesFromYaml() throws Exception {
        ExporterConfig config = loadFromString(SERVLET_CONFIG);

        assertThat(config.getQueries(), arrayWithSize(1));
    }

    @SuppressWarnings("unchecked")
    private ExporterConfig loadFromString(String yamlString) {
        yamlConfig = (Map<String, Object>) new Yaml().load(yamlString);

        return ExporterConfig.loadConfig(yamlConfig);
    }

    @Test
    public void afterLoad_hasExpectedQuery() throws Exception {
        ExporterConfig config = loadFromString(SERVLET_CONFIG);

        assertThat(config, hasQueryFor("applicationRuntimes", "componentRuntimes"));
    }

    @Test
    public void afterLoad_convertToString() throws Exception {
        ExporterConfig config = loadFromString(SNAKE_CASE_CONFIG);

        assertThat(config.toString(), equalToIgnoringWhiteSpace(SNAKE_CASE_CONFIG));
    }

    @Test
    public void includeSnakeCaseTrueSettingInToString() throws Exception {
        ExporterConfig config = loadFromString(SNAKE_CASE_CONFIG);

        assertThat(config.toString(), equalToIgnoringWhiteSpace(SNAKE_CASE_CONFIG));
    }

    private static final String SNAKE_CASE_CONFIG =
            "metricsNameSnakeCase: true\n" +
            "queries:\n" +
            "- applicationRuntimes:\n" +
            "    key: name\n" +
            "    workManagerRuntimes:\n" +
            "      prefix: workmanager_\n" +
            "      key: applicationName\n" +
            "      values: [pendingRequests, completedRequests, stuckThreadCount]\n";

    @Test
    public void afterAppend_configHasOriginalDestination() throws Exception {
        ExporterConfig config = getAppendedConfiguration(SERVLET_CONFIG, WORK_MANAGER_CONFIG);

        assertThat(config.getHost(), equalTo(EXPECTED_HOST));
        assertThat(config.getPort(), equalTo(EXPECTED_PORT));
    }

    private ExporterConfig getAppendedConfiguration(String firstConfiguration, String secondConfiguration) {
        ExporterConfig config = loadFromString(firstConfiguration);
        ExporterConfig config2 = loadFromString(secondConfiguration);
        config.append(config2);
        return config;
    }

    private static final String WORK_MANAGER_CONFIG = "---\n" +
            "host: otherhost\n" +
            "port: 9876\n" +
            "metricsNameSnakeCase: true\n" +
            "queries:\n" +
            "- applicationRuntimes:\n" +
            "    key: name\n" +
            "    workManagerRuntimes:\n" +
            "      prefix: workmanager_\n" +
            "      key: applicationName\n" +
            "      values: [pendingRequests, completedRequests, stuckThreadCount]\n";

    @Test
    public void afterAppend_configHasOriginalSnakeCase() throws Exception {
        assertThat(getAppendedConfiguration(SERVLET_CONFIG, WORK_MANAGER_CONFIG).getMetricsNameSnakeCase(), is(false));
        assertThat(getAppendedConfiguration(WORK_MANAGER_CONFIG, SERVLET_CONFIG).getMetricsNameSnakeCase(), is(true));
     }

    @Test
    public void afterAppend_configHasOriginalQuery() throws Exception {
        ExporterConfig config = getAppendedConfiguration(SERVLET_CONFIG, WORK_MANAGER_CONFIG);

        assertThat(config, hasQueryFor("applicationRuntimes", "componentRuntimes", "servlets"));
    }

    @Test
    public void afterAppend_configContainsNewQuery() throws Exception {
        ExporterConfig config = getAppendedConfiguration(SERVLET_CONFIG, WORK_MANAGER_CONFIG);

        assertThat(config, hasQueryFor("applicationRuntimes", "workManagerRuntimes"));
    }

    @Test
    public void whenAppendToNoQueries_configHasNewQuery() throws Exception {
        ExporterConfig config = getAppendedConfiguration("", WORK_MANAGER_CONFIG);

        assertThat(config, hasQueryFor("applicationRuntimes", "workManagerRuntimes"));
    }

    @Test
    public void whenAppendedConfigurationHasNoQueries_configHasOriginalQuery() throws Exception {
        ExporterConfig config = getAppendedConfiguration(SERVLET_CONFIG, "");

        assertThat(config, hasQueryFor("applicationRuntimes", "componentRuntimes", "servlets"));
    }

    @Test
    public void afterReplace_configHasOriginalDestination() throws Exception {
        ExporterConfig config = getReplacedConfiguration(SERVLET_CONFIG, WORK_MANAGER_CONFIG);

        assertThat(config.getHost(), equalTo(EXPECTED_HOST));
        assertThat(config.getPort(), equalTo(EXPECTED_PORT));
    }

    private ExporterConfig getReplacedConfiguration(String firstConfiguration, String secondConfiguration) {
        ExporterConfig config = loadFromString(firstConfiguration);
        ExporterConfig config2 = loadFromString(secondConfiguration);
        config.replace(config2);
        return config;
    }

    @Test
    public void afterReplace_configHasChangedSnakeCase() throws Exception {
        assertThat(getReplacedConfiguration(SERVLET_CONFIG, WORK_MANAGER_CONFIG).getMetricsNameSnakeCase(), is(true));
        assertThat(getReplacedConfiguration(WORK_MANAGER_CONFIG, SERVLET_CONFIG).getMetricsNameSnakeCase(), is(false));
     }

    @Test
    public void afterReplace_configHasNewQuery() throws Exception {
        ExporterConfig config = getReplacedConfiguration(SERVLET_CONFIG, WORK_MANAGER_CONFIG);

        assertThat(config, hasQueryFor("applicationRuntimes", "workManagerRuntimes"));
    }

    @Test
    public void afterReplace_configDoesNotHaveOriginalQuery() throws Exception {
        ExporterConfig config = getReplacedConfiguration(SERVLET_CONFIG, WORK_MANAGER_CONFIG);

        assertThat(config, not(hasQueryFor("applicationRuntimes", "componentRuntimes", "servlets")));
    }

    @Test
    public void afterReplaceWithEmptyConfig_configHasNoQueries() throws Exception {
        ExporterConfig config = getReplacedConfiguration(SERVLET_CONFIG, "");

        assertThat(config.getQueries(), arrayWithSize(0));
    }


    @Test
    public void whenYamlContainsMergeableQueries_MergeThem() throws Exception {
        ExporterConfig config = loadFromString(MERGEABLE_CONFIG);

        assertThat(config.toString(), equalTo(MERGED_CONFIG));
    }

    private static final String MERGEABLE_CONFIG =
            "host: otherhost\n" +
            "port: 9876\n" +
            "metricsNameSnakeCase: true\n" +
            "queries:\n" +
            "- applicationRuntimes:\n" +
            "    key: name\n" +
            "    workManagerRuntimes:\n" +
            "      prefix: workmanager_\n" +
            "      key: applicationName\n" +
            "      values: [pendingRequests, completedRequests, stuckThreadCount]\n" +
            "- applicationRuntimes:\n" +
            "    key: name\n" +
            "    componentRuntimes:\n" +
            "      type: WebAppComponentRuntime\n" +
            "      prefix: webapp_config_\n" +
            "      key: name\n" +
            "      values: [deploymentState, type, contextRoot, sourceInfo, openSessionsHighCount, openSessionsCurrentCount, sessionsOpenedTotalCount]\n" +
            "      servlets:\n" +
            "        prefix: weblogic_servlet_\n" +
            "        key: servletName\n" +
            "        values: [invocationTotalCount, executionTimeTotal]\n";

    private static final String MERGED_CONFIG =
            "metricsNameSnakeCase: true\n" +
            "queries:\n" +
            "- applicationRuntimes:\n" +
            "    key: name\n" +
            "    workManagerRuntimes:\n" +
            "      prefix: workmanager_\n" +
            "      key: applicationName\n" +
            "      values: [pendingRequests, completedRequests, stuckThreadCount]\n" +
            "    componentRuntimes:\n" +
            "      type: WebAppComponentRuntime\n" +
            "      prefix: webapp_config_\n" +
            "      key: name\n" +
            "      values: [deploymentState, type, contextRoot, sourceInfo, openSessionsHighCount, openSessionsCurrentCount, sessionsOpenedTotalCount]\n" +
            "      servlets:\n" +
            "        prefix: weblogic_servlet_\n" +
            "        key: servletName\n" +
            "        values: [invocationTotalCount, executionTimeTotal]\n";

    @Test
    public void afterAppendWithNewTopLevelQuery_configHasMultipleTopLevelQueries() throws Exception {
        ExporterConfig config = getAppendedConfiguration(SERVLET_CONFIG, PARTITION_CONFIG);

        assertThat(config.getQueries(), arrayWithSize(2));
    }

    private static final String PARTITION_CONFIG =
            "queries:\n" +
            "- partitionRuntimes:\n" +
            "    key: name\n" +
            "    keyName: partition\n" +
            "    applicationRuntimes:\n" +
            "      key: name\n" +
            "      workManagerRuntimes:\n" +
            "        prefix: workmanager_\n" +
            "        key: applicationName\n" +
            "        values: [pendingRequests, completedRequests, stuckThreadCount]\n";


    @Test
    public void afterAppendWithMatchingTopLevelQuery_configHasMergedQueries() throws Exception {
        ExporterConfig config = getAppendedConfiguration(SERVLET_CONFIG, WORK_MANAGER_CONFIG);

        assertThat(config.getQueries(), arrayWithSize(1));
    }

    @Test
    public void whenConfigHasSingleValue_displayAsScalar() throws Exception {
        ExporterConfig exporterConfig = loadFromString(CONFIG_WITH_SINGLE_VALUE);

        assertThat(exporterConfig.toString(), equalTo(CONFIG_WITH_SINGLE_VALUE));
    }


    private static final String CONFIG_WITH_SINGLE_VALUE =
            "queries:\n" +
            "- JVMRuntime:\n" +
            "    key: name\n" +
            "    values: heapFreeCurrent\n";

    @Test(expected = ConfigurationException.class)
    public void whenConfigHasDuplicateValues_reportFailure() throws Exception {
        loadFromString(CONFIG_WITH_DUPLICATE_VALUE);
    }

    private static final String CONFIG_WITH_DUPLICATE_VALUE =
            "queries:\n" +
            "- applicationRuntimes:\n" +
            "    key: name\n" +
            "    workManagerRuntimes:\n" +
            "      prefix: workmanager_\n" +
            "      key: applicationName\n" +
            "      values: [heapFreeCurrent,heapFreeCurrent]\n";

    @Test(expected = ConfigurationException.class)
    public void whenConfigHasNoValues_reportFailure() throws Exception {
        loadFromString(CONFIG_WITH_NO_VALUES);
    }

    private static final String CONFIG_WITH_NO_VALUES =
            "queries:\n" +
            "- JVMRuntime:\n" +
            "    key: name\n" +
            "    values: []\n";


    static class QueryHierarchyMatcher extends TypeSafeDiagnosingMatcher<ExporterConfig> {
        private String[] selectorKeys;

        private QueryHierarchyMatcher(String[] selectorKeys) {
            this.selectorKeys = selectorKeys;
        }

        static QueryHierarchyMatcher hasQueryFor(String... selectorKeys) {
            return new QueryHierarchyMatcher(selectorKeys);
        }

        @Override
        protected boolean matchesSafely(ExporterConfig config, Description description) {
            for (MBeanSelector mBeanSelector : config.getQueries())
                if (matchesQuery(mBeanSelector, selectorKeys)) return true;

            reportMismatch(config, description);
            return false;
        }

        private boolean matchesQuery(MBeanSelector mBeanSelector, String[] selectorKeys) {
            MBeanSelector selector = mBeanSelector;
            for (String selectorKey : selectorKeys) {
                MBeanSelector nestedSelector = selector.getNestedSelectors().get(selectorKey);
                if (nestedSelector == null) return false;
                selector = nestedSelector;
            }
            return true;
        }

        private void reportMismatch(ExporterConfig config, Description description) {
            description.appendText("configuration has queries:\n ").appendText(config.toQueryString());
        }

        @Override
        public void describeTo(Description description) {
            description.appendValueList("query for [", ", ", "]", selectorKeys);
        }
    }


}
