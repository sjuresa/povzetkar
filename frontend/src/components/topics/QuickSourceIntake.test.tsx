import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { QuickSourceIntake } from './QuickSourceIntake';
import { useGetApiV1SourcesDetailsSourceTypeIdIdentifier } from '../../api/generated/content-sources/content-sources';

// Only the validation hook exists here: Quick Add must not create sources itself (POV-219)
vi.mock('../../api/generated/content-sources/content-sources', () => ({
  useGetApiV1SourcesDetailsSourceTypeIdIdentifier: vi.fn(),
}));

// The debounced value is controllable: by default it follows the input immediately
const debounce = vi.hoisted(() => ({ frozen: undefined as string | undefined }));
vi.mock('../../hooks/useDebounce', () => ({
  useDebounce: <T,>(value: T) => (debounce.frozen ?? value) as T,
}));

type Validation = 'idle' | 'validating' | 'valid' | 'notFound' | 'unavailable';

const ERROR_STATUS: Partial<Record<Validation, number>> = { notFound: 404, unavailable: 503 };

function givenValidation(state: Validation) {
  const errorStatus = ERROR_STATUS[state];
  vi.mocked(useGetApiV1SourcesDetailsSourceTypeIdIdentifier).mockReturnValue({
    data: state === 'valid' ? { data: { name: 'Veritasium', channelId: 'UC123' } } : undefined,
    isFetching: state === 'validating',
    isSuccess: state === 'valid',
    isError: errorStatus !== undefined,
    error: errorStatus === undefined ? null : { response: { status: errorStatus } },
  } as unknown as ReturnType<typeof useGetApiV1SourcesDetailsSourceTypeIdIdentifier>);
}

function renderIntake() {
  const onSuccess = vi.fn();
  render(<QuickSourceIntake onSuccess={onSuccess} onCancel={vi.fn()} />);
  fireEvent.change(screen.getByLabelText('topics.form.quick_add.identifier_label'), { target: { value: '  @veritasium ' } });
  return { onSuccess, addButton: screen.getByRole('button', { name: /topics.form.quick_add.submit_button/ }) };
}

describe('QuickSourceIntake', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    debounce.frozen = undefined;
  });

  it('hands a confirmed channel to the form as an entry to save with the topic', () => {
    givenValidation('valid');
    const { onSuccess, addButton } = renderIntake();

    expect(addButton).toBeEnabled();
    fireEvent.click(addButton);

    // the channel ID the check resolved, not the typed handle
    expect(onSuccess).toHaveBeenCalledWith({ platform: 'YOUTUBE', sourceIdentifier: 'UC123', name: 'Veritasium' });
  });

  it.each<Validation>(['validating', 'notFound', 'unavailable'])('cannot add while the channel is %s', (state) => {
    givenValidation(state);
    const { onSuccess, addButton } = renderIntake();

    expect(addButton).toBeDisabled();
    fireEvent.click(addButton);
    expect(onSuccess).not.toHaveBeenCalled();
  });

  it('does not add an edited identifier with the result of the one checked before', () => {
    givenValidation('valid');
    const { onSuccess, addButton } = renderIntake();
    debounce.frozen = '  @veritasium ';

    fireEvent.change(screen.getByLabelText('topics.form.quick_add.identifier_label'), { target: { value: '@other' } });

    expect(addButton).toBeDisabled();
    fireEvent.click(addButton);
    expect(onSuccess).not.toHaveBeenCalled();
  });

  it('forgets a checked channel when the identifier becomes too short to check', () => {
    givenValidation('valid');
    const { addButton } = renderIntake();

    fireEvent.change(screen.getByLabelText('topics.form.quick_add.identifier_label'), { target: { value: '@ve' } });

    expect(addButton).toBeDisabled();
  });

  it('checks a channel URL as one encoded path segment', () => {
    givenValidation('valid');
    render(<QuickSourceIntake onSuccess={vi.fn()} onCancel={vi.fn()} />);

    fireEvent.change(screen.getByLabelText('topics.form.quick_add.identifier_label'), { target: { value: ' https://www.youtube.com/@veritasium ' } });

    expect(vi.mocked(useGetApiV1SourcesDetailsSourceTypeIdIdentifier)).toHaveBeenLastCalledWith(
      1, 'https%3A%2F%2Fwww.youtube.com%2F%40veritasium', expect.anything());
  });

  it('offers Blog but keeps it disabled — it is not supported yet', () => {
    givenValidation('idle');
    render(<QuickSourceIntake onSuccess={vi.fn()} onCancel={vi.fn()} />);

    expect(screen.getByRole('button', { name: 'BLOG' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'YOUTUBE' })).toBeEnabled();
  });
});
