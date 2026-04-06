import { describe, it, expect, beforeEach, vi } from 'vitest';
import { useOnboardingStore } from './onboardingStore';

const mockTriggerSound = vi.fn();

// Mock eventStore to prevent audio side-effects in tests
vi.mock('./eventStore', () => ({
  useEventStore: {
    getState: () => ({
      triggerNotificationSound: mockTriggerSound,
    }),
  },
}));

function resetStore() {
  useOnboardingStore.setState({
    steps: { topic: false, source: false, trigger: false, read: false, share: false },
    dismissed: false,
    collapsed: false,
  });
}

describe('onboardingStore', () => {
  beforeEach(() => {
    resetStore();
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('starts with all steps incomplete and not dismissed or collapsed', () => {
    const { steps, dismissed, collapsed } = useOnboardingStore.getState();
    expect(steps).toEqual({ topic: false, source: false, trigger: false, read: false, share: false });
    expect(dismissed).toBe(false);
    expect(collapsed).toBe(false);
  });

  it('markStepComplete sets the step to true', () => {
    useOnboardingStore.getState().markStepComplete('topic');
    expect(useOnboardingStore.getState().steps.topic).toBe(true);
  });

  it('markStepComplete triggers sound on first completion', () => {
    useOnboardingStore.getState().markStepComplete('topic');
    expect(mockTriggerSound).toHaveBeenCalledTimes(1);
    expect(mockTriggerSound).toHaveBeenCalledWith('success');
  });

  it('markStepComplete is idempotent — does not re-trigger sound on second call', () => {
    useOnboardingStore.getState().markStepComplete('topic');
    useOnboardingStore.getState().markStepComplete('topic');
    expect(mockTriggerSound).toHaveBeenCalledTimes(1);
  });

  it('syncFromDashboard marks topic when activeTopics > 0', () => {
    useOnboardingStore.getState().syncFromDashboard(2, false);
    expect(useOnboardingStore.getState().steps.topic).toBe(true);
    expect(useOnboardingStore.getState().steps.trigger).toBe(false);
  });

  it('syncFromDashboard marks trigger fallback when hasBriefings is true', () => {
    useOnboardingStore.getState().syncFromDashboard(0, true);
    expect(useOnboardingStore.getState().steps.trigger).toBe(true);
  });

  it('syncFromDashboard marks nothing when values are zero/false', () => {
    useOnboardingStore.getState().syncFromDashboard(0, false);
    const { steps } = useOnboardingStore.getState();
    expect(Object.values(steps).every((v) => v === false)).toBe(true);
  });

  it('toggleCollapsed flips the collapsed flag', () => {
    expect(useOnboardingStore.getState().collapsed).toBe(false);
    useOnboardingStore.getState().toggleCollapsed();
    expect(useOnboardingStore.getState().collapsed).toBe(true);
    useOnboardingStore.getState().toggleCollapsed();
    expect(useOnboardingStore.getState().collapsed).toBe(false);
  });

  it('setDismissed sets the dismissed flag', () => {
    useOnboardingStore.getState().setDismissed(true);
    expect(useOnboardingStore.getState().dismissed).toBe(true);
    useOnboardingStore.getState().setDismissed(false);
    expect(useOnboardingStore.getState().dismissed).toBe(false);
  });
});
