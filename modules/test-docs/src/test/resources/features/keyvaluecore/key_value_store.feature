Feature: KeyValueStore Usage and Limitations
  As a developer
  I want to understand how to use KeyValueStore and its limitations
  So that I can use it effectively in my application

  Background: Data Types and Internal Representation
    Documentation: KeyValueStore operates on well-defined data types for consistent storage and retrieval
    The KeyValueStore uses two primary data structures:
    - DataKey: Represents keys using String type (current implementation)
    - DataValue: Represents values using byte[] array for flexible data storage
    These types ensure type safety and consistent behavior across all operations.
    Values are converted to byte[] arrays for storage, allowing any serializable data type.
    Keys remain as strings in DataKey format for consistent key management.

  Background:
    Given a new KeyValueStore instance

  Scenario: Basic Set and Get Operations
    Given I set a value "Hello World" for key "test-key"
    When I get the value for key "test-key"
    Then the retrieved value should be "Hello World"
    And the value should have version 0

  Scenario: Setting a Value Without Previous Version
    Documentation: When setting a value with null previous version, it performs an insert or update
    Given I set a value "First Value" for key "version-test"
    When I set a value "Second Value" for key "version-test"
    Then the value for key "version-test" should be "Second Value"
    And the value should have version 1

  Scenario: Optimistic Locking with Version Control
    Documentation: KeyValueStore supports optimistic locking using version numbers
    Given I set a value "Initial Value" for key "versioned-key"
    When I set a value "Updated Value" for key "versioned-key" with previous version 0
    Then the value for key "versioned-key" should be "Updated Value"
    And the value should have version 1

  Scenario: Concurrent Update Detection
    Documentation: KeyValueStore throws ConcurrentUpdateException when version mismatch occurs
    Given I set a value "Value 1" for key "concurrent-key"
    When I set a value "Value 2" for key "concurrent-key" with previous version 999
    Then the operation should fail with ConcurrentUpdateException

  Scenario: Removing a Key
    Documentation: KeyValueStore supports removing keys from the store
    Given I set a value "To Be Removed" for key "removable-key"
    When I remove the key "removable-key"
    Then getting the value for key "removable-key" should return null
    And the total key count should decrease by 1

  Scenario: Getting a Non-Existent Key
    Documentation: Getting a key that doesn't exist returns null
    When I get the value for key "non-existent-key"
    Then the retrieved value should be null

  Scenario: Multiple Keys Across Partitions
    Documentation: KeyValueStore uses 32 partitions to distribute keys
    Given I set values for multiple keys:
      | key      | value           |
      | key-1    | value-1         |
      | key-2    | value-2         |
      | key-3    | value-3         |
      | key-100  | value-100       |
    Then the total key count should be 4

  Scenario: Version Increment on Updates
    Documentation: Each update operation increments the version number
    Given I set a value "V0" for key "version-tracker"
    When I set a value "V1" for key "version-tracker"
    When I get the value for key "version-tracker"
    Then the value should have version 1
    When I set a value "V2" for key "version-tracker"
    When I get the value for key "version-tracker"
    Then the value should have version 2

  Scenario: Overwriting Existing Values
    Documentation: Setting a value with null previous version overwrites existing values
    Given I set a value "Original" for key "overwrite-test"
    When I set a value "Overwritten" for key "overwrite-test"
    Then the value for key "overwrite-test" should be "Overwritten"
    And the original value should be lost

  Scenario: Last Access Time Update
    Documentation: KeyValueStore updates last access time when getting a value
    Given I set a value "Access Test" for key "access-time-key"
    And I wait a small amount of time
    When I get the value for key "access-time-key"
    Then the last access time should be updated

  Scenario Outline: Different Data Types
    Documentation: KeyValueStore can store any data as byte arrays through DataValue
    All values are converted to byte[] arrays for storage, allowing flexible data types
    Keys remain as strings in DataKey format for consistent key management
    Given I set a value '<value>' for key '<key>'
    When I get the value for key '<key>'
    Then the retrieved value should be '<value>'
    Examples:
      | key           | value                    |
      | string-key    | Simple String            |
      | json-key      | {"name":"test","id":123} |
      | number-key    | 12345                    |
      | empty-key     |                          |

  Scenario: Limitations - No Persistence
    Documentation: KeyValueStore is in-memory only and does not persist data. Data is lost when the store instance is destroyed.
    DataKey and DataValue objects exist only in memory and are not serialized to disk.
    Given I set a value "Persistent Test" for key "persist-key"
    When I create a new KeyValueStore instance
    And I get the value for key "persist-key" in the new instance
    Then the retrieved value should be null

  Scenario: Limitations - Fixed Partition Count
    Documentation: KeyValueStore uses a fixed number of 32 partitions. Partition count cannot be configured and is fixed at 32.
    Given the KeyValueStore is initialized
    Then the store should have 32 partitions

  Scenario: Limitations - Asynchronous Operations
    Documentation: All operations return CompletableFuture and are asynchronous. All operations must be awaited using join() or get() on CompletableFuture.
    Given I set a value "Async Test" for key "async-key"
    When I get the value for key "async-key"
    Then the operation should complete asynchronously

  Scenario: Limitations - No Transaction Support
    Documentation: KeyValueStore does not support transactions. Operations are independent and cannot be rolled back.
    Given I set a value "Tx1" for key "tx-key-1"
    And I set a value "Tx2" for key "tx-key-2"
    When I remove the key "tx-key-1"
    Then key "tx-key-1" should not exist
    And key "tx-key-2" should still exist

  Scenario: Limitations - No Expiration or TTL
    Documentation: KeyValueStore does not support automatic expiration of keys. Keys must be manually removed; no automatic expiration.
    Given I set a value "TTL Test" for key "ttl-key"
    When I wait for some time
    And I get the value for key "ttl-key"
    Then the value should still exist

  Scenario: Limitations - Memory Only Storage
    Documentation: KeyValueStore stores all data in memory as DataKey-DataValue pairs. All data is stored in memory; large datasets may cause memory issues.
    Each DataValue contains byte[] data plus metadata (dataType, lastAccessTimeMs, version), consuming additional memory beyond the raw data.
    Given I set multiple values for keys from "mem-key-1" to "mem-key-1000"
    Then the total key count should be 1000

