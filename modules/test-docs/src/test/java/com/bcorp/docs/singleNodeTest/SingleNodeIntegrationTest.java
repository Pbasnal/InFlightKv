package com.bcorp.docs.singleNodeTest;

import io.cucumber.junit.platform.engine.Constants;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/singleNodeTest")
@ConfigurationParameter(key = Constants.PLUGIN_PROPERTY_NAME, value = "pretty, html:target/cucumber-reports-single-node.html,json:target/cucumber-reports-single-node.json")
@ConfigurationParameter(key = Constants.GLUE_PROPERTY_NAME, value = "com.bcorp.docs.singleNodeTest")
public class SingleNodeIntegrationTest {
    // This class serves as the test runner for single-node integration tests
    // It uses JUnit Platform Suite to execute Cucumber scenarios
    // Assumes the InFlightKv API is running before tests are executed
}
