# Counter Load Test

This document describes the counter load test functionality in the InFlightKv load test application.

## Overview

The counter load test focuses on testing concurrent updates to a single shared counter key. This is a stress test for version conflict handling and optimistic concurrency control in the key-value store.

## How It Works

1. **Initialization**: A counter key `"shared-counter"` is initialized with value `{"count": 0}`
2. **Concurrent Updates**: Multiple threads simultaneously try to increment the counter
3. **Version Conflicts**: When conflicts occur (HTTP 409), threads automatically retry with exponential backoff
4. **Atomic Increments**: Each successful update increments the counter by exactly 1

## Key Features

- **Single Shared Key**: All threads update the same counter key
- **Version Conflict Handling**: Automatic retry on 409 Conflict responses
- **Retry Limits**: Maximum 10 retries per operation to prevent infinite loops
- **Metrics Tracking**: Comprehensive statistics on conflicts, retries, and success rates

## Usage

```bash
# Basic counter test
java -jar load-test.jar --counter

# Counter test with custom parameters
java -jar load-test.jar --counter -t 10 -r 50 -h localhost -p 8080
```

## Parameters

- `-t, --threads`: Number of concurrent threads (default: 3)
- `-r, --requests`: Number of increments per thread (default: 100)
- `-h, --host`: Target server host (default: localhost)
- `-p, --port`: Target server port (default: 8080)
- `-d, --duration`: Maximum test duration in seconds (default: 300)

## Expected Results

For a properly implemented optimistic concurrency system:

- **Total Successful Updates**: Should equal `threads × requests_per_thread`
- **Final Counter Value**: Should equal the total successful updates
- **Conflict Retries**: Some conflicts are expected and normal
- **Max Retries Hit**: Should be minimal (indicates system performance issues)

## Example Output

```
=== Counter Load Test Results ===
Total Operations: 1500
Successful Updates: 300
Conflict Retries: 1200
Max Retries Hit: 0
Success Rate: 20.00%
Total Duration: 5423ms
Operations/Second: 276.61
Final Counter Value: 300
Average Retries per Update: 4.00
=================================
```

## Performance Expectations

- **High Contention**: With many threads, expect significant retries
- **Low Contention**: With few threads, expect minimal conflicts
- **System Load**: Retries indicate system is handling concurrent updates correctly

## Troubleshooting

### Too Many Max Retries Hit
- **Cause**: System cannot handle the concurrency level
- **Solution**: Reduce thread count or improve system performance

### Final Counter Value Incorrect
- **Cause**: Lost updates due to version conflicts not being handled properly
- **Solution**: Check the key-value store's optimistic concurrency implementation

### Zero Successful Updates
- **Cause**: Connection issues or API incompatibility
- **Solution**: Verify server is running and API endpoints are correct
