import axios, { AxiosError } from 'axios';
import type { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import { v4 as uuidv4 } from 'uuid';

/**
 * Custom request config to support idempotency keys and other metadata
 */
export interface CustomRequestConfig extends InternalAxiosRequestConfig {
  idempotencyKey?: string;
  skipEnvelopeUnwrap?: boolean;
}

/**
 * Standard API Envelope structure used by the backend
 */
export interface ApiEnvelope<T = any> {
  data: T;
  meta: {
    timestamp: string;
    version?: number | string;
    [key: string]: any;
  };
  error: {
    code: string;
    message: string;
    details?: any;
  } | null;
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

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
  (response: AxiosResponse<ApiEnvelope>) => {
    const config = response.config as CustomRequestConfig;
    
    // If we want the full envelope (e.g. to access meta), we can skip unwrapping
    if (config.skipEnvelopeUnwrap) {
      return response;
    }

    // Unwrap the backend envelope: { data, meta, error } -> data
    // The data is what the generated hooks expect
    return response.data.data as any;
  },
  async (error: AxiosError<ApiEnvelope>) => {
    const { response } = error;

    if (response) {
      // Handle standard status codes
      switch (response.status) {
        case 401:
          console.error('[API] Unauthorized - Redirecting to login or clearing session');
          // TODO: Implement OIDC logout / redirect
          break;
        case 403:
          console.error('[API] Forbidden - Insufficient permissions');
          break;
        case 429:
          console.warn('[API] Rate Limited');
          // Handle Retry-After if present
          const retryAfter = response.headers['retry-after'];
          if (retryAfter) {
            console.warn(`[API] Should retry after ${retryAfter} seconds`);
          }
          break;
        case 503:
          console.error('[API] Service Unavailable');
          break;
        default:
          // Extract error message from envelope if available
          const errorMessage = response.data?.error?.message || error.message;
          console.error(`[API] Error ${response.status}: ${errorMessage}`);
      }
    } else {
      console.error('[API] Network Error or Timeout');
    }

    return Promise.reject(error);
  }
);

/**
 * Custom fetcher for Orval integration.
 * Maps Orval's fetch-style call to our Axios instance.
 */
export const customInstance = <T>(
  url: string,
  options: any // This is essentially RequestInit | { method, headers, ... }
): Promise<T> => {
  return client({
    url,
    method: options.method,
    headers: options.headers,
    data: options.body ? JSON.parse(options.body) : undefined,
    params: options.params,
    ...options,
  }) as unknown as Promise<T>;
};

export default client;
