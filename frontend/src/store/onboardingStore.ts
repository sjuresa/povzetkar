import { create } from 'zustand';
import { useEventStore } from './eventStore';
import { getApiV1UsersMeOnboarding, patchApiV1UsersMeOnboarding } from '../api/generated/users/users';
import type { OnboardingDTO, OnboardingPatch } from '../api/generated/model';
import logger from '../util/logger';

export type OnboardingStep = 'topic' | 'source' | 'trigger' | 'read' | 'share';

export interface OnboardingSteps {
  topic: boolean;
  source: boolean;
  trigger: boolean;
  read: boolean;
  share: boolean;
}

export const ORDERED_STEPS: OnboardingStep[] = ['topic', 'source', 'trigger', 'read', 'share'];

export const STEP_ROUTES: Record<OnboardingStep, string> = {
  topic: '/topics',
  source: '/topics',
  trigger: '/topics',
  read: '/briefings',
  share: '/briefings',
};

const DEFAULT_STEPS: OnboardingSteps = {
  topic: false,
  source: false,
  trigger: false,
  read: false,
  share: false,
};

interface OnboardingState {
  /** True once the current user's state has been loaded from the server (enabled accounts only). */
  loaded: boolean;
  steps: OnboardingSteps;
  dismissed: boolean;
  collapsed: boolean;
  /** Steps marked before the state was loaded (e.g. a briefing opened in a fresh tab); applied by `load`. */
  earlySteps: OnboardingStep[];
  load: () => Promise<void>;
  markStepComplete: (stepOrSteps: OnboardingStep | OnboardingStep[], silent?: boolean) => void;
  syncFromDashboard: (activeTopics?: number, hasBriefings?: boolean) => void;
  toggleCollapsed: () => void;
  setDismissed: (dismissed: boolean) => void;
}

/** The load in flight, shared by overlapping callers (e.g. React StrictMode's double effect). */
let loadPromise: Promise<void> | null = null;

/** Sends a partial update; the store is already updated. On failure the error is logged and `onFailure` runs. */
function persist(patch: OnboardingPatch, onFailure?: () => void) {
  patchApiV1UsersMeOnboarding(patch).catch((error: unknown) => {
    logger.warn('[Onboarding] Failed to save the guide state', { error: error as Error });
    onFailure?.();
  });
}

/**
 * The dashboard onboarding guide, held per user on the server (POV-216). The store is an in-memory
 * cache, loaded for enabled accounts. Until then, marked steps are only remembered (and applied
 * silently once loaded — accounts without a guide never load, so nothing is sent for them); the other
 * actions are no-ops.
 */
export const useOnboardingStore = create<OnboardingState>()((set, get) => ({
  loaded: false,
  steps: { ...DEFAULT_STEPS },
  dismissed: false,
  collapsed: false,
  earlySteps: [],

  load: () => {
    loadPromise ??= (async () => {
      try {
        const response = await getApiV1UsersMeOnboarding();
        const state = response.data as OnboardingDTO;
        set({
          loaded: true,
          steps: { ...DEFAULT_STEPS, ...state.steps },
          dismissed: state.dismissed ?? false,
          collapsed: state.collapsed ?? false,
        });
        const early = get().earlySteps;
        set({ earlySteps: [] });
        get().markStepComplete(early, true);
      } catch (error: unknown) {
        logger.warn('[Onboarding] Failed to load the guide state', { error: error as Error });
      } finally {
        loadPromise = null;
      }
    })();
    return loadPromise;
  },

  markStepComplete: (stepOrSteps: OnboardingStep | OnboardingStep[], silent = false) => {
    const stepsToMark = Array.isArray(stepOrSteps) ? stepOrSteps : [stepOrSteps];
    if (!get().loaded) {
      set({ earlySteps: [...new Set([...get().earlySteps, ...stepsToMark])] });
      return;
    }
    const newlyCompleted = stepsToMark.filter((s) => !get().steps[s]);
    if (newlyCompleted.length === 0) return;

    const patchSteps = Object.fromEntries(newlyCompleted.map((s) => [s, true]));
    set({ steps: { ...get().steps, ...patchSteps } });
    // A lost step would stay ticked here but never reach the server — roll it back so a later mark resends it
    persist({ steps: patchSteps }, () =>
      set({ steps: { ...get().steps, ...Object.fromEntries(newlyCompleted.map((s) => [s, false])) } })
    );
    if (!silent) {
      useEventStore.getState().triggerNotificationSound('success');
    }
  },

  syncFromDashboard: (activeTopics?: number, hasBriefings?: boolean) => {
    const stepsToMark: OnboardingStep[] = [];

    // If they have briefings, they must have completed topic, source, and trigger steps
    if (hasBriefings) {
      stepsToMark.push('topic', 'source', 'trigger');
    } else if (activeTopics && activeTopics > 0) {
      stepsToMark.push('topic');
    }

    if (stepsToMark.length > 0) {
      get().markStepComplete(stepsToMark, true);
    }
  },

  toggleCollapsed: () => {
    if (!get().loaded) return;
    const collapsed = !get().collapsed;
    set({ collapsed });
    persist({ collapsed });
  },

  setDismissed: (dismissed: boolean) => {
    if (!get().loaded) return;
    set({ dismissed });
    persist({ dismissed });
  },
}));
