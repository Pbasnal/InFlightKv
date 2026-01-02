# InFlightKv Single Node Integration Tests

This directory contains Cucumber integration tests for the InFlightKv Spring Web API in single-node configuration.

## Prerequisites

Before running these tests, ensure that:
1. The InFlightKv Spring Web application is running
2. The application is configured for single-node mode (using `application-single-node.yaml`)
3. The application is accessible at `http://localhost:8080`

## Starting the Application

To start the application in single-node mode:

```bash
cd modules/spring-web
../../gradlew bootRun --args='--spring.profiles.active=single-node'
```

Or using the single-node configuration file:

```bash
cd modules/spring-web
../../gradlew bootRun --args='--spring.config.location=classpath:application-single-node.yaml'
```

The application will start on port 8080 by default.

## Running the Integration Tests

Once the application is running, execute the integration tests:

```bash
cd modules/test-docs
../../gradlew test --tests "*SingleNodeIntegrationTest*"
```

## Test Structure

The tests are organized as follows:

- `single_node_integration.feature`: Main feature file containing all happy path scenarios
- `SingleNodeIntegrationSteps.java`: Step definitions implementing the test logic using REST-assured
- `SingleNodeIntegrationTest.java`: JUnit test runner configuration

## API Behavior Notes

The InFlightKv API expects all values to be sent as JSON strings in the request body:

- Simple strings like "Hello World" are sent as `"\"Hello World\""`
- JSON objects are sent as their string representation
- All PUT/PATCH operations require valid JSON in the request body

## Test Scenarios

The integration tests cover the following happy path scenarios:

1. **Basic Key-Value Operations**: Storing and retrieving string values (as JSON strings)
2. **JSON Data Handling**: Storing and retrieving JSON objects and arrays
3. **Version Control**: Automatic version increment and optimistic locking
4. **PATCH Operations**: Partial updates with version validation
5. **Key Deletion**: Removing keys from the store
6. **Error Handling**: Proper 404 responses for non-existent keys
7. **Key Listing**: Retrieving all keys in NDJSON format
8. **Cluster Routing**: Key routing information for single-node setup
9. **Large Documents**: Handling large JSON documents
10. **Multiple Operations**: Sequential operations on different keys

## Test Documentation

Each scenario includes detailed documentation explaining:
- How the API feature works
- Expected behavior
- Response formats
- Usage patterns

These tests serve as both validation and comprehensive documentation for API consumers.

## Troubleshooting

### Common Issues:

1. **Connection Refused**: Ensure the application is running on `http://localhost:8080`
2. **Test Failures**: Verify the application logs for any startup issues
3. **JSON Parsing Errors**: Check that all values are properly formatted as JSON strings
4. **Port Conflicts**: Check if port 8080 is available or configure a different port

### Logs

Application logs can be found in the console when starting with `bootRun`. Test reports are generated in:
- `modules/test-docs/build/reports/tests/test/index.html`
- `modules/test-docs/target/cucumber-reports-single-node.html` (Cucumber reports)
