// Supabase Edge Function: verify-purchase
// Google Play 영수증을 서버 측에서 검증하고 profiles.premium_switches를 업데이트합니다.
//
// 배포 방법:
//   supabase functions deploy verify-purchase
//
// 필요한 환경변수 (Supabase Dashboard → Settings → Edge Functions):
//   SUPABASE_URL: 프로젝트 URL
//   SUPABASE_SERVICE_ROLE_KEY: 서비스롤 키
//   GOOGLE_PLAY_SERVICE_ACCOUNT_JSON: Google Play Service Account JSON (전체 JSON 문자열)
//
// 전제 조건:
//   - Google Play Developer 계정 ($25 등록)
//   - Service Account 생성 및 Google Play Console 연결
//   - docs/PLAY_DEVELOPER_SETUP.md 참고

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { JWT } from "https://deno.land/x/djwt@v3.0.2/mod.ts";

const PLAY_API_BASE = "https://androidpublisher.googleapis.com/androidpublisher/v3";
const PACKAGE_NAME = "com.dogakdogak.keyboard";

async function getGoogleAccessToken(): Promise<string> {
  const serviceAccountJson = Deno.env.get("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON") ?? "";
  const serviceAccount = JSON.parse(serviceAccountJson);

  const now = Math.floor(Date.now() / 1000);
  const payload = {
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/androidpublisher",
    aud: "https://oauth2.googleapis.com/token",
    exp: now + 3600,
    iat: now,
  };

  const privateKey = await crypto.subtle.importKey(
    "pkcs8",
    new TextEncoder().encode(serviceAccount.private_key
      .replace("-----BEGIN PRIVATE KEY-----", "")
      .replace("-----END PRIVATE KEY-----", "")
      .replace(/\n/g, "")),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );

  const jwt = await new JWT({ alg: "RS256" }).sign(payload, privateKey);
  const tokenRes = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`,
  });
  const tokenData = await tokenRes.json();
  return tokenData.access_token;
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", {
      headers: {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
      },
    });
  }

  try {
    const authHeader = req.headers.get("Authorization");
    if (!authHeader) {
      return new Response(JSON.stringify({ error: "Unauthorized" }), { status: 401 });
    }

    const { purchaseToken, productId } = await req.json();
    if (!purchaseToken || !productId) {
      return new Response(JSON.stringify({ error: "Missing purchaseToken or productId" }), { status: 400 });
    }

    // 현재 유저 확인
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_ANON_KEY") ?? "",
      { global: { headers: { Authorization: authHeader } } }
    );
    const { data: { user }, error: userError } = await supabase.auth.getUser();
    if (userError || !user) {
      return new Response(JSON.stringify({ error: "Unauthorized" }), { status: 401 });
    }

    // Google Play API로 영수증 검증
    const accessToken = await getGoogleAccessToken();
    const verifyRes = await fetch(
      `${PLAY_API_BASE}/applications/${PACKAGE_NAME}/purchases/products/${productId}/tokens/${purchaseToken}`,
      { headers: { Authorization: `Bearer ${accessToken}` } }
    );
    const purchase = await verifyRes.json();

    // purchaseState: 0 = Purchased, 1 = Canceled, 2 = Pending
    if (purchase.purchaseState !== 0) {
      return new Response(JSON.stringify({ error: "Purchase not valid", purchaseState: purchase.purchaseState }), { status: 400 });
    }

    // 검증 성공 → profiles 테이블에 구매 정보 업데이트
    const supabaseAdmin = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
      { auth: { autoRefreshToken: false, persistSession: false } }
    );

    const { data: profile } = await supabaseAdmin
      .from("profiles")
      .select("premium_switches")
      .eq("id", user.id)
      .single();

    const existingSwitches: string[] = profile?.premium_switches ?? [];
    if (!existingSwitches.includes(productId)) {
      await supabaseAdmin
        .from("profiles")
        .update({ premium_switches: [...existingSwitches, productId] })
        .eq("id", user.id);
    }

    return new Response(JSON.stringify({ success: true, productId }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  } catch (error) {
    return new Response(JSON.stringify({ error: String(error) }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});
