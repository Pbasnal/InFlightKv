# API Usage Guide

## Overview

This guide explains how to properly use the InFlight Key-Value Store API, including the required JSON format for values and common usage patterns.

## Key Requirements

### Value Format: JSON Only

**Important**: The API only accepts JSON-formatted strings as values. Plain text strings like `"Hello World"` will not work. You must send properly formatted JSON strings.

### Examples

#### ❌ Incorrect Usage
```bash
# This will FAIL - plain string is not valid JSON
curl -X PUT http://localhost:8080/kv/greeting \
  -H "Content-Type: application/json" \
  -d "Hello World"
```

#### ✅ Correct Usage
```bash
# This will WORK - properly formatted JSON string
curl -X PUT http://localhost:8080/kv/greeting \
  -H "Content-Type: application/json" \
  -d '"Hello World"'
```

## Data Types and JSON Formats

### Strings
```bash
# Store a string value
curl -X PUT http://localhost:8080/kv/message \
  -H "Content-Type: application/json" \
  -d '"This is a string value"'
```

### Numbers
```bash
# Store a number
curl -X PUT http://localhost:8080/kv/count \
  -H "Content-Type: application/json" \
  -d '42'
```

### Objects
```bash
# Store a JSON object
curl -X PUT http://localhost:8080/kv/user \
  -H "Content-Type: application/json" \
  -d '{"name": "John", "age": 30, "city": "New York"}'
```

### Arrays
```bash
# Store a JSON array
curl -X PUT http://localhost:8080/kv/tags \
  -H "Content-Type: application/json" \
  -d '["javascript", "react", "nodejs"]'
```

### Booleans
```bash
# Store a boolean value
curl -X PUT http://localhost:8080/kv/active \
  -H "Content-Type: application/json" \
  -d 'true'
```

## API Endpoints

### Store a Value
```bash
PUT /kv/{key}
Content-Type: application/json

# Body must be valid JSON
"value"
```

### Retrieve a Value
```bash
GET /kv/{key}
```

### Update with Version Check
```bash
PUT /kv/{key}?ifVersion={version}
Content-Type: application/json

# Only updates if current version matches ifVersion
"value"
```

### Partial Update (Merge)
```bash
PATCH /kv/{key}
Content-Type: application/json

# Merges with existing object, creates if doesn't exist
{"field": "newValue"}
```

### Delete a Value
```bash
DELETE /kv/{key}
```

### Get All Keys
```bash
GET /kv
```

## Common Error Scenarios

### Invalid JSON Format
```bash
# Request
curl -X PUT http://localhost:8080/kv/test \
  -H "Content-Type: application/json" \
  -d "not valid json"

# Response: 400 Bad Request
{
  "error": {
    "errorCode": "WRONG_DATA_TYPE",
    "message": "Value is not a proper json string"
  }
}
```

### Version Conflict
```bash
# Request (assuming current version is not 5)
curl -X PUT "http://localhost:8080/kv/test?ifVersion=5" \
  -H "Content-Type: application/json" \
  -d '"new value"'

# Response: 409 Conflict
```

## Why JSON Only?

The InFlight KV Store uses JSON as its native data format for several reasons:

1. **Type Safety**: JSON provides structured data types (strings, numbers, objects, arrays, booleans)
2. **Interoperability**: JSON is universally supported across programming languages
3. **Schema Flexibility**: JSON allows complex nested structures without predefined schemas
4. **Merge Operations**: JSON enables partial updates and merging of objects
5. **Validation**: JSON parsing provides built-in validation of data structure

## Content-Type Header

Always include the `Content-Type: application/json` header when sending data to the API, even though the controller accepts `Mono<String>` - this is for semantic correctness and future compatibility.

## Troubleshooting

### "Value is not a proper json string" Error
- Ensure your value is valid JSON
- Strings must be quoted: `"Hello World"` not `Hello World`
- Objects and arrays must have proper JSON syntax

### "Expected version doesn't match latest version" Error
- Use GET to retrieve current version first
- Include the correct `ifVersion` parameter in your update request

### Key Not Found
- Keys are case-sensitive
- Use GET /kv to see all available keys
- Check for URL encoding issues with special characters in keys

## Examples in Different Languages

### JavaScript/Node.js
```javascript
const response = await fetch('http://localhost:8080/kv/greeting', {
  method: 'PUT',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify("Hello World")  // Note: JSON.stringify adds quotes
});
```

### Python
```python
import requests
import json

response = requests.put(
    'http://localhost:8080/kv/greeting',
    json="Hello World"  # requests automatically JSON encodes strings
)
```

### Java
```java
HttpClient client = HttpClient.newHttpClient();
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("http://localhost:8080/kv/greeting"))
    .header("Content-Type", "application/json")
    .PUT(HttpRequest.BodyPublishers.ofString("\"Hello World\""))
    .build();
```

## Best Practices

1. **Always validate JSON** before sending requests
2. **Use proper Content-Type headers** for clarity
3. **Handle version conflicts** gracefully in your application
4. **URL encode keys** that contain special characters
5. **Test with GET requests** to verify your data was stored correctly
