CREATE OR REPLACE FUNCTION public.delete_user_owned_data(
    p_user_id UUID,
    p_purchase_token_hashes TEXT[] DEFAULT ARRAY[]::TEXT[]
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    DELETE FROM clicks_daily WHERE user_id = p_user_id;
    DELETE FROM app_clicks_daily WHERE user_id = p_user_id;
    DELETE FROM user_purchases WHERE user_id = p_user_id;
    DELETE FROM purchase_logs
    WHERE user_id = p_user_id
       OR purchase_token_hash = ANY(COALESCE(p_purchase_token_hashes, ARRAY[]::TEXT[]));
    DELETE FROM profiles WHERE id = p_user_id;
END;
$$;

REVOKE ALL ON FUNCTION public.delete_user_owned_data(UUID, TEXT[]) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.delete_user_owned_data(UUID, TEXT[]) TO service_role;
