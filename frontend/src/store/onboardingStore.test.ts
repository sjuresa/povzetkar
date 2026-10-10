import { describe, it, expect, beforeEach, vi } from 'vitest';
import { useOnboardingStore } from './onboardingStore';
import { getApiV1UsersMeOnboarding, patchApiV1UsersMeOnboarding } from '../api/generated/users/users';

const mockTriggerSound = vi.fn();

// Mock eventStore to prevent audio side-effects in tests
vi.mock('./eventStore', () => ({
  useEventStore: {
    getState: () => ({
      triggerNotificationSound: mockTriggerSound,
    }),
  },
}));

vi.mock('../api/generated/users/users', () => ({
  getApiV1UsersMeOnboarding: vi.fn(),
  patchApiV1UsersMeOnboarding: vi.fn(),
}));

const EMPTY_STEPS = { topic: false, source: false, trigger: false, read: false, share: false };

function resetStore(loaded: boolean) {
  useOnboardingStore.setState({ loaded, steps: { ...EMPTY_STEPS }, dismissed: false, collapsed: false, earlySteps: [] });
}

describe('onboardingStore', () => {
  beforeEach(() => {
    vi.mocked(patchApiV1UsersMeOnboarding).mockResolvedValue({ data: {} } as never);
    resetStore(true);
  });

  describe('before the server state is loaded', () => {
    beforeEach(() => resetStore(false));

    it('changes no visible state and sends nothing', () => {
      const store = useOnboardingStore.getState();
      store.markStepComplete('topic');
      store.syncFromDashboard(3, true);
      store.toggleCollapsed();
      store.setDismissed(true);

      const { steps, dismissed, collapsed } = useOnboardingStore.getState();
      expect(steps).toEqual(EMPTY_STEPS);
      expect(dismissed).toBe(false);
      expect(collapsed).toBe(false);
      expect(patchApiV1UsersMeOnboarding).not.toHaveBeenCalled();
      expect(mockTriggerSound).not.toHaveBeenCalled();
    });
  });

  describe('load', () => {
    beforeEach(() => resetStore(false));

    it('populates the store from the server', async () => {
      vi.mocked(getApiV1UsersMeOnboarding).mockResolvedValue({
        data: { steps: { ...EMPTY_STEPS, topic: true, share: true }, dismissed: false, collapsed: true },
      } as never);

      await useOnboardingStore.getState().load();

      const { loaded, steps, collapsed } = useOnboardingStore.getState();
      expect(loaded).toBe(true);
      expect(steps).toEqual({ ...EMPTY_STEPS, topic: true, share: true });
      expect(collapsed).toBe(true);
    });

    it('applies steps marked before the state arrived (e.g. a briefing opened in a fresh tab)', async () => {
      useOnboardingStore.getState().markStepComplete('read');
      useOnboardingStore.getState().markStepComplete('topic');
      vi.mocked(getApiV1UsersMeOnboarding).mockResolvedValue({
        data: { steps: { ...EMPTY_STEPS, topic: true }, dismissed: false, collapsed: false },
      } as never);

      await useOnboardingStore.getState().load();

      expect(useOnboardingStore.getState().steps).toEqual({ ...EMPTY_STEPS, topic: true, read: true });
      expect(patchApiV1UsersMeOnboarding).toHaveBeenCalledExactlyOnceWith({ steps: { read: true } });
      expect(mockTriggerSound).not.toHaveBeenCalled();
    });

    it('sends nothing after load when the early marks are already stored', async () => {
      useOnboardingStore.getState().markStepComplete('topic');
      vi.mocked(getApiV1UsersMeOnboarding).mockResolvedValue({
        data: { steps: { ...EMPTY_STEPS, topic: true }, dismissed: false, collapsed: false },
      } as never);

      await useOnboardingStore.getState().load();

      expect(patchApiV1UsersMeOnboarding).not.toHaveBeenCalled();
    });

    it('shares one request between overlapping loads', async () => {
      vi.mocked(getApiV1UsersMeOnboarding).mockResolvedValue({
        data: { steps: { ...EMPTY_STEPS }, dismissed: false, collapsed: false },
      } as never);

      await Promise.all([useOnboardingStore.getState().load(), useOnboardingStore.getState().load()]);

      expect(getApiV1UsersMeOnboarding).toHaveBeenCalledTimes(1);
      expect(useOnboardingStore.getState().loaded).toBe(true);
    });

    it('stays unloaded when the request fails', async () => {
      vi.mocked(getApiV1UsersMeOnboarding).mockRejectedValue(new Error('403'));

      await useOnboardingStore.getState().load();

      expect(useOnboardingStore.getState().loaded).toBe(false);
    });
  });

  it('markStepComplete updates the store immediately and sends only the new steps', async () => {
    useOnboardingStore.setState({ steps: { ...EMPTY_STEPS, topic: true } });

    useOnboardingStore.getState().markStepComplete(['topic', 'source']);

    expect(useOnboardingStore.getState().steps.source).toBe(true);
    await vi.waitFor(() => expect(patchApiV1UsersMeOnboarding).toHaveBeenCalledExactlyOnceWith({ steps: { source: true } }));
  });

  it('markStepComplete plays the sound only on the first completion and does not resend', async () => {
    useOnboardingStore.getState().markStepComplete('topic');
    useOnboardingStore.getState().markStepComplete('topic');

    expect(mockTriggerSound).toHaveBeenCalledTimes(1);
    expect(mockTriggerSound).toHaveBeenCalledWith('success');
    await vi.waitFor(() => expect(patchApiV1UsersMeOnboarding).toHaveBeenCalledTimes(1));
  });

  it('markStepComplete does not play a sound in silent mode', () => {
    useOnboardingStore.getState().markStepComplete('topic', true);

    expect(useOnboardingStore.getState().steps.topic).toBe(true);
    expect(mockTriggerSound).not.toHaveBeenCalled();
  });

  it('a failed step PATCH unloads the store and resends the step on the next load', async () => {
    vi.mocked(patchApiV1UsersMeOnboarding).mockRejectedValueOnce(new Error('503'));

    useOnboardingStore.getState().markStepComplete('read');
    await vi.waitFor(() => expect(useOnboardingStore.getState().loaded).toBe(false));
    expect(useOnboardingStore.getState().earlySteps).toEqual(['read']);

    vi.mocked(getApiV1UsersMeOnboarding).mockResolvedValue({
      data: { steps: { ...EMPTY_STEPS }, dismissed: false, collapsed: false },
    } as never);
    await useOnboardingStore.getState().load();

    expect(useOnboardingStore.getState().steps.read).toBe(true);
    expect(patchApiV1UsersMeOnboarding).toHaveBeenLastCalledWith({ steps: { read: true } });
  });

  it('a failed preference PATCH unloads the store so the saved state is reloaded', async () => {
    vi.mocked(patchApiV1UsersMeOnboarding).mockRejectedValueOnce(new Error('503'));

    useOnboardingStore.getState().toggleCollapsed();

    await vi.waitFor(() => expect(useOnboardingStore.getState().loaded).toBe(false));
    expect(useOnboardingStore.getState().earlySteps).toEqual([]);
  });

  it('sends PATCHes one after another, in order', async () => {
    let releaseFirst: () => void = () => {};
    vi.mocked(patchApiV1UsersMeOnboarding)
      .mockImplementationOnce(() => new Promise((resolve) => { releaseFirst = () => resolve({ data: {} } as never); }))
      .mockResolvedValue({ data: {} } as never);

    useOnboardingStore.getState().toggleCollapsed();
    useOnboardingStore.getState().toggleCollapsed();
    await Promise.resolve();
    expect(patchApiV1UsersMeOnboarding).toHaveBeenCalledTimes(1);

    releaseFirst();
    await vi.waitFor(() => expect(patchApiV1UsersMeOnboarding).toHaveBeenCalledTimes(2));
    expect(vi.mocked(patchApiV1UsersMeOnboarding).mock.calls.map((c) => c[0])).toEqual([
      { collapsed: true },
      { collapsed: false },
    ]);
  });

  it('syncFromDashboard marks topic when activeTopics > 0', () => {
    useOnboardingStore.getState().syncFromDashboard(2, false);

    expect(useOnboardingStore.getState().steps).toEqual({ ...EMPTY_STEPS, topic: true });
    expect(mockTriggerSound).not.toHaveBeenCalled();
  });

  it('syncFromDashboard marks topic, source and trigger when hasBriefings is true', () => {
    useOnboardingStore.getState().syncFromDashboard(0, true);

    expect(useOnboardingStore.getState().steps).toEqual({ ...EMPTY_STEPS, topic: true, source: true, trigger: true });
  });

  it('syncFromDashboard marks nothing when values are zero/false', () => {
    useOnboardingStore.getState().syncFromDashboard(0, false);

    expect(useOnboardingStore.getState().steps).toEqual(EMPTY_STEPS);
    expect(patchApiV1UsersMeOnboarding).not.toHaveBeenCalled();
  });

  it('toggleCollapsed flips the flag and persists it', async () => {
    useOnboardingStore.getState().toggleCollapsed();
    expect(useOnboardingStore.getState().collapsed).toBe(true);
    await vi.waitFor(() => expect(patchApiV1UsersMeOnboarding).toHaveBeenLastCalledWith({ collapsed: true }));

    useOnboardingStore.getState().toggleCollapsed();
    expect(useOnboardingStore.getState().collapsed).toBe(false);
    await vi.waitFor(() => expect(patchApiV1UsersMeOnboarding).toHaveBeenLastCalledWith({ collapsed: false }));
  });

  it('setDismissed sets the flag and persists it', async () => {
    useOnboardingStore.getState().setDismissed(true);

    expect(useOnboardingStore.getState().dismissed).toBe(true);
    await vi.waitFor(() => expect(patchApiV1UsersMeOnboarding).toHaveBeenCalledWith({ dismissed: true }));
  });

  it('does not use localStorage', () => {
    const setItem = vi.spyOn(localStorage, 'setItem');
    const getItem = vi.spyOn(localStorage, 'getItem');

    useOnboardingStore.getState().markStepComplete('topic');
    useOnboardingStore.getState().toggleCollapsed();

    expect(setItem).not.toHaveBeenCalled();
    expect(getItem).not.toHaveBeenCalled();
  });
});
