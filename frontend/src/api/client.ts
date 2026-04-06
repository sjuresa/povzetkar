import axios from 'axios';
import type { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios';

import logger from '../util/logger';
import { useAuthStore } from '../store/authStore';
import axiosRetry, { isNetworkOrIdempotentRequestError } from 'axios-retry';

function generateUuid(): string {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replaceAll(/[xy]/g, (c) => {
    const r = Math.trunc(Math.random() * 16);
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

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

/**
 * Type helper to unwrap the API envelope if it exists
 */
export type UnwrapEnvelope<T> = T extends { data: infer D } ? D : T;

const API_BASE_URL = (import.meta.env as Record<string, string>).VITE_API_BASE_URL || '';

const client: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
    'X-Requested-With': 'XMLHttpRequest',
  },
});

// Configure Axios Retry Strategy
axiosRetry(client, {
  retries: 3,
  retryDelay: (retryCount) => axiosRetry.exponentialDelay(retryCount),
  onRetry: (retryCount) => {
    logger.warn(`[API] Retrying request (${retryCount}/3)...`);
  },
  retryCondition: (error) => {
    // Retry on network errors or transient 5xx status codes
    return isNetworkOrIdempotentRequestError(error) ||
           (!!error.response?.status && [502, 503, 504].includes(error.response.status));
  },
  shouldResetTimeout: true,
});

/**
 * Request Interceptor: Tracing & Idempotency
 */
client.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 1. Inject Correlation ID for every request
    config.headers['X-Correlation-ID'] = generateUuid();

    // 2. Inject Idempotency Key for /commands/* POST requests
    if (config.method?.toLowerCase() === 'post' && config.url?.includes('/commands/')) {
      const customConfig = config as CustomRequestConfig;
      if (!customConfig.idempotencyKey) {
        customConfig.idempotencyKey = generateUuid();
      }
      config.headers['Idempotency-Key'] = customConfig.idempotencyKey;
    }

    return config;
  },
  (error) => Promise.reject(error instanceof Error ? error : new Error(String(error)))
);

/**
 * Response Interceptor: Envelope Unwrapping & Global Error Handling
 */
client.interceptors.response.use(
  (response: AxiosResponse) => {
    const config = response.config as CustomRequestConfig;
    if (config.skipEnvelopeUnwrap) {
      return response;
    }

    const data = response.data as ApiEnvelope<unknown>;
    if (data?.error) {
      const error = new Error(data.error.message);
      (error as Error & { code?: string }).code = data.error.code;
      return Promise.reject(error);
    }
    
    // Replace the response.data with the unwrapped inner data (the actual DTO)
    // This aligns with Orval's expectation that AxiosResponse.data contains the model.
    let unwrappedData = data?.data;
    
    if (Array.isArray(unwrappedData) && data?.meta?.pagination) {
      const pagination = data.meta.pagination as { 
        totalElements: number; 
        page: number; 
        size: number; 
        totalPages: number; 
        hasNext: boolean; 
      };
      unwrappedData = {
        items: unwrappedData,
        totalCount: pagination.totalElements,
        pageIndex: pagination.page,
        pageSize: pagination.size,
        totalPages: pagination.totalPages,
        hasNext: pagination.hasNext
      };
    }
    
    // Aligns with Orval's expectation that the promise resolves to the response object 
    // containing the .data property (which we've unwrapped to be the inner DTO).
    response.data = unwrappedData;
    
    return response;
  },
  (error: import('axios').AxiosError<ApiEnvelope>) => {
    const { response } = error;

    if (response) {
      switch (response.status) {
        case 401:
          logger.error('[API] Unauthorized - Clearing local session', { status: response.status });
          useAuthStore.getState().clearAuth();
          break;
        case 403:
          logger.error('[API] Forbidden - Insufficient permissions', { status: response.status });
          break;
        case 429: {
          logger.warn('[API] Rate Limited', { status: response.status });
          const retryAfter = (response.headers as Record<string, string | undefined>)['retry-after'];
          const user = useAuthStore.getState().user; // Define user here
          if (user?.id && retryAfter) {
            logger.warn(`[API] Should retry after ${retryAfter} seconds`, { retryAfter });
          }
          break;
        }
        case 503:
          logger.error('[API] Service Unavailable', { status: response.status });
          break;
        default: {
          const errorMessage = (response.data as ApiEnvelope | undefined)?.error?.message || error.message;
          logger.error(`[API] Error ${response.status}: ${errorMessage}`, { status: response.status, code: (response.data as ApiEnvelope | undefined)?.error?.code });
        }
      }
    } else if (axios.isCancel(error)) {
      // SILENTLY IGNORE: Cancellation is expected in React 18 / TanStack Query
      logger.debug('[API] Request canceled', { message: error.message });
    } else {
      logger.error('[API] Network Error or Timeout', { message: error.message });
    }

    throw error;
  }
);

const toAxiosHeaders = (headers: HeadersInit | undefined): Record<string, string> => {
  if (!headers) return {};
  if (headers instanceof Headers) {
    const obj: Record<string, string> = {};
    headers.forEach((value, key) => { obj[key] = value; });
    return obj;
  }
  if (Array.isArray(headers)) return Object.fromEntries(headers);
  return headers;
};

declare global {
  interface RequestInit {
    version?: number;
    skipEnvelopeUnwrap?: boolean;
    params?: Record<string, unknown>;
  }
}

export type CustomRequestOptions = {
  method?: string;
  headers?: HeadersInit;
  body?: BodyInit | null;
  params?: Record<string, unknown>;
  signal?: AbortSignal | null;
  version?: number;
  skipEnvelopeUnwrap?: boolean;
} & RequestInit & Record<string, unknown>;

export const customInstance = <T>(
  url: string,
  options: CustomRequestOptions
): Promise<T> => {
  const { method, headers, body, params, signal, version } = options;

  let requestData: BodyInit | null | Record<string, unknown> | undefined = body;
  if (typeof body === 'string') {
    const requestHeaders = new Headers(headers);
    const contentType = requestHeaders.get('Content-Type');

    if (contentType?.includes('application/json')) {
      try {
        requestData = JSON.parse(body) as Record<string, unknown>;

      } catch (e) {
        logger.error('[API] Failed to parse JSON body', { error: e, url });
      }
    }
  }

  const finalHeaders = toAxiosHeaders(headers);
  const normalizedMethod = method?.toUpperCase() || 'GET';

  // 1. Prioritize explicit version in options
  if (version !== undefined && ['PATCH', 'PUT', 'DELETE'].includes(normalizedMethod)) {
    finalHeaders['If-Match'] = `"${version}"`;
  }
  // 2. Fallback to automatic extraction from data object (Optimistic Locking)
  else if (
    requestData &&
    typeof requestData === 'object' &&
    'version' in requestData &&
    ['PATCH', 'PUT'].includes(normalizedMethod)
  ) {
    const dataVersion = (requestData as { version?: number }).version;
    if (dataVersion !== undefined) {
      finalHeaders['If-Match'] = `"${dataVersion}"`;
    }
  }

  return client({
    url,
    method,
    headers: finalHeaders,
    data: requestData,
    params: params as Record<string, unknown>,
    signal: signal || undefined,
  }).then((response) => response as unknown as T);
};

export default client;
