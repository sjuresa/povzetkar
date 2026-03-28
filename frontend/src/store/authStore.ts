import { create } from 'zustand';
import type { UserDTO } from '../api/generated/model';
import { getApiV1UsersMe } from '../api/generated/users/users';

interface AuthState {
  user: UserDTO | null;
  isAuthenticated: boolean;
  isLoaded: boolean;
  isLoading: boolean;
  sidebarCollapsed: boolean;
  checkAuth: () => Promise<void>;
  login: () => void;
  logout: () => void;
  toggleSidebar: () => void;
  clearAuth: () => void;
}


let checkAuthPromise: Promise<void> | null = null;

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  isAuthenticated: false,
  isLoaded: false,
  isLoading: false,
  sidebarCollapsed: false,
  checkAuth: async () => {
    // If a check is already in progress, return the existing promise
    if (checkAuthPromise) return checkAuthPromise;

    checkAuthPromise = (async () => {
      set({ isLoading: true });
      try {
        const response = await getApiV1UsersMe();
        const user = response?.data as UserDTO;
        if (user?.id) {
          set({ user, isAuthenticated: true, isLoaded: true, isLoading: false });
        } else {
          set({ user: null, isAuthenticated: false, isLoaded: true, isLoading: false });
        }
      } catch (error: unknown) {
        const err = error as { message?: string; code?: string };
        // If the request was cancelled, we don't know the status yet.
        // We SHOULD NOT set isLoaded: true because that would trigger a redirect to/from landing.
        if (err?.message === 'canceled' || err?.code === 'ERR_CANCELED') {
           set({ isLoading: false });
           return;
        }

        // Only log serious failures, 401 is "normal" for unauthenticated
        set({ user: null, isAuthenticated: false, isLoaded: true, isLoading: false });
      } finally {
        checkAuthPromise = null;
      }
    })();

    return checkAuthPromise;
  },
  login: () => {
    globalThis.location.href = `/auth/login`;
  },
  logout: () => {
    globalThis.location.href = `/logout`;
  },
  clearAuth: () => set({ user: null, isAuthenticated: false, isLoaded: true }),
  toggleSidebar: () => set((state) => ({ sidebarCollapsed: !state.sidebarCollapsed })),
}));
