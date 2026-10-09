import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useAuthStore } from './authStore';
import { getApiV1UsersMe } from '../api/generated/users/users';

vi.mock('../api/generated/users/users', () => ({
  getApiV1UsersMe: vi.fn(),
}));

describe('authStore', () => {
  beforeEach(() => {
    // Reset the store before each test
    const { clearAuth } = useAuthStore.getState();
    clearAuth();
    useAuthStore.setState({ isLoaded: false });
    
    // Mock globalThis.location.href
    vi.stubGlobal('location', {
      href: '',
    });
  });

  it('login() should redirect to /auth/login', () => {
    const { login } = useAuthStore.getState();
    
    login();
    
    expect(globalThis.location.href).toBe('/auth/login');
  });

  it('logout() should redirect to /logout', () => {
    const { logout } = useAuthStore.getState();
    logout();
    expect(globalThis.location.href).toBe('/logout');
  });

  describe('checkAuth()', () => {
    it('should set isAuthenticated: true on success', async () => {
      const mockUser = { id: 1, name: 'Test User' };
      // Mock the WRAPPED response structure Orval expects
      vi.mocked(getApiV1UsersMe).mockResolvedValue({
        data: mockUser,
        status: 200,
        headers: new Headers(),
      } as any);

      const { checkAuth } = useAuthStore.getState();
      await checkAuth();

      const state = useAuthStore.getState();
      expect(state.isAuthenticated).toBe(true);
      expect(state.user).toEqual(mockUser);
      expect(state.isLoaded).toBe(true);
    });

    it('should set isAuthenticated: false on 401/error', async () => {
      vi.mocked(getApiV1UsersMe).mockRejectedValue({
        status: 401,
        message: 'Unauthorized',
      });

      const { checkAuth } = useAuthStore.getState();
      await checkAuth();

      const state = useAuthStore.getState();
      expect(state.isAuthenticated).toBe(false);
      expect(state.user).toBeNull();
      expect(state.isLoaded).toBe(true);
    });

    it('should not set isLoaded: true on cancellation', async () => {
      vi.mocked(getApiV1UsersMe).mockRejectedValue({
        code: 'ERR_CANCELED',
        message: 'canceled',
      });

      const { checkAuth } = useAuthStore.getState();
      await checkAuth();

      const state = useAuthStore.getState();
      expect(state.isLoaded).toBe(false); // Should stay false to avoid redirect loop
    });
  });
});
