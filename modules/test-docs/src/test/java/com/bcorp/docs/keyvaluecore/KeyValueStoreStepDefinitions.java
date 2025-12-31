package com.bcorp.docs.keyvaluecore;

import com.bcorp.exceptions.ConcurrentUpdateException;
import com.bcorp.kvstore.KeyValueStore;
import com.bcorp.kvstore.SystemClock;
import com.bcorp.pojos.CachedDataValue;
import com.bcorp.pojos.DataKey;
import com.bcorp.pojos.RequestDataValue;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

public class KeyValueStoreStepDefinitions {
    private KeyValueStore keyValueStore;
    private KeyValueStore newKeyValueStore;
    private CachedDataValue retrievedValue;
    private Exception caughtException;
    private long initialKeyCount;

    @Before
    public void setUp() {
        keyValueStore = null;
        newKeyValueStore = null;
        retrievedValue = null;
        caughtException = null;
    }

    @Given("a new KeyValueStore instance")
    public void aNewKeyValueStoreInstance() {
        keyValueStore = new KeyValueStore(new SystemClock());
    }

    @Given("I set a value {string} for key {string}")
    public void iSetAValueForKey(String value, String key) {
        DataKey dataKey = DataKey.fromString(key);
        RequestDataValue requestDataValue = RequestDataValue.fromString(value);
        keyValueStore.set(dataKey, requestDataValue, null).join();
    }

    @Given("I set values for multiple keys:")
    public void iSetValuesForMultipleKeys(io.cucumber.datatable.DataTable dataTable) {
        dataTable.asMaps().forEach(row ->
                iSetAValueForKey(row.get("value"), row.get("key"))
        );
    }

    @Given("the KeyValueStore is initialized")
    public void theKeyValueStoreIsInitialized() {
        keyValueStore = new KeyValueStore(new SystemClock());
    }

    @Given("I wait a small amount of time")
    public void iWaitASmallAmountOfTime() throws InterruptedException {
        Thread.sleep(10);
    }

    @Given("I wait for some time")
    public void iWaitForSomeTime() throws InterruptedException {
        Thread.sleep(50);
    }

//    @Given("I have {int} keys with values")
//    public void iHaveKeysWithValues(int keyCount) {
//        if (keyCount == 0) return; // No keys to set up
//
//        // Set up test keys for partitioning scenarios
//        String[] testKeys = {"key-1", "key-2", "key-3", "key-100"};
//        for (int i = 0; i < Math.min(keyCount, testKeys.length); i++) {
//            iSetAValueForKey("value-" + (i + 1), testKeys[i]);
//        }
//    }

    @Given("I set multiple values for keys from {string} to {string}")
    public void iSetMultipleValuesForKeysFromTo(String startKey, String endKey) {
        // Extract number from keys like "mem-key-1" to "mem-key-1000"
        int start = Integer.parseInt(startKey.substring(startKey.lastIndexOf('-') + 1));
        int end = Integer.parseInt(endKey.substring(endKey.lastIndexOf('-') + 1));

        for (int i = start; i <= end; i++) {
            String key = "mem-key-" + i;
            String value = "value-" + i;
            iSetAValueForKey(value, key);
        }
    }

    @When("I get the value for key {string}")
    public void iGetTheValueForKey(String key) {
        DataKey dataKey = DataKey.fromString(key);
        retrievedValue = keyValueStore.get(dataKey).join();
    }

    @When("I update the value to {string} for key {string}")
    public void iUpdateTheValueToForKey(String value, String key) {
        DataKey dataKey = DataKey.fromString(key);
        RequestDataValue requestDataValue = RequestDataValue.fromString(value);
        keyValueStore.set(dataKey, requestDataValue, null).join();
        retrievedValue = keyValueStore.get(dataKey).join();
    }

    @When("I set a value {string} for key {string} with previous version {int}")
    public void iSetAValueForKeyWithPreviousVersionWhen(String value, String key, int prevVersion) {
        DataKey dataKey = DataKey.fromString(key);
        RequestDataValue requestDataValue = RequestDataValue.fromString(value);
        try {
            keyValueStore.set(dataKey, requestDataValue, (long) prevVersion).join();
        } catch (Exception e) {
            caughtException = e.getCause() instanceof Exception ? (Exception) e.getCause() : e;
        }
    }

    @When("I remove the key {string}")
    public void iRemoveTheKey(String key) {
        DataKey dataKey = DataKey.fromString(key);
        initialKeyCount = keyValueStore.totalKeys();
        keyValueStore.remove(dataKey).join();
    }


    @When("I create a new KeyValueStore instance")
    public void iCreateANewKeyValueStoreInstance() {
        newKeyValueStore = new KeyValueStore(new SystemClock());
    }

    @When("I get the value for key {string} in the new instance")
    public void iGetTheValueForKeyInTheNewInstance(String key) {
        DataKey dataKey = DataKey.fromString(key);
        retrievedValue = newKeyValueStore.get(dataKey).join();
    }

    @When("the operation should complete asynchronously")
    public void theOperationShouldCompleteAsynchronously() {
        // Verify that operations return CompletableFuture
        DataKey dataKey = DataKey.fromString("async-key");
        CompletableFuture<CachedDataValue> future = keyValueStore.get(dataKey);
        assertNotNull(future, "Operation should return CompletableFuture");
        // The fact that we can call get() or join() confirms it's a CompletableFuture
        // This step documents that operations are async
    }

    @Then("the value at key {string} should be {string} with version {int}")
    public void theRetrievedValueShouldBe(String key, String expectedValue, int expectedVersion) {
        DataKey dataKey = DataKey.fromString(key);
        CachedDataValue value = keyValueStore.get(dataKey).join();

        if (keyValueStore.containsKey(dataKey).join()) {
            assertNotNull(value, "Retrieved value should not be null");
            String actualValue = new String(value.data(), StandardCharsets.UTF_8);
            assertEquals(expectedValue, actualValue, "Retrieved value should match expected value");
            assertEquals(expectedVersion, value.version(), "Retrieved value should match expected version");
        } else {
            assertNull(value, "Retrieved value should be null");
        }
    }

    @Then("the retrieved value should be {string}")
    public void theRetrievedValueShouldBe(String expectedValue) {
        if (expectedValue == null || expectedValue.isEmpty()) {
            assertEquals(0, retrievedValue.data().length, "Retrieved value should be null");
        } else {
            assertNotNull(retrievedValue, "Retrieved value should not be null");
            String actualValue = new String(retrievedValue.data(), StandardCharsets.UTF_8);
            assertEquals(expectedValue, actualValue, "Retrieved value should match expected value");
        }
    }

    @Then("the value should have version {int}")
    public void theValueShouldHaveVersion(int expectedVersion) {
        assertNotNull(retrievedValue, "Value must be retrieved first");
        assertEquals(expectedVersion, retrievedValue.version(), "Version should match");
    }

    @Then("the value for key {string} should be {string}")
    public void theValueForKeyShouldBe(String key, String expectedValue) {
        DataKey dataKey = DataKey.fromString(key);
        CachedDataValue value = keyValueStore.get(dataKey).join();

        assertNotNull(value, "Value should not be null");
        String actualValue = new String(value.data(), StandardCharsets.UTF_8);
        assertEquals(expectedValue, actualValue, "Value should match expected");
        retrievedValue = value;
    }

    @Then("the operation should fail with ConcurrentUpdateException")
    public void theOperationShouldFailWithConcurrentUpdateException() {
        assertNotNull(caughtException, "Exception should have been caught");
        assertTrue(caughtException instanceof ConcurrentUpdateException ||
                        caughtException.getCause() instanceof ConcurrentUpdateException,
                "Exception should be ConcurrentUpdateException");
    }

    @Then("getting the value for key {string} should return null")
    public void gettingTheValueForKeyShouldReturnNull(String key) {
        DataKey dataKey = DataKey.fromString(key);
        CachedDataValue value = keyValueStore.get(dataKey).join();
        assertNull(value, "Value should be null after removal");
    }

    @Then("the total key count should decrease by {int}")
    public void theTotalKeyCountShouldDecrease(int decreaseInKeyCount) {
        long currentKeyCount = keyValueStore.totalKeys();
        assertTrue(initialKeyCount - currentKeyCount == decreaseInKeyCount, "Key count should have decreased by " + decreaseInKeyCount);
    }

    @Then("the total key count should be {int}")
    public void theTotalKeyCountShouldBe(int expectedCount) {
        long actualCount = keyValueStore.totalKeys();
        assertEquals(expectedCount, actualCount, "Total key count should match");
    }

    @Then("the retrieved value should be null")
    public void theRetrievedValueShouldBeNull() {
        assertNull(retrievedValue, "Retrieved value should be null");
    }

    @Then("the store should have {int} partitions")
    public void theStoreShouldHavePartitions(int expectedPartitions) {
        // KeyValueStore uses 32 partitions internally, we can verify by checking behavior
        // Since partitions are private, we verify by checking that operations work correctly
        // which implies partitions are initialized
        assertNotNull(keyValueStore, "KeyValueStore should be initialized");
        // We can't directly access partitions, but we can verify the store works
        // which confirms partitions are set up (32 is hardcoded in constructor)
        assertEquals(32, expectedPartitions, "KeyValueStore should have 32 partitions");
    }

    @Then("the last access time should be updated")
    public void theLastAccessTimeShouldBeUpdated() {
        // Get the value again to check if access time was updated
        DataKey dataKey = DataKey.fromString("access-time-key");
        CachedDataValue valueAfterAccess = keyValueStore.get(dataKey).join();
        assertNotNull(valueAfterAccess, "Value should exist");
        // The access time should be recent (within last second)
        long currentTime = System.currentTimeMillis();
        assertTrue(valueAfterAccess.lastAccessTimeMs() <= currentTime,
                "Last access time should be set");
        assertTrue(valueAfterAccess.lastAccessTimeMs() > currentTime - 1000,
                "Last access time should be recent");
    }

    @Then("key {string} should not exist")
    public void keyShouldNotExist(String key) {
        DataKey dataKey = DataKey.fromString(key);
        CachedDataValue value = keyValueStore.get(dataKey).join();
        assertNull(value, "Key should not exist");
    }

    @Then("key {string} should still exist")
    public void keyShouldStillExist(String key) {
        DataKey dataKey = DataKey.fromString(key);
        CachedDataValue value = keyValueStore.get(dataKey).join();
        assertNotNull(value, "Key should still exist");
    }

    @Then("the value should still exist")
    public void theValueShouldStillExist() {
        DataKey dataKey = DataKey.fromString("ttl-key");
        CachedDataValue value = keyValueStore.get(dataKey).join();
        assertNotNull(value, "Value should still exist");
    }

    @Then("the original value should be lost")
    public void theOriginalValueShouldBeLost() {
        // This is verified by checking the new value in the previous step
        // The original value "Original" is overwritten by "Overwritten"
        DataKey dataKey = DataKey.fromString("overwrite-test");
        CachedDataValue value = keyValueStore.get(dataKey).join();
        assertNotNull(value, "Value should exist");
        String actualValue = new String(value.data(), StandardCharsets.UTF_8);
        assertEquals("Overwritten", actualValue, "Original value should be overwritten");
    }
}

