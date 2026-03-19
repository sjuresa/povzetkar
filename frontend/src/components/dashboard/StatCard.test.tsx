import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { StatCard } from './StatCard';

describe('StatCard', () => {
  it('renders label and value', () => {
    render(
      <StatCard 
        label="Test Metric" 
        value="42" 
        icon={<span data-testid="test-icon">icon</span>} 
      />
    );
    
    expect(screen.getByText('Test Metric')).toBeInTheDocument();
    expect(screen.getByText('42')).toBeInTheDocument();
    expect(screen.getByTestId('test-icon')).toBeInTheDocument();
  });

  it('renders description if provided', () => {
    render(
      <StatCard 
        label="Test Metric" 
        value="42" 
        icon={<span>icon</span>}
        description="Helper text" 
      />
    );
    
    expect(screen.getByText('Helper text')).toBeInTheDocument();
  });

  it('renders placeholder if value is undefined', () => {
    render(
      <StatCard 
        label="Empty" 
        value={undefined} 
        icon={<span>icon</span>} 
      />
    );
    
    expect(screen.getByText('--')).toBeInTheDocument();
  });
});
