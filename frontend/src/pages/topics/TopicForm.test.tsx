import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { TopicForm } from './TopicForm';
import { usePostApiV1Topics, usePatchApiV1TopicsId, useGetApiV1TopicsDefaultPromptTemplate } from '../../api/generated/topics/topics';
import type { Topic } from '../../api/generated/model';
import type { SelectedSource } from '../../components/topics/selectedSource';

vi.mock('../../api/generated/topics/topics', () => ({
  usePostApiV1Topics: vi.fn(),
  usePatchApiV1TopicsId: vi.fn(),
  useGetApiV1TopicsDefaultPromptTemplate: vi.fn(),
}));

const invalidateQueries = vi.fn();
vi.mock('@tanstack/react-query', () => ({
  useQueryClient: () => ({ invalidateQueries }),
}));

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const markStepComplete = vi.fn();
vi.mock('../../store/onboardingStore', () => ({
  useOnboardingStore: { getState: () => ({ markStepComplete }) },
}));

const EXISTING: SelectedSource = { key: 'id:1', id: 1, name: 'Existing Channel', platform: 'YOUTUBE', identifier: 'UC1' };
const DRAFT: SelectedSource = {
  key: 'new:YOUTUBE:UC2', name: 'New Channel', platform: 'YOUTUBE', identifier: 'UC2',
  draft: { platform: 'YOUTUBE', sourceIdentifier: 'UC2', name: 'New Channel' },
};
// the existing channel entered again through Quick Add (the check resolved it to the same channel ID)
const SAME_AS_EXISTING: SelectedSource = {
  key: 'new:YOUTUBE:UC1', name: 'Existing Channel', platform: 'YOUTUBE', identifier: 'UC1',
  draft: { platform: 'YOUTUBE', sourceIdentifier: 'UC1', name: 'Existing Channel' },
};

// The modal is replaced by buttons that pick a source, as the real modal does through onSelect
vi.mock('../../components/topics/SourceSearchModal', () => ({
  SourceSearchModal: ({ isOpen, onSelect }: { isOpen: boolean; onSelect: (s: SelectedSource) => void }) =>
    isOpen ? (
      <div>
        <button onClick={() => onSelect(EXISTING)}>pick-existing</button>
        <button onClick={() => onSelect(DRAFT)}>pick-draft</button>
        <button onClick={() => onSelect(SAME_AS_EXISTING)}>pick-same-channel</button>
      </div>
    ) : null,
}));

const createTopic = vi.fn();
const patchTopic = vi.fn();

function givenMutations({ patchPending = false } = {}) {
  vi.mocked(usePostApiV1Topics).mockReturnValue({ mutateAsync: createTopic, isPending: false } as unknown as ReturnType<typeof usePostApiV1Topics>);
  vi.mocked(usePatchApiV1TopicsId).mockReturnValue({ mutateAsync: patchTopic, isPending: patchPending } as unknown as ReturnType<typeof usePatchApiV1TopicsId>);
  vi.mocked(useGetApiV1TopicsDefaultPromptTemplate).mockReturnValue({ data: undefined } as unknown as ReturnType<typeof useGetApiV1TopicsDefaultPromptTemplate>);
}

function pick(which: 'pick-existing' | 'pick-draft' | 'pick-same-channel') {
  fireEvent.click(screen.getByText('topics.form.add_source_button'));
  fireEvent.click(screen.getByText(which));
}

const submit = () => fireEvent.click(screen.getByRole('button', { name: /topics.form.(create|update)/ }));

describe('TopicForm', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    givenMutations();
    createTopic.mockResolvedValue({});
    patchTopic.mockResolvedValue({});
  });

  it('creates the topic with the chosen sources and the Quick Add entries', async () => {
    render(<TopicForm onSuccess={vi.fn()} onCancel={vi.fn()} />);
    fireEvent.change(screen.getByPlaceholderText('topics.form.name_placeholder'), { target: { value: 'Science' } });
    pick('pick-existing');
    pick('pick-draft');

    submit();

    await waitFor(() => expect(createTopic).toHaveBeenCalledWith({
      data: expect.objectContaining({ name: 'Science', sourceIds: [1], newSources: [DRAFT.draft] }),
    }));
    expect(markStepComplete).toHaveBeenCalledWith(['topic', 'source']);
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['/api/v1/sources'] });
  });

  it('counts a topic with only Quick Add sources as the "add a source" onboarding step', async () => {
    render(<TopicForm onSuccess={vi.fn()} onCancel={vi.fn()} />);
    fireEvent.change(screen.getByPlaceholderText('topics.form.name_placeholder'), { target: { value: 'Science' } });
    pick('pick-draft');

    submit();

    await waitFor(() => expect(markStepComplete).toHaveBeenCalledWith(['topic', 'source']));
    expect(createTopic).toHaveBeenCalledWith({ data: expect.objectContaining({ sourceIds: [], newSources: [DRAFT.draft] }) });
  });

  it('lists an entry once and removes it by its key', () => {
    render(<TopicForm onSuccess={vi.fn()} onCancel={vi.fn()} />);
    pick('pick-draft');
    pick('pick-draft');

    expect(screen.getAllByText('New Channel')).toHaveLength(1);

    fireEvent.click(screen.getByTitle('Remove source'));
    expect(screen.queryByText('New Channel')).not.toBeInTheDocument();
  });

  it('does not list a channel twice when it is entered again through Quick Add', () => {
    render(<TopicForm onSuccess={vi.fn()} onCancel={vi.fn()} />);
    pick('pick-existing');
    pick('pick-same-channel');

    expect(screen.getAllByText('Existing Channel')).toHaveLength(1);
  });

  it('keeps the entered sources when saving fails', async () => {
    createTopic.mockRejectedValue(new Error('boom'));
    render(<TopicForm onSuccess={vi.fn()} onCancel={vi.fn()} />);
    fireEvent.change(screen.getByPlaceholderText('topics.form.name_placeholder'), { target: { value: 'Science' } });
    pick('pick-draft');

    submit();

    await waitFor(() => expect(createTopic).toHaveBeenCalled());
    expect(screen.getByText('New Channel')).toBeInTheDocument();
  });

  it('updates an existing topic through the patch mutation with its new sources', async () => {
    const topic: Topic = { id: 5, name: 'Science', enabled: true, version: 3, contentSources: [{ id: 1, name: 'Existing Channel', platform: 'YOUTUBE' }] };
    render(<TopicForm initialData={topic} onSuccess={vi.fn()} onCancel={vi.fn()} />);
    pick('pick-draft');

    submit();

    await waitFor(() => expect(patchTopic).toHaveBeenCalledWith({
      id: 5,
      data: expect.objectContaining({ sourceIds: [1], newSources: [DRAFT.draft], version: 3 }),
    }));
    expect(markStepComplete).toHaveBeenCalledWith('source');
  });

  it('draws the enabled toggle in theme colours, so its knob stays visible on the dark theme', () => {
    render(<TopicForm onSuccess={vi.fn()} onCancel={vi.fn()} />);
    const track = screen.getByTestId('topic-enabled-track');

    // the checked track is --color-action-primary (near white on dark): a hard-coded white knob disappears on it
    expect(track.className).not.toMatch(/after:bg-white|after:border-white/);
    expect(track.className).toContain('peer-checked:after:bg-[var(--color-action-primary-text)]');
    expect(track.className).toContain('after:bg-[var(--color-text-secondary)]');
  });

  it('disables saving while an update is in flight', () => {
    givenMutations({ patchPending: true });
    const topic: Topic = { id: 5, name: 'Science', enabled: true, version: 3, contentSources: [] };
    render(<TopicForm initialData={topic} onSuccess={vi.fn()} onCancel={vi.fn()} />);

    expect(screen.getByRole('button', { name: /topics.form.update/ })).toBeDisabled();
  });
});
