import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useAuthStore } from './authStore';

describe('authStore', () => {
  beforeEach(() => {
    // Reset the store before each test
    const { clearAuth } = useAuthStore.getState();
    clearAuth();
    
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
});
