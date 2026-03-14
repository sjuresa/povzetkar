import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import App from '../App'

describe('App', () => {
  it('renders progress text', () => {
    render(<App />)
    expect(screen.getByText(/AI-Powered Briefings/i)).toBeInTheDocument()
  })
})
