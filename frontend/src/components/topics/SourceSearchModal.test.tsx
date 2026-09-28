import { useState } from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { SourceSearchModal } from './SourceSearchModal';
import { useGetApiV1Sources } from '../../api/generated/content-sources/content-sources';
import type { SelectedSource } from './selectedSource';

vi.mock('../../api/generated/content-sources/content-sources', () => ({
  useGetApiV1Sources: vi.fn(),
}));

vi.mock('./QuickSourceIntake', () => ({
  QuickSourceIntake: ({ onSuccess, onCancel }: { onSuccess: (draft: object) => void; onCancel: () => void }) => (
    <div data-testid="quick-add">
      <button onClick={() => onSuccess({ platform: 'YOUTUBE', sourceIdentifier: ' @new ', name: 'New Channel' })}>submit-quick-add</button>
      <button onClick={onCancel}>cancel-quick-add</button>
    </div>
  ),
}));

/** The form keeps the modal mounted and toggles isOpen — as TopicForm does. */
function Harness({ onSelect }: Readonly<{ onSelect: (source: SelectedSource) => void }>) {
  const [open, setOpen] = useState(true);
  return (
    <>
      <button onClick={() => setOpen(true)}>open-modal</button>
      <SourceSearchModal isOpen={open} onClose={() => setOpen(false)} onSelect={onSelect} />
    </>
  );
}

const openQuickAdd = () => fireEvent.click(screen.getByText('topics.form.search_modal.quick_add_trigger'));
const reopen = () => fireEvent.click(screen.getByText('open-modal'));

describe('SourceSearchModal', () => {
  beforeEach(() => {
    vi.mocked(useGetApiV1Sources).mockReturnValue({
      data: { data: { items: [{ id: 1, name: 'My Channel', platform: 'YOUTUBE', sourceIdentifier: 'UC1' }], totalCount: 1 } },
      isLoading: false,
    } as unknown as ReturnType<typeof useGetApiV1Sources>);
  });

  it('starts at the search list again after being closed with X from Quick Add', () => {
    render(<Harness onSelect={vi.fn()} />);
    openQuickAdd();
    expect(screen.getByTestId('quick-add')).toBeInTheDocument();

    fireEvent.click(screen.getByLabelText('common.close'));
    reopen();

    expect(screen.queryByTestId('quick-add')).not.toBeInTheDocument();
    expect(screen.getByText('My Channel')).toBeInTheDocument();
  });

  it('passes a Quick Add entry on as a draft and starts at the search list next time', () => {
    const onSelect = vi.fn();
    render(<Harness onSelect={onSelect} />);
    openQuickAdd();

    fireEvent.click(screen.getByText('submit-quick-add'));

    expect(onSelect).toHaveBeenCalledWith(expect.objectContaining({
      key: 'new:YOUTUBE:@new',
      name: 'New Channel',
      draft: { platform: 'YOUTUBE', sourceIdentifier: '@new', name: 'New Channel' },
    }));
    reopen();
    expect(screen.queryByTestId('quick-add')).not.toBeInTheDocument();
  });

  it('passes a chosen existing source on by its id and clears the search', () => {
    const onSelect = vi.fn();
    render(<Harness onSelect={onSelect} />);
    fireEvent.change(screen.getByPlaceholderText('topics.form.search_modal.search_placeholder'), { target: { value: 'my' } });

    fireEvent.click(screen.getByText('My Channel'));

    expect(onSelect).toHaveBeenCalledWith(expect.objectContaining({ key: 'id:1', id: 1, name: 'My Channel' }));
    reopen();
    expect(screen.getByPlaceholderText('topics.form.search_modal.search_placeholder')).toHaveValue('');
  });
});
