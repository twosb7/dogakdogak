-- Harden purchase entitlement and token replay protection

-- Prevent clients from self-granting entitlements directly via RLS.
DROP POLICY IF EXISTS "insert_own" ON user_purchases;
DROP POLICY IF EXISTS "update_own" ON user_purchases;

-- Keep token material out of logs and enforce replay protection key.
ALTER TABLE purchase_logs
    ADD COLUMN IF NOT EXISTS purchase_token_hash TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS uq_purchase_logs_purchase_token_hash
    ON purchase_logs(purchase_token_hash)
    WHERE purchase_token_hash IS NOT NULL;
