import axios, { AxiosError } from 'axios';
import type { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import { v4 as uuidv4 } from 'uuid';
import logger from '../util/logger';

/**
 * Custom request config to support idempotency keys and other metadata
 */
export interface CustomRequestConfig extends InternalAxiosRequestConfig<unknown> {
  idempotencyKey?: string;
  skipEnvelopeUnwrap?: boolean;
}

/**
 * Standard API Envelope structure used by the backend
 */
export interface ApiEnvelope<T = unknown> {
  data: T;
  meta: {
    timestamp: string;
    version?: number | string;
    [key: string]: unknown;
  };
  error: {
    code: string;
    message: string;
    details?: unknown;
  } | null;
}

const API_BASE_URL = (import.meta.env as Record<string, string>).VITE_API_BASE_URL || 'http://localhost:8080';

const client: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

/**
 * Request Interceptor: Tracing & Idempotency
 */
client.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 1. Inject Correlation ID for every request
    config.headers['X-Correlation-ID'] = uuidv4();

    // 2. Inject Idempotency Key for /commands/* POST requests
    if (config.method?.toLowerCase() === 'post' && config.url?.includes('/commands/')) {
      // Use existing key if provided (e.g. for retries), otherwise generate new
      const customConfig = config as CustomRequestConfig;
      if (!customConfig.idempotencyKey) {
        customConfig.idempotencyKey = uuidv4();
      }
      config.headers['Idempotency-Key'] = customConfig.idempotencyKey;
    }

    return config;
  },
  (error: AxiosError) => Promise.reject(error)
);

/**
 * Response Interceptor: Envelope Unwrapping & Global Error Handling
 */
client.interceptors.response.use(
  (response: AxiosResponse) => {
    const config = response.config as CustomRequestConfig;
    if (config.skipEnvelopeUnwrap) {
      // eslint-disable-next-line @typescript-eslint/no-unsafe-return
      return response.data;
    }

    const data = response.data as ApiEnvelope<unknown>;
    // The custom mutator is responsible for unwrapping the response envelope.
    if (data?.error) {
      // If the API returns a 2xx status but includes an error in the envelope,
      // treat it as a failed request. Wrap in an Error to satisfy linting.
      const error = new Error(data.error.message);
      (error as Error & { code?: string }).code = data.error.code;
      return Promise.reject(error);
    }
    // eslint-disable-next-line @typescript-eslint/no-unsafe-return, @typescript-eslint/no-explicit-any
    return data?.data as any;
  },
  async (error: AxiosError<ApiEnvelope>) => {
    const { response } = error;

    if (response) {
      // Handle standard status codes
      switch (response.status) {
        case 401:
          logger.error('[API] Unauthorized - Redirecting to login or clearing session', { status: response.status });
          // TODO: Implement OIDC logout / redirect
          break;
        case 403:
          logger.error('[API] Forbidden - Insufficient permissions', { status: response.status });
          break;
        case 429: {
          logger.warn('[API] Rate Limited', { status: response.status });
          // Handle Retry-After if present
          const retryAfter = response.headers ? (response.headers['retry-after'] as string | undefined) : undefined;
          if (retryAfter) {
            logger.warn(`[API] Should retry after ${retryAfter} seconds`, { retryAfter });
          }
          break;
        }
        case 503:
          logger.error('[API] Service Unavailable', { status: response.status });
          break;
        default: {
          // Extract error message from envelope if available
          const errorMessage = (response.data as ApiEnvelope | undefined)?.error?.message || error.message;
          logger.error(`[API] Error ${response.status}: ${errorMessage}`, { status: response.status, code: (response.data as ApiEnvelope | undefined)?.error?.code });
        }
      }
    } else {
      logger.error('[API] Network Error or Timeout', { message: error.message });
    }

    return Promise.reject(error);
  }
);

const toAxiosHeaders = (headers: HeadersInit | undefined): Record<string, string> => {
  if (!headers) {
    return {};
  }
  if (headers instanceof Headers) {
    const obj: Record<string, string> = {};
    headers.forEach((value, key) => {
      obj[key] = value;
    });
    return obj;
  }
  if (Array.isArray(headers)) {
    return Object.fromEntries(headers);
  }
  return headers;
};

/**
 * Custom fetcher for Orval integration.
 * Maps Orval's fetch-style call to our Axios instance.
 */
export const customInstance = <T>(
  url: string,
  options: RequestInit & { params?: unknown }
): Promise<T> => {
  const { method, headers, body, params, signal } = options;
  
  let data = body;
  if (typeof body === 'string') {
    const requestHeaders = new Headers(headers);
    const contentType = requestHeaders.get('Content-Type');
    
    if (contentType?.includes('application/json')) {
      try {
        // eslint-disable-next-line @typescript-eslint/no-unsafe-assignment
        data = JSON.parse(body);
      } catch (e) {
        logger.error('[API] Failed to parse JSON body', { error: e, url });
      }
    }
  }

  return client({
    url,
    method,
    headers: toAxiosHeaders(headers),
    data,
    params: params as Record<string, unknown>,
    signal: signal || undefined,
  }) as unknown as Promise<T>;
};

export default client;
