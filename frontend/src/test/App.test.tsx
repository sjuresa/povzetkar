import { render, screen, waitFor } from '@testing-library/react'
import { describe, it, expect, beforeEach } from 'vitest'
import App from '../App'
import { useAuthStore } from '../store/authStore'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
    },
  },
});

describe('App', () => {
  beforeEach(() => {
    // Reset and mock store to avoid real network calls during route initialization
    useAuthStore.setState({ 
      isLoaded: true, 
      isAuthenticated: false, 
      user: null 
    });
  });

  it('renders landing page on first load', async () => {
    render(
      <QueryClientProvider client={queryClient}>
        <App />
      </QueryClientProvider>
    )
    
    // Wait for the route to resolve and render the component
    // Note: Due to i18n mock in setup.ts returning the key, we check for the key pattern
    await waitFor(() => {
      expect(screen.getByTestId('hero-cta')).toBeInTheDocument();
    }, { timeout: 3000 });
  })
})
