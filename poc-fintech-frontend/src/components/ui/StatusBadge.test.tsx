import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { StatusBadge } from './StatusBadge';

describe('StatusBadge', () => {
  it('renders COMPLETED with green styling', () => {
    render(<StatusBadge status="COMPLETED" />);
    const badge = screen.getByText('Completed');
    expect(badge).toBeInTheDocument();
    expect(badge.className).toContain('bg-green');
  });

  it('renders FAILED with red styling', () => {
    render(<StatusBadge status="FAILED" />);
    const badge = screen.getByText('Failed');
    expect(badge).toBeInTheDocument();
    expect(badge.className).toContain('bg-red');
  });

  it('renders INITIATED with blue styling', () => {
    render(<StatusBadge status="INITIATED" />);
    const badge = screen.getByText('Initiated');
    expect(badge).toBeInTheDocument();
    expect(badge.className).toContain('bg-blue');
  });

  it('renders FRAUD_CHECKING correctly', () => {
    render(<StatusBadge status="FRAUD_CHECKING" />);
    expect(screen.getByText('Fraud Check')).toBeInTheDocument();
  });
});

