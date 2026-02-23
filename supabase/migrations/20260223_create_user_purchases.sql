-- user_purchases: Supabase 계정별 인앱구매 기록 (교차 기기 복원용)
CREATE TABLE user_purchases (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    product_id TEXT NOT NULL,
    verified BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(user_id, product_id)
);

ALTER TABLE user_purchases ENABLE ROW LEVEL SECURITY;

CREATE POLICY "read_own" ON user_purchases FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY "insert_own" ON user_purchases FOR INSERT
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "update_own" ON user_purchases FOR UPDATE
    USING (auth.uid() = user_id);

CREATE INDEX idx_user_purchases_user ON user_purchases(user_id);

-- purchase_logs에 user_id 컬럼 추가 (nullable, 기존 데이터 보존)
ALTER TABLE purchase_logs ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL;
