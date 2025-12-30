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

  Scenario Outline: Basic Set and Get Operations
    Given I set a value "<value>" for key "<key>"
    Then the value at key "<key>" should be "<value>" with version <version>
    Examples:
      | description                              | key | value   | version |
      | set a key for the first time             | key | value 1 | 0       |

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

  Scenario Outline: Error Handling and Validation
  Documentation: KeyValueStore provides proper error handling for version conflicts and invalid operations
    Given I set a value "<initial_value>" for key "<key>"
    When I set a value "<new_value>" for key "<key>" with previous version <prev_version>
    Then the operation should fail with ConcurrentUpdateException

    Examples:
      | description      | key            | initial_value | new_value | prev_version |
      | Version conflict | concurrent-key | Value 1       | Value 2   | 999          |
      | Stale version    | version-key    | Initial       | Updated   | 5            |

  Scenario: Removing a Key
  Documentation: KeyValueStore supports removing keys from the store
    Given I set a value "To Be Removed" for key "removable-key"
    When I remove the key "removable-key"
    Then getting the value for key "removable-key" should return null
    And the total key count should decrease by 1

  Scenario: Version Increment on Updates
  Documentation: Each update operation increments the version number automatically
    Given I set a value "V0" for key "version-tracker"
    When I update the value to "V1" for key "version-tracker"
    Then the value should have version 1
    When I update the value to "V2" for key "version-tracker"
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
      | description   | key        | value                    |
      | Simple string | string-key | Simple String            |
      | JSON object   | json-key   | {"name":"test","id":123} |
      | Numeric value | number-key | 12345                    |
      | Empty string  | empty-key  |                          |

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

