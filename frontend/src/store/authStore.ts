import { create } from 'zustand';
import type { UserDTO } from '../api/generated/model';
import { getApiV1UsersMe } from '../api/generated/users/users';

interface AuthState {
  user: UserDTO | null;
  isAuthenticated: boolean;
  isLoaded: boolean;
  sidebarCollapsed: boolean;
  checkAuth: () => Promise<void>;
  login: () => void;
  logout: () => void;
  toggleSidebar: () => void;
}

const API_BASE_URL = (import.meta.env as Record<string, string>).VITE_API_BASE_URL || 'http://localhost:8080';

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  isAuthenticated: false,
  isLoaded: false,
  sidebarCollapsed: false,
  checkAuth: async () => {
    try {
      const response = await getApiV1UsersMe();
      // Orval customInstance returns data directly if status is 200, 
      // but if typed with Response objects, we check status.
      // Based on generated code: export const getApiV1UsersMe = async ( options?: RequestInit): Promise<getApiV1UsersMeResponse>
      // and getApiV1UsersMeResponse = (getApiV1UsersMeResponseSuccess | getApiV1UsersMeResponseError)
      // Success is { data: UserDTO, status: 200, headers: Headers }
      
      if (response && response.status === 200 && response.data) {
        set({ user: response.data, isAuthenticated: true, isLoaded: true });
      } else {
        set({ user: null, isAuthenticated: false, isLoaded: true });
      }
    } catch {
      set({ user: null, isAuthenticated: false, isLoaded: true });
    }
  },
  login: () => {
    window.location.href = `${API_BASE_URL}/login`;
  },
  logout: () => {
    window.location.href = `${API_BASE_URL}/logout`;
  },
  toggleSidebar: () => set((state) => ({ sidebarCollapsed: !state.sidebarCollapsed })),
}));
