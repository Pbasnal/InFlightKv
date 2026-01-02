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
  -d '{"name": "John"}'
```

## Data Types and JSON Formats

### Objects
```bash
# Store a JSON object
curl -X PUT http://localhost:8080/kv/user \
  -H "Content-Type: application/json" \
  -d '{"name": "John", "age": 30, "city": "New York"}'
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
  -d '{"newField": "new value"}'

# Response: 409 Conflict
```

## Why JSON Only?

The InFlight KV Store currently only supports JSON but other data formats will be part of the future plan

## Best Practices

1. **Always validate JSON** before sending requests
2. **Use proper Content-Type headers** for clarity
3. **Handle version conflicts** gracefully in your application
4. **URL encode keys** that contain special characters
5. **Test with GET requests** to verify your data was stored correctly
