/**
 * GlucoTwin AI — typed API client.
 *
 * Base URL is read from the VITE_API_BASE_URL environment variable.
 * In dev mode (JWT_PUBLIC_KEY blank on the backend) requests are authenticated
 * with the X-Dev-Auth header.  When a real JWT is available, swap the header
 * for `Authorization: Bearer <token>` — change only this file.
 *
 * Never put real credentials or API keys here.
 */

const BASE_URL = (import.meta.env.VITE_API_BASE_URL as string) ?? ''

const DEFAULT_HEADERS: Record<string, string> = {
  'Content-Type': 'application/json',
  // Development auth — backend accepts this when JWT_PUBLIC_KEY is blank.
  // Replace with `Authorization: Bearer <jwt>` when a real auth server is set up.
  'X-Dev-Auth': 'ROLE_CLINICIAN',
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly traceId?: string
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const url = `${BASE_URL}${path}`
  const response = await fetch(url, {
    ...init,
    headers: {
      ...DEFAULT_HEADERS,
      ...(init?.headers as Record<string, string> | undefined),
    },
  })

  if (!response.ok) {
    // Try to parse structured error body
    let errorCode = `HTTP_${response.status}`
    let errorMessage = `Request failed with status ${response.status}`
    let traceId: string | undefined

    try {
      const body = (await response.json()) as {
        error?: string
        message?: string
        traceId?: string
      }
      if (body.error) errorCode = body.error
      if (body.message) errorMessage = body.message
      if (body.traceId) traceId = body.traceId
    } catch {
      // body was not JSON — use defaults
    }

    throw new ApiError(response.status, errorCode, errorMessage, traceId)
  }

  // 204 No Content — return null safely
  if (response.status === 204) return null as unknown as T

  return response.json() as Promise<T>
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', body: body ? JSON.stringify(body) : undefined }),
  put: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'PUT', body: body ? JSON.stringify(body) : undefined }),
  patch: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'PATCH', body: body ? JSON.stringify(body) : undefined }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}
