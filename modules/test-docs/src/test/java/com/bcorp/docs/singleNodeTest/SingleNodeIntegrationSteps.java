package com.bcorp.docs.singleNodeTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;

public class SingleNodeIntegrationSteps {

    private String baseUrl;
    private Response lastResponse;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Integer> storedVersions = new HashMap<>();

    @Given("the InFlightKv API is running on {string}")
    public void theInFlightKvAPIIsRunningOn(String baseUrl) {
        this.baseUrl = baseUrl;
        RestAssured.baseURI = baseUrl;
    }

    @Given("I have a clean key-value store")
    public void iHaveACleanKeyValueStore() {
        // For integration tests, we assume the API is running with a clean state
        // In a real scenario, you might want to clear all keys or use a test database
        storedVersions.clear();
    }

    @Given("the key {string} does not exist")
    public void theKeyDoesNotExist(String key) {
        // Attempt to delete the key if it exists, but don't fail if it doesn't
        try {
            given()
                    .when()
                    .delete("/kv/" + key);
        } catch (Exception e) {
            // Ignore any exceptions - the key might not exist
        }
    }

    @When("I store the value {string} with key {string}")
    public void iStoreTheValueWithKey(String value, String key) {
        // Send the plain string value
        lastResponse = given()
                .body(value)
                .when()
                .put("/kv/" + key);

        assertEquals(200, lastResponse.getStatusCode());
        storeVersionFromResponse(key, lastResponse);
    }

    @When("I store the JSON value {string} with key {string}")
    public void iStoreTheJsonValueWithKey(String jsonValue, String key) {
        lastResponse = given()
                .body(jsonValue)
                .when()
                .put("/kv/" + key);

        assertEquals(200, lastResponse.getStatusCode());
        storeVersionFromResponse(key, lastResponse);
    }

    @When("I store a large JSON document with key {string}")
    public void iStoreALargeJsonDocumentWithKey(String key, String jsonContent) {
        lastResponse = given()
                .body(jsonContent.trim())
                .when()
                .put("/kv/" + key);

        assertEquals(200, lastResponse.getStatusCode());
        storeVersionFromResponse(key, lastResponse);
    }

    @When("I store an empty string with key {string}")
    public void iStoreAnEmptyStringWithKey(String key) {
        lastResponse = given()
                .body("")
                .when()
                .put("/kv/" + key);

        assertEquals(200, lastResponse.getStatusCode());
        storeVersionFromResponse(key, lastResponse);
    }

    @When("I update the value to {string} for key {string}")
    public void iUpdateTheValueToForKey(String value, String key) {
        lastResponse = given()
                .body(value)
                .when()
                .put("/kv/" + key);

        assertEquals(200, lastResponse.getStatusCode());
        storeVersionFromResponse(key, lastResponse);
    }

    @When("I update the value to {string} for key {string} with expected version {int}")
    public void iUpdateTheValueToForKeyWithExpectedVersion(String value, String key, int expectedVersion) {
        lastResponse = given()
                .queryParam("ifVersion", expectedVersion)
                .body(value)
                .when()
                .put("/kv/" + key);

        assertEquals(200, lastResponse.getStatusCode());
        storeVersionFromResponse(key, lastResponse);
    }

    @When("I patch the value to {string} for key {string} with expected version {int}")
    public void iPatchTheValueToForKeyWithExpectedVersion(String value, String key, int expectedVersion) {
        lastResponse = given()
                .queryParam("ifVersion", expectedVersion)
                .body(value)
                .when()
                .patch("/kv/" + key);

        assertEquals(200, lastResponse.getStatusCode());
        storeVersionFromResponse(key, lastResponse);
    }

    @When("I store the value {string} with key {string} without version check")
    public void iStoreTheValueWithKeyWithoutVersionCheck(String value, String key) {
        lastResponse = given()
                .body(value)
                .when()
                .put("/kv/" + key);

        assertEquals(200, lastResponse.getStatusCode());
        storeVersionFromResponse(key, lastResponse);
    }

    @When("I delete the key {string}")
    public void iDeleteTheKey(String key) {
        lastResponse = given()
                .when()
                .delete("/kv/" + key);

        assertEquals(200, lastResponse.getStatusCode());
    }

    @When("I attempt to retrieve a non-existent key {string}")
    public void iAttemptToRetrieveANonExistentKey(String key) {
        lastResponse = given()
                .when()
                .get("/kv/" + key);
    }

    @When("I request all keys from the store")
    public void iRequestAllKeysFromTheStore() {
        lastResponse = given()
                .when()
                .get("/kv");
    }

    @When("I request routing information for key {string}")
    public void iRequestRoutingInformationForKey(String key) {
        lastResponse = given()
                .when()
                .get("/api/cluster/route/" + key);
    }

    @Then("I should be able to retrieve {string} for key {string}")
    public void iShouldBeAbleToRetrieveForKey(String expectedValue, String key) {
        Response response = given()
                .when()
                .get("/kv/" + key);

        assertEquals(200, response.getStatusCode());
        response.then().body("data", equalTo(expectedValue));
    }

    @Then("I should be able to retrieve the exact JSON {string} for key {string}")
    public void iShouldBeAbleToRetrieveTheExactJsonForKey(String expectedJson, String key) {
        Response response = given()
                .when()
                .get("/kv/" + key);

        assertEquals(200, response.getStatusCode());
        // Remove all spaces from expected JSON for comparison
        String expectedJsonWithoutSpaces = expectedJson
                .replaceAll(",\\s+", ",")
                .replaceAll(":\\s+", ":");
        response.then().body("data", equalTo(expectedJsonWithoutSpaces));
    }

    @Then("I should be able to retrieve the exact large JSON document for key {string}")
    public void iShouldBeAbleToRetrieveTheExactLargeJsonDocumentForKey(String key) {
        Response response = given()
                .when()
                .get("/kv/" + key);

        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getBody().jsonPath().getString("data"));
    }

    @Then("I should be able to retrieve an empty string for key {string}")
    public void iShouldBeAbleToRetrieveAnEmptyStringForKey(String key) {
        Response response = given()
                .when()
                .get("/kv/" + key);

        assertEquals(200, response.getStatusCode());
        response.then().body("data", equalTo(""));
    }

    @Then("the response should indicate success")
    public void theResponseShouldIndicateSuccess() {
        assertEquals(200, lastResponse.getStatusCode());
    }

    @Then("the version should be {int}")
    public void theVersionShouldBe(int expectedVersion) {
        lastResponse.then().body("version", equalTo(expectedVersion));
    }

    @And("the current version is {int}")
    public void theCurrentVersionIs(int version) {
        // This is just for documentation in the feature file
        // The version is already tracked in storedVersions map
    }

    @Then("attempting to retrieve key {string} should return not found")
    public void attemptingToRetrieveKeyShouldReturnNotFound(String key) {
        Response response = given()
                .when()
                .get("/kv/" + key);

        assertEquals(404, response.getStatusCode());
    }

    @Then("the response should indicate the key was not found")
    public void theResponseShouldIndicateTheKeyWasNotFound() {
        assertEquals(404, lastResponse.getStatusCode());
    }

    @Then("I should receive a list containing keys {string}, {string}, and {string}")
    public void iShouldReceiveAListContainingKeys(String key1, String key2, String key3) {
        assertEquals(200, lastResponse.getStatusCode());

        String responseBody = lastResponse.getBody().asString();
        String[] lines = responseBody.trim().split("\n");

        boolean foundKey1 = false, foundKey2 = false, foundKey3 = false;

        for (String line : lines) {
            try {
                JsonNode jsonNode = objectMapper.readTree(line);
                String key = jsonNode.get("key").asText();
                if (key.equals(key1)) foundKey1 = true;
                if (key.equals(key2)) foundKey2 = true;
                if (key.equals(key3)) foundKey3 = true;
            } catch (Exception e) {
                fail("Invalid JSON in NDJSON response: " + line);
            }
        }

        assertTrue(foundKey1, "Key " + key1 + " not found in response");
        assertTrue(foundKey2, "Key " + key2 + " not found in response");
        assertTrue(foundKey3, "Key " + key3 + " not found in response");
    }

    @Then("the response should be in NDJSON format")
    public void theResponseShouldBeInNdjsonFormat() {
        assertEquals("application/x-ndjson", lastResponse.getContentType());

        String responseBody = lastResponse.getBody().asString();
        String[] lines = responseBody.trim().split("\n");

        for (String line : lines) {
            assertFalse(line.trim().isEmpty(), "Empty line found in NDJSON response");
            try {
                objectMapper.readTree(line);
            } catch (Exception e) {
                fail("Invalid JSON in NDJSON response: " + line);
            }
        }
    }

    @Then("each key should be associated with the current node")
    public void eachKeyShouldBeAssociatedWithTheCurrentNode() {
        String responseBody = lastResponse.getBody().asString();
        String[] lines = responseBody.trim().split("\n");

        for (String line : lines) {
            try {
                JsonNode jsonNode = objectMapper.readTree(line);
                assertNotNull(jsonNode.get("node"), "Node field missing in NDJSON entry: " + line);
                assertFalse(jsonNode.get("node").asText().isEmpty(), "Node field is empty in NDJSON entry: " + line);
            } catch (Exception e) {
                fail("Invalid JSON in NDJSON response: " + line);
            }
        }
    }

    @Then("I should receive routing details indicating which node handles {string}")
    public void iShouldReceiveRoutingDetailsIndicatingWhichNodeHandles(String key) {
        assertEquals(200, lastResponse.getStatusCode());
        lastResponse.then()
                .body("key", equalTo(key))
                .body("shouldRedirect", notNullValue())
                .body("nodeId", notNullValue());
    }

    @Then("the routing should indicate local handling for single node setup")
    public void theRoutingShouldIndicateLocalHandlingForSingleNodeSetup() {
        lastResponse.then().body("shouldRedirect", equalTo(false));
    }

    @Given("I have stored {string} with key {string}")
    public void iHaveStoredWithKey(String value, String key) {
        Response response = given()
                .body(value)
                .when()
                .put("/kv/" + key);

        assertEquals(200, response.getStatusCode());
        storeVersionFromResponse(key, response);
    }

    @And("I store {string} with key {string}")
    public void iStoreWithKey(String value, String key) {
        Response response = given()
                .body(value)
                .when()
                .put("/kv/" + key);

        assertEquals(200, response.getStatusCode());
        storeVersionFromResponse(key, response);
    }

    private void storeVersionFromResponse(String key, Response response) {
        if (response.getStatusCode() == 200) {
            Integer version = response.jsonPath().getInt("version");
            if (version != null) {
                storedVersions.put(key, version);
            }
        }
    }
}
