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

const API_BASE_URL = (import.meta.env as Record<string, string>).VITE_API_BASE_URL;

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
        const { data: user } = await getApiV1UsersMe();
        if (user && user.id) {
          set({ user, isAuthenticated: true, isLoaded: true, isLoading: false });
        } else {
          set({ user: null, isAuthenticated: false, isLoaded: true, isLoading: false });
        }
      } catch {
        // Only log serious failures, 401 is "normal" for unauthenticated
        set({ user: null, isAuthenticated: false, isLoaded: true, isLoading: false });
      } finally {
        checkAuthPromise = null;
      }
    })();

    return checkAuthPromise;
  },
  login: () => {
    globalThis.location.href = `${API_BASE_URL}/login`;
  },
  logout: () => {
    globalThis.location.href = `${API_BASE_URL}/logout`;
  },
  clearAuth: () => set({ user: null, isAuthenticated: false, isLoaded: true }),
  toggleSidebar: () => set((state) => ({ sidebarCollapsed: !state.sidebarCollapsed })),
}));
