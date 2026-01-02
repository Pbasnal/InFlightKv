# Design Documentation

This directory contains design documentation for the InFlight Key-Value Store system.

## Files

- `kv-store-design.md` - Comprehensive design documentation covering the KeyValueStore and KeyValuePartition classes, including alternative implementation approaches and architectural decisions.
- `event-loop-vs-threadpool.md` - Detailed explanation of event loops vs thread pools, comparing true event loop implementations with the current single-threaded executor approach.
- `api-usage-guide.md` - Practical guide for using the Key-Value Store API, including JSON format requirements and common usage patterns.
- `future-roadmap.md` - Planned features and enhancements roadmap, including persistence, TTL support, and Pub/Sub capabilities.

## Overview

The documentation provides:
- Detailed analysis of the current partitioned, event-loop based architecture
- Thread safety and concurrency control mechanisms
- Alternative approaches with pros/cons analysis
- Justification for the chosen implementation strategy

## Key Components Documented

- **KeyValueStore**: Main entry point with 32-partition architecture
- **KeyValuePartition**: Single-threaded event loop implementation for thread safety
- **Concurrency Control**: Version-based optimistic locking
- **Performance Characteristics**: Partitioning benefits and tradeoffs
