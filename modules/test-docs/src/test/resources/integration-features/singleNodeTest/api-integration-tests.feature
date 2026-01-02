Feature: InFlightKv Single Node API Integration
  As a developer integrating with InFlightKv
  I want to understand all the happy path operations available
  So that I can effectively use the key-value store API

  Background: API Availability
    Given the InFlightKv API is running on "http://localhost:8080"

  Scenario: Storing and Retrieving JSON Objects
    Documentation: InFlightKv can store any valid JSON data as string values
    And the key "user-profile" does not exist
    When I store the JSON value '{"name": "John", "age": 30, "city": "New York"}' with key "user-profile"
    Then I should be able to retrieve the exact JSON '{"name": "John", "age": 30, "city": "New York"}' for key "user-profile"

  Scenario: Storing and Retrieving Complex JSON Arrays
    Documentation: Complex JSON structures including arrays are fully supported
    And the key "shopping-cart" does not exist
    When I store the JSON value '[{"id": 1, "item": "book"}, {"id": 2, "item": "pen"}]' with key "shopping-cart"
    Then I should be able to retrieve the exact JSON '[{"id": 1, "item": "book"}, {"id": 2, "item": "pen"}]' for key "shopping-cart"

  Scenario: Version Control - First Time Storage
    Documentation: When storing a value for the first time, it gets version 0
    And the key "version-test" does not exist
    When I store the value '{"name":"John"}' with key "version-test"
    Then I should be able to retrieve '{"name":"John"}' for key "version-test"
    And the version should be 0

  Scenario: Version Control - Updates Increment Version
    Documentation: Each update operation automatically increments the version number
    And the key "update-test" does not exist
    Given I have stored '{"name":"John"}' with key "update-test"
    When I update the value to '{"name":"Ryan"}' for key "update-test"
    Then I should be able to retrieve '{"name":"Ryan"}' for key "update-test"
    And the version should be 1

  Scenario: Optimistic Locking with Version Validation
    Documentation: Use version numbers to ensure you're updating the expected version
    And the key "optimistic-test" does not exist
    Given I have stored '{"name":"John"}' with key "optimistic-test"
    And the current version is 0
    When I update the value to '{"name":"Ryan"}' for key "optimistic-test" with expected version 0
    Then I should be able to retrieve '{"name":"Ryan"}' for key "optimistic-test"
    And the version should be 1

  Scenario: Partial Updates with PATCH
    Documentation: PATCH operations allow updating values with version control, similar to PUT but semantically for partial updates
    And the key "patch-test" does not exist
    Given I have stored '{"name":"John"}' with key "patch-test"
    And the current version is 0
    When I patch the value to '{"surname":"Wheeler"}' for key "patch-test" with expected version 0
    Then I should be able to retrieve '{"name":"John","surname":"Wheeler"}' for key "patch-test"
    And the version should be 1

  Scenario: Overwriting Values Without Version Check
    Documentation: PUT operations without version parameter will overwrite existing values regardless of current version
    And the key "overwrite-test" does not exist
    Given I have stored '{"name":"John"}' with key "overwrite-test"
    And the current version is 0
    When I store the value '{"name":"Ryan"}' with key "overwrite-test" without version check
    Then I should be able to retrieve '{"name":"Ryan"}' for key "overwrite-test"
    And the version should be 1

  Scenario: Deleting Existing Keys
    Documentation: Keys can be completely removed from the store
    And the key "delete-test" does not exist
    Given I have stored '{"name":"John"}' with key "delete-test"
    When I delete the key "delete-test"
    Then attempting to retrieve key "delete-test" should return not found

  Scenario: Retrieving Non-Existent Keys
    Documentation: Requesting a key that doesn't exist returns a 404 status
    When I attempt to retrieve a non-existent key "missing-key"
    Then the response should indicate the key was not found

  Scenario: Listing All Keys in Single Node
    Documentation: GET /kv without parameters returns all keys in NDJSON format for single node deployments
    And the key "list-key-1" does not exist
    And the key "list-key-2" does not exist
    And the key "list-key-3" does not exist
    Given I have stored '{"name":"John"}' with key "list-key-1"
    And I have stored '{"name":"Ryan"}' with key "list-key-2"
    And I have stored '{"name":"Kali"}' with key "list-key-3"
    When I request all keys from the store
    Then I should receive a list containing keys "list-key-1", "list-key-2", and "list-key-3"
    And the response should be in NDJSON format
    And each key should be associated with the current node

  Scenario: Storing Large JSON Documents
    Documentation: The API supports storing reasonably large JSON documents
    And the key "large-doc" does not exist
    When I store a large JSON document with key "large-doc"
    """
    {
      "metadata": {
        "version": "1.0",
        "created": "2024-01-01T00:00:00Z",
        "tags": ["test", "large", "document"]
      },
      "content": {
        "title": "Large Document Test",
        "description": "This is a test document with multiple nested objects and arrays",
        "sections": [
          {
            "id": 1,
            "title": "Introduction",
            "content": "This section introduces the document"
          },
          {
            "id": 2,
            "title": "Body",
            "content": "This is the main content section with more detailed information"
          }
        ],
        "references": [
          {"type": "url", "value": "https://example.com"},
          {"type": "doi", "value": "10.1234/example"}
        ]
      }
    }
    """
    Then I should be able to retrieve the exact large JSON document for key "large-doc"

