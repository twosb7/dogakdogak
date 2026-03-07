import "@supabase/functions-js/edge-runtime.d.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const JSON_HEADERS = {
  "Content-Type": "application/json",
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
}

function jsonResponse(body: Record<string, unknown>, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: JSON_HEADERS,
  })
}

async function deleteRows(
  supabase: ReturnType<typeof createClient>,
  table: string,
  column: string,
  userId: string,
) {
  const { error } = await supabase.from(table).delete().eq(column, userId)
  if (error) {
    throw new Error(`${table} delete failed: ${error.message}`)
  }
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: JSON_HEADERS })
  }

  if (req.method !== "POST") {
    return jsonResponse({ success: false, error: "method not allowed" }, 405)
  }

  try {
    const authHeader = req.headers.get("authorization")
    if (!authHeader?.startsWith("Bearer ")) {
      return jsonResponse({ success: false, error: "unauthorized" }, 401)
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL")
    const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY")
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")

    if (!supabaseUrl || !supabaseAnonKey || !serviceRoleKey) {
      return jsonResponse({ success: false, error: "server misconfigured" }, 500)
    }

    const userClient = createClient(supabaseUrl, supabaseAnonKey, {
      auth: { autoRefreshToken: false, persistSession: false },
      global: { headers: { Authorization: authHeader } },
    })

    const { data: authData, error: authError } = await userClient.auth.getUser()
    if (authError || !authData.user?.id) {
      return jsonResponse({ success: false, error: "unauthorized" }, 401)
    }

    const userId = authData.user.id
    const adminClient = createClient(supabaseUrl, serviceRoleKey, {
      auth: { autoRefreshToken: false, persistSession: false },
    })

    await deleteRows(adminClient, "clicks_daily", "user_id", userId)
    await deleteRows(adminClient, "app_clicks_daily", "user_id", userId)
    await deleteRows(adminClient, "user_purchases", "user_id", userId)
    await deleteRows(adminClient, "purchase_logs", "user_id", userId)

    const { error: avatarError } = await adminClient.storage
      .from("avatars")
      .remove([`${userId}.jpg`])
    if (avatarError) {
      throw new Error(`avatar delete failed: ${avatarError.message}`)
    }

    await deleteRows(adminClient, "profiles", "id", userId)

    const { error: deleteUserError } = await adminClient.auth.admin.deleteUser(userId)
    if (deleteUserError) {
      throw new Error(`auth delete failed: ${deleteUserError.message}`)
    }

    return jsonResponse({ success: true })
  } catch (error) {
    console.error("delete-user failed", error)
    const message = error instanceof Error ? error.message : "internal error"
    return jsonResponse({ success: false, error: message }, 500)
  }
})
