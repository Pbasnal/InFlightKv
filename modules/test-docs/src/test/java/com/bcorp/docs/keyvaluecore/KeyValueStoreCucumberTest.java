package com.bcorp.docs.keyvaluecore;

import io.cucumber.junit.platform.engine.Constants;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = Constants.PLUGIN_PROPERTY_NAME, value = "pretty, html:target/cucumber-reports")
@ConfigurationParameter(key = Constants.GLUE_PROPERTY_NAME, value = "com.bcorp.docs.keyvaluecore")
public class KeyValueStoreCucumberTest {
    // This class serves as the test runner for Cucumber tests
    // It uses JUnit Platform Suite to execute Cucumber scenarios
}

