import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { UsersTable } from './UsersTable';
import type { UserDTO } from '../../api/generated/model';

vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));
vi.mock('../../api/generated/users/users', () => ({
  getGetApiV1UsersQueryKey: vi.fn(() => ['users']),
}));

const makeUser = (overrides: Partial<UserDTO> = {}): UserDTO => ({
  id: 1,
  email: 'test@example.com',
  name: 'Test User',
  active: true,
  accountStatus: 'ENABLED',
  lastLoginAt: '2026-05-20T10:00:00Z' as unknown as object,
  createdAt: '2026-01-01T00:00:00Z' as unknown as object,
  roles: [],
  permissions: [],
  ...overrides,
});

function renderTable(users: UserDTO[], sort = '', onSortChange = vi.fn()) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <UsersTable users={users} sort={sort} onSortChange={onSortChange} />
    </QueryClientProvider>
  );
}

describe('UsersTable', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('shows no-results message when users list is empty', () => {
    renderTable([]);
    expect(screen.getByText('admin.users.table.noResults')).toBeInTheDocument();
  });

  it('renders user email in table', () => {
    renderTable([makeUser({ email: 'jane@example.com' })]);
    expect(screen.getByText('jane@example.com')).toBeInTheDocument();
  });

  it('shows Disable button for ENABLED user', () => {
    renderTable([makeUser({ accountStatus: 'ENABLED' })]);
    expect(screen.getByText('admin.users.actions.disable')).toBeInTheDocument();
    expect(screen.queryByText('admin.users.actions.approve')).not.toBeInTheDocument();
  });

  it('shows Approve and Reject buttons for PENDING_APPROVAL user', () => {
    renderTable([makeUser({ accountStatus: 'PENDING_APPROVAL' })]);
    expect(screen.getByText('admin.users.actions.approve')).toBeInTheDocument();
    expect(screen.getByText('admin.users.actions.reject')).toBeInTheDocument();
    expect(screen.queryByText('admin.users.actions.disable')).not.toBeInTheDocument();
  });

  it('shows Enable button for DISABLED user', () => {
    renderTable([makeUser({ accountStatus: 'DISABLED' })]);
    expect(screen.getByText('admin.users.actions.enable')).toBeInTheDocument();
    expect(screen.queryByText('admin.users.actions.disable')).not.toBeInTheDocument();
  });

  it('shows Re-approve button for REJECTED user', () => {
    renderTable([makeUser({ accountStatus: 'REJECTED' })]);
    expect(screen.getByText('admin.users.actions.reapprove')).toBeInTheDocument();
  });

  it('calls onSortChange with asc on first header click', () => {
    const onSortChange = vi.fn();
    renderTable([makeUser()], '', onSortChange);
    fireEvent.click(screen.getByText('admin.users.table.email'));
    expect(onSortChange).toHaveBeenCalledWith('email,asc');
  });

  it('cycles sort: asc -> desc -> none', () => {
    const onSortChange = vi.fn();
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { rerender } = renderTable([makeUser()], '', onSortChange);

    fireEvent.click(screen.getByText('admin.users.table.email'));
    expect(onSortChange).toHaveBeenNthCalledWith(1, 'email,asc');

    rerender(
      <QueryClientProvider client={queryClient}>
        <UsersTable users={[makeUser()]} sort="email,asc" onSortChange={onSortChange} />
      </QueryClientProvider>
    );
    fireEvent.click(screen.getByText('admin.users.table.email'));
    expect(onSortChange).toHaveBeenNthCalledWith(2, 'email,desc');

    rerender(
      <QueryClientProvider client={queryClient}>
        <UsersTable users={[makeUser()]} sort="email,desc" onSortChange={onSortChange} />
      </QueryClientProvider>
    );
    fireEvent.click(screen.getByText('admin.users.table.email'));
    expect(onSortChange).toHaveBeenNthCalledWith(3, '');
  });
});
