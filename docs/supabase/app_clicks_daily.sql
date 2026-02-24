-- =============================================================
-- 앱별 Score/Touch 랭킹 스키마 및 RPC 함수
-- Supabase SQL Editor에서 실행
-- 모든 날짜 연산은 Asia/Seoul (KST) 기준
-- =============================================================

-- 1. 앱별 일별 클릭/터치 기록 테이블
CREATE TABLE IF NOT EXISTS app_clicks_daily (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    package_name TEXT NOT NULL,
    click_date DATE NOT NULL DEFAULT (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date,
    click_count BIGINT NOT NULL DEFAULT 0,
    touch_count BIGINT NOT NULL DEFAULT 0,
    UNIQUE(user_id, package_name, click_date)
);

-- 인덱스: 랭킹 쿼리 최적화
CREATE INDEX IF NOT EXISTS idx_app_clicks_daily_pkg_date
    ON app_clicks_daily (package_name, click_date);
CREATE INDEX IF NOT EXISTS idx_app_clicks_daily_user
    ON app_clicks_daily (user_id);

-- RLS 활성화
ALTER TABLE app_clicks_daily ENABLE ROW LEVEL SECURITY;

-- RLS 정책: 자신의 데이터만 CRUD 가능
CREATE POLICY "Users can manage their own app clicks"
    ON app_clicks_daily
    FOR ALL
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- 읽기는 모든 인증 유저에게 허용 (랭킹 조회용)
CREATE POLICY "Authenticated users can read all app clicks"
    ON app_clicks_daily
    FOR SELECT
    USING (auth.role() = 'authenticated');

-- =============================================================
-- 2. 앱별 일별 Score 동기화 (배치 upsert)
-- =============================================================
CREATE OR REPLACE FUNCTION upsert_app_daily_clicks(p_data jsonb)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    INSERT INTO app_clicks_daily (user_id, package_name, click_date, click_count)
    SELECT auth.uid(), d->>'package_name',
           (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date,
           (d->>'click_count')::bigint
    FROM jsonb_array_elements(p_data) d
    ON CONFLICT (user_id, package_name, click_date)
    DO UPDATE SET click_count = EXCLUDED.click_count;
END;
$$;

-- =============================================================
-- 3. 앱별 일별 Touch 동기화 (배치 upsert)
-- =============================================================
CREATE OR REPLACE FUNCTION upsert_app_daily_touches(p_data jsonb)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    INSERT INTO app_clicks_daily (user_id, package_name, click_date, touch_count)
    SELECT auth.uid(), d->>'package_name',
           (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date,
           (d->>'touch_count')::bigint
    FROM jsonb_array_elements(p_data) d
    ON CONFLICT (user_id, package_name, click_date)
    DO UPDATE SET touch_count = EXCLUDED.touch_count;
END;
$$;

-- =============================================================
-- 4. 앱별 Score 랭킹 조회
-- =============================================================
CREATE OR REPLACE FUNCTION get_app_ranking(
    p_package_name text,
    p_period text DEFAULT 'alltime',
    p_limit int DEFAULT 50
)
RETURNS TABLE(
    rank bigint,
    user_id uuid,
    display_name text,
    avatar_url text,
    click_count bigint
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
AS $$
DECLARE
    v_start_date date;
BEGIN
    v_start_date := CASE p_period
        WHEN 'daily'   THEN (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date
        WHEN 'weekly'  THEN (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date - INTERVAL '7 days'
        WHEN 'monthly' THEN (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date - INTERVAL '30 days'
        ELSE NULL  -- alltime
    END;

    RETURN QUERY
    SELECT
        ROW_NUMBER() OVER (ORDER BY SUM(a.click_count) DESC) AS rank,
        a.user_id,
        COALESCE(p.display_name, '익명') AS display_name,
        p.avatar_url,
        SUM(a.click_count)::bigint AS click_count
    FROM app_clicks_daily a
    JOIN profiles p ON p.id = a.user_id
    WHERE a.package_name = p_package_name
      AND a.click_count > 0
      AND (v_start_date IS NULL OR a.click_date >= v_start_date)
    GROUP BY a.user_id, p.display_name, p.avatar_url
    ORDER BY click_count DESC
    LIMIT p_limit;
END;
$$;

-- =============================================================
-- 5. 앱별 Touch 랭킹 조회
-- =============================================================
CREATE OR REPLACE FUNCTION get_app_touch_ranking(
    p_package_name text,
    p_period text DEFAULT 'alltime',
    p_limit int DEFAULT 50
)
RETURNS TABLE(
    rank bigint,
    user_id uuid,
    display_name text,
    avatar_url text,
    click_count bigint
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
AS $$
DECLARE
    v_start_date date;
BEGIN
    v_start_date := CASE p_period
        WHEN 'daily'   THEN (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date
        WHEN 'weekly'  THEN (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date - INTERVAL '7 days'
        WHEN 'monthly' THEN (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date - INTERVAL '30 days'
        ELSE NULL  -- alltime
    END;

    RETURN QUERY
    SELECT
        ROW_NUMBER() OVER (ORDER BY SUM(a.touch_count) DESC) AS rank,
        a.user_id,
        COALESCE(p.display_name, '익명') AS display_name,
        p.avatar_url,
        SUM(a.touch_count)::bigint AS click_count
    FROM app_clicks_daily a
    JOIN profiles p ON p.id = a.user_id
    WHERE a.package_name = p_package_name
      AND a.touch_count > 0
      AND (v_start_date IS NULL OR a.click_date >= v_start_date)
    GROUP BY a.user_id, p.display_name, p.avatar_url
    ORDER BY click_count DESC
    LIMIT p_limit;
END;
$$;

-- =============================================================
-- 6. delete-user Edge Function에서 app_clicks_daily도 삭제하도록
--    (CASCADE로 자동 삭제되지만 명시적 참고용)
-- =============================================================
-- Edge Function에 추가 필요:
-- await supabaseAdmin.from("app_clicks_daily").delete().eq("user_id", user.id);
