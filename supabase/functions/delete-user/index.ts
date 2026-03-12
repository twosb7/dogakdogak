import "@supabase/functions-js/edge-runtime.d.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

type SupabaseErrorLike = { message: string } | null

type PurchaseLogHashRow = {
  purchase_token_hash: string | null
}

type DeleteUserAdminClient = {
  from: (table: string) => {
    select: (columns: string) => {
      eq: (
        column: string,
        value: string,
      ) => Promise<{ data: PurchaseLogHashRow[] | null; error: SupabaseErrorLike }>
    }
  }
  rpc: (
    fn: string,
    args: Record<string, unknown>,
  ) => Promise<{ error: SupabaseErrorLike }>
  storage: {
    from: (bucket: string) => {
      remove: (paths: string[]) => Promise<{ error: SupabaseErrorLike }>
    }
  }
  auth: {
    admin: {
      deleteUser: (userId: string) => Promise<{ error: SupabaseErrorLike }>
    }
  }
}

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

async function getPurchaseLogHashes(
  adminClient: DeleteUserAdminClient,
  userId: string,
): Promise<string[]> {
  const { data, error } = await adminClient
    .from("purchase_logs")
    .select("purchase_token_hash")
    .eq("user_id", userId)
  if (error) {
    throw new Error(`purchase log lookup failed: ${error.message}`)
  }
  return (data ?? [])
    .map((row) => row.purchase_token_hash)
    .filter((hash): hash is string => typeof hash === "string" && hash.length > 0)
}

export async function deleteUserResources(
  adminClient: DeleteUserAdminClient,
  userId: string,
  purchaseTokenHashes: string[] = [],
) {
  const { error: avatarError } = await adminClient.storage
    .from("avatars")
    .remove([`${userId}.jpg`])
  if (avatarError) {
    throw new Error(`avatar delete failed: ${avatarError.message}`)
  }

  const { error: deleteUserError } = await adminClient.auth.admin.deleteUser(userId)
  if (deleteUserError) {
    throw new Error(`auth delete failed: ${deleteUserError.message}`)
  }

  const { error: purgeError } = await adminClient.rpc("delete_user_owned_data", {
    p_user_id: userId,
    p_purchase_token_hashes: purchaseTokenHashes,
  })
  if (purgeError) {
    throw new Error(`delete_user_owned_data failed: ${purgeError.message}`)
  }
}

export async function handler(req: Request): Promise<Response> {
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
    }) as unknown as DeleteUserAdminClient

    const purchaseTokenHashes = await getPurchaseLogHashes(adminClient, userId)
    await deleteUserResources(adminClient, userId, purchaseTokenHashes)

    return jsonResponse({ success: true })
  } catch (error) {
    console.error("delete-user failed", error)
    const message = error instanceof Error ? error.message : "internal error"
    return jsonResponse({ success: false, error: message }, 500)
  }
}

if (import.meta.main) {
  Deno.serve(handler)
}
