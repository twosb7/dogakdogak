# Ranking Sync Dedup Design

## Summary

Reduce redundant ranking sync traffic by skipping outbound sync RPC calls when the current local values are identical to the last successfully synced values.

## Problem

- Login-time sync, ranking-screen sync, and hourly background sync can all send the same daily score/touch payload repeatedly.
- After removing the ranking-tab visit gate, repeated unchanged sync payloads become more frequent.
- The current implementation has no persisted "last successful sync snapshot" check.

## Desired Behavior

- Apply deduplication to all ranking sync entry points, not just the background worker.
- Skip sync when the current payload exactly matches the last successfully synced payload for the same user and sync type.
- Only store the new snapshot after the RPC succeeds.
- Failed RPC calls must not update the snapshot so the next attempt can retry.

## Recommended Approach

Keep deduplication inside `RankingRepository`.

- Inject a `SharedPreferences` store into `RankingRepository` for persisted sync snapshots.
- Persist one snapshot per user and sync type:
  - daily score
  - daily touch
  - app daily score
  - app daily touch
- Canonicalize app maps before storing so ordering differences do not cause false syncs.
- Continue skipping empty app maps.

## Scope

In scope:

- `RankingRepository` sync methods
- persisted snapshot keys
- repository unit tests for skip/store/retry rules

Out of scope:

- changing ranking fetch APIs
- changing WorkManager cadence
- changing local counter accumulation

## Verification

- first sync with new payload calls RPC
- second sync with identical payload skips RPC
- changed payload calls RPC again
- failed RPC does not store the snapshot
