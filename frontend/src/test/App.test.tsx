import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import App from '../App'

describe('App', () => {
  it('renders landing page on first load', async () => {
    render(<App />)
    expect(await screen.findByText(/Start Your Briefing/i)).toBeInTheDocument()
  })
})
