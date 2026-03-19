import { render, screen, waitFor } from '@testing-library/react'
import { describe, it, expect, beforeEach } from 'vitest'
import App from '../App'
import { useAuthStore } from '../store/authStore'

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
    render(<App />)
    
    // Wait for the route to resolve and render the component
    await waitFor(() => {
      expect(screen.getByText(/Start Your Briefing/i)).toBeInTheDocument();
    }, { timeout: 3000 });
  })
})
