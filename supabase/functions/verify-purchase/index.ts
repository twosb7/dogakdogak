import "@supabase/functions-js/edge-runtime.d.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const GOOGLE_PACKAGE_NAME = "com.dogakdogak.keyboard"

const VALID_PRODUCT_IDS = new Set([
  "com.dogakdogak.switch.pebble2",
  "com.dogakdogak.switch.pebble3",
  "com.dogakdogak.switch.pebble4",
  "com.dogakdogak.switch.pebble5",
  "com.dogakdogak.switch.pebble6",
  "com.dogakdogak.switch.pebble7",
  "com.dogakdogak.switch.pebble8",
  "com.dogakdogak.switch.pebble9",
  "com.dogakdogak.switch.pebble10",
  "com.dogakdogak.switch.pebble11",
  "com.dogakdogak.switch.pebble.bundle",
  "com.dogakdogak.effects.premium",
  "com.dogakdogak.effects.bubble",
  "com.dogakdogak.effects.arcade",
  "com.dogakdogak.effects.cuttypink",
  "com.dogakdogak.effects.bundle",
])

// 간단한 IP 기반 Rate Limiting (메모리 내)
const rateLimitMap = new Map<string, { count: number; resetAt: number }>()
const RATE_LIMIT_WINDOW_MS = 60_000
const RATE_LIMIT_MAX = 10
const JSON_HEADERS = { "Content-Type": "application/json" }

function isRateLimited(ip: string): boolean {
  const now = Date.now()
  const entry = rateLimitMap.get(ip)
  if (!entry || now > entry.resetAt) {
    rateLimitMap.set(ip, { count: 1, resetAt: now + RATE_LIMIT_WINDOW_MS })
    return false
  }
  entry.count++
  return entry.count > RATE_LIMIT_MAX
}

function getClientIp(req: Request): string {
  const trustedHeaders = ["cf-connecting-ip", "x-real-ip", "x-client-ip"]
  for (const header of trustedHeaders) {
    const value = req.headers.get(header)
    if (value) return value.split(",")[0].trim()
  }
  return "unknown"
}

function jsonResponse(body: Record<string, unknown>, status: number): Response {
  return new Response(JSON.stringify(body), { status, headers: JSON_HEADERS })
}

Deno.serve(async (req) => {
  try {
    // Rate limiting
    const clientIp = getClientIp(req)
    if (isRateLimited(clientIp)) {
      return jsonResponse({ valid: false, error: "too many requests" }, 429)
    }

    const authHeader = req.headers.get("authorization")
    if (!authHeader?.startsWith("Bearer ")) {
      return jsonResponse({ valid: false, error: "unauthorized" }, 401)
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL")
    const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY")
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")
    if (!supabaseUrl || !supabaseAnonKey || !serviceRoleKey) {
      return jsonResponse({ valid: false, error: "server misconfigured" }, 500)
    }

    const userClient = createClient(supabaseUrl, supabaseAnonKey, {
      global: { headers: { Authorization: authHeader } },
    })
    const { data: authData, error: authError } = await userClient.auth.getUser()
    if (authError || !authData.user?.id) {
      return jsonResponse({ valid: false, error: "unauthorized" }, 401)
    }
    const userId = authData.user.id

    const body = await req.json()
    const purchaseToken = typeof body.purchaseToken === "string" ? body.purchaseToken.trim() : ""
    const orderId = typeof body.orderId === "string" && body.orderId.trim().length > 0 ? body.orderId.trim() : null
    const productIds = typeof body.productIds === "string" ? body.productIds : ""
    const packageName = typeof body.packageName === "string" ? body.packageName : ""

    // 패키지명 검증
    if (packageName !== GOOGLE_PACKAGE_NAME) {
      return jsonResponse({ valid: false, error: "invalid package" }, 400)
    }

    if (!purchaseToken || !productIds) {
      return jsonResponse({ valid: false, error: "missing required fields" }, 400)
    }

    // productId 화이트리스트 검증
    const productIdList = productIds.split(",").map((id: string) => id.trim()).filter(Boolean)
    if (productIdList.length === 0) {
      return jsonResponse({ valid: false, error: "missing required fields" }, 400)
    }
    for (const pid of productIdList) {
      if (!VALID_PRODUCT_IDS.has(pid)) {
        return jsonResponse({ valid: false, error: "invalid product" }, 400)
      }
    }

    const supabase = createClient(supabaseUrl, serviceRoleKey)
    const purchaseTokenHash = await sha256Hex(purchaseToken)

    // 동일 토큰을 다른 계정이 재사용하는 경우 차단
    const { data: replayRows, error: replayError } = await supabase
      .from("purchase_logs")
      .select("user_id")
      .eq("purchase_token_hash", purchaseTokenHash)
      .neq("user_id", userId)
      .limit(1)
    if (replayError) throw replayError
    if (replayRows && replayRows.length > 0) {
      return jsonResponse({ valid: false, error: "purchase token already used" }, 409)
    }

    // Google Play Developer API로 구매 검증
    const serviceAccountKeyJson = Deno.env.get("GOOGLE_SERVICE_ACCOUNT_KEY")
    let verified = false

    if (serviceAccountKeyJson) {
      const serviceAccountKey = JSON.parse(serviceAccountKeyJson)
      const accessToken = await getGoogleAccessToken(serviceAccountKey)

      for (const productId of productIdList) {
        const url = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${packageName}/purchases/products/${productId}/tokens/${purchaseToken}`
        const res = await fetch(url, {
          headers: { Authorization: `Bearer ${accessToken}` },
        })
        if (!res.ok) {
          return jsonResponse({ valid: false, error: "google verification failed" }, 502)
        }
        const data = await res.json()

        if (orderId && data.orderId !== orderId) {
          return jsonResponse({ valid: false, error: "order mismatch" }, 400)
        }

        if (data.purchaseState === 0) {
          verified = true
        } else {
          return jsonResponse({ valid: false, error: "purchase not valid" }, 400)
        }
      }
    } else {
      return jsonResponse({ valid: false, error: "server misconfigured" }, 500)
    }

    // DB에 구매 로그 기록
    const { error: purchaseLogError } = await supabase.from("purchase_logs").insert({
      order_id: orderId || null,
      product_ids: productIds,
      purchase_token: "[redacted]",
      purchase_token_hash: purchaseTokenHash,
      verified,
      user_id: userId,
    })
    if (purchaseLogError) {
      if (purchaseLogError.code === "23505") {
        const { data: existingLog, error: existingLogError } = await supabase
          .from("purchase_logs")
          .select("user_id")
          .eq("purchase_token_hash", purchaseTokenHash)
          .maybeSingle()
        if (existingLogError) throw existingLogError
        if (existingLog?.user_id && existingLog.user_id !== userId) {
          return jsonResponse({ valid: false, error: "purchase token already used" }, 409)
        }
      } else {
        throw purchaseLogError
      }
    }

    // 검증 성공 + 로그인 사용자 → user_purchases에 기록 (교차 기기 복원용)
    if (verified) {
      const rows = productIdList.map((pid: string) => ({
        user_id: userId,
        product_id: pid,
        verified: true,
      }))
      const { error: upsertError } = await supabase.from("user_purchases").upsert(rows, {
        onConflict: "user_id,product_id",
      })
      if (upsertError) throw upsertError
    }

    return jsonResponse({ valid: verified }, 200)
  } catch (e) {
    console.error("verify-purchase failed", e)
    return jsonResponse({ valid: false, error: "internal error" }, 500)
  }
})

/** Google 서비스 계정으로 Access Token 획득 */
async function getGoogleAccessToken(key: { client_email: string; private_key: string }): Promise<string> {
  const now = Math.floor(Date.now() / 1000)

  const header = base64url(JSON.stringify({ alg: "RS256", typ: "JWT" }))
  const claim = base64url(JSON.stringify({
    iss: key.client_email,
    scope: "https://www.googleapis.com/auth/androidpublisher",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }))

  const signInput = new TextEncoder().encode(`${header}.${claim}`)
  const keyData = key.private_key
    .replace(/-----[^-]+-----/g, "")
    .replace(/\s/g, "")
  const binaryKey = Uint8Array.from(atob(keyData), (c) => c.charCodeAt(0))

  const cryptoKey = await crypto.subtle.importKey(
    "pkcs8",
    binaryKey,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  )
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", cryptoKey, signInput)
  const sig = base64url(String.fromCharCode(...new Uint8Array(signature)))

  const jwt = `${header}.${claim}.${sig}`

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`,
  })
  const data = await res.json()
  return data.access_token
}

function base64url(str: string): string {
  return btoa(str).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "")
}

async function sha256Hex(input: string): Promise<string> {
  const buffer = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(input))
  const bytes = new Uint8Array(buffer)
  return Array.from(bytes).map((b) => b.toString(16).padStart(2, "0")).join("")
}
