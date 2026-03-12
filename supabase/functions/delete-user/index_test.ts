import { assertEquals, assertRejects } from "jsr:@std/assert"

import { deleteUserResources } from "./index.ts"

Deno.test("deleteUserResources deletes avatar before auth before database purge", async () => {
  const calls: string[] = []
  const adminClient = {
    storage: {
      from: () => ({
        remove: async () => {
          calls.push("avatar")
          return { error: null }
        },
      }),
    },
    auth: {
      admin: {
        deleteUser: async () => {
          calls.push("auth")
          return { error: null }
        },
      },
    },
    rpc: async () => {
      calls.push("purge")
      return { error: null }
    },
  }

  await deleteUserResources(adminClient as never, "user-1")

  assertEquals(calls, ["avatar", "auth", "purge"])
})

Deno.test("deleteUserResources stops before purge when auth deletion fails", async () => {
  const calls: string[] = []
  const adminClient = {
    storage: {
      from: () => ({
        remove: async () => {
          calls.push("avatar")
          return { error: null }
        },
      }),
    },
    auth: {
      admin: {
        deleteUser: async () => {
          calls.push("auth")
          return { error: { message: "boom" } }
        },
      },
    },
    rpc: async () => {
      calls.push("purge")
      return { error: null }
    },
  }

  await assertRejects(
    () => deleteUserResources(adminClient as never, "user-1"),
    Error,
    "auth delete failed: boom",
  )
  assertEquals(calls, ["avatar", "auth"])
})
