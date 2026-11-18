// The access token lives only in memory. It is short-lived (15 minutes) and is never
// written to localStorage, where any injected script could read it. After a reload the
// app gets a fresh one from /auth/refresh, using the httpOnly refresh cookie.
let token: string | null = null

export const authToken = {
  get: () => token,
  set: (t: string) => { token = t },
  clear: () => { token = null },
}
