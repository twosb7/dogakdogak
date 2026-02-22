import "@supabase/functions-js/edge-runtime.d.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const GOOGLE_PACKAGE_NAME = "com.dogakdogak.keyboard"

Deno.serve(async (req) => {
  try {
    const { purchaseToken, orderId, productIds, packageName } = await req.json()

    // 패키지명 검증
    if (packageName !== GOOGLE_PACKAGE_NAME) {
      return new Response(
        JSON.stringify({ valid: false, error: "invalid package" }),
        { status: 400, headers: { "Content-Type": "application/json" } }
      )
    }

    if (!purchaseToken || !productIds) {
      return new Response(
        JSON.stringify({ valid: false, error: "missing required fields" }),
        { status: 400, headers: { "Content-Type": "application/json" } }
      )
    }

    // Google Play Developer API로 구매 검증
    const serviceAccountKeyJson = Deno.env.get("GOOGLE_SERVICE_ACCOUNT_KEY")
    let verified = false

    if (serviceAccountKeyJson) {
      const serviceAccountKey = JSON.parse(serviceAccountKeyJson)
      const accessToken = await getGoogleAccessToken(serviceAccountKey)

      for (const productId of productIds.split(",")) {
        const url = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${packageName}/purchases/products/${productId.trim()}/tokens/${purchaseToken}`
        const res = await fetch(url, {
          headers: { Authorization: `Bearer ${accessToken}` },
        })
        const data = await res.json()

        if (data.purchaseState === 0) {
          verified = true
        } else {
          return new Response(
            JSON.stringify({ valid: false, error: "purchase not valid", detail: data }),
            { status: 400, headers: { "Content-Type": "application/json" } }
          )
        }
      }
    } else {
      // 서비스 계정 키 미설정 시 → 로그만 기록 (검증 스킵)
      verified = false
    }

    // DB에 구매 로그 기록
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
    )
    await supabase.from("purchase_logs").insert({
      order_id: orderId || null,
      product_ids: productIds,
      purchase_token: purchaseToken.substring(0, 20) + "...",
      verified,
    })

    return new Response(
      JSON.stringify({ valid: verified }),
      { status: 200, headers: { "Content-Type": "application/json" } }
    )
  } catch (e) {
    return new Response(
      JSON.stringify({ valid: false, error: (e as Error).message }),
      { status: 500, headers: { "Content-Type": "application/json" } }
    )
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
