import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import { useEventStore } from './eventStore';

export type OnboardingStep = 'topic' | 'source' | 'trigger' | 'read' | 'share';

export interface OnboardingSteps {
  topic: boolean;
  source: boolean;
  trigger: boolean;
  read: boolean;
  share: boolean;
}

const DEFAULT_STEPS: OnboardingSteps = {
  topic: false,
  source: false,
  trigger: false,
  read: false,
  share: false,
};

interface OnboardingState {
  steps: OnboardingSteps;
  dismissed: boolean;
  collapsed: boolean;
  markStepComplete: (step: OnboardingStep, silent?: boolean) => void;
  syncFromDashboard: (activeTopics?: number, hasBriefings?: boolean) => void;
  toggleCollapsed: () => void;
  setDismissed: (dismissed: boolean) => void;
}

export const useOnboardingStore = create<OnboardingState>()(
  persist(
    (set, get) => ({
      steps: { ...DEFAULT_STEPS },
      dismissed: false,
      collapsed: false,

      markStepComplete: (step: OnboardingStep, silent = false) => {
        if (get().steps[step]) return; // idempotent — no duplicate sound
        set((state) => ({
          steps: { ...state.steps, [step]: true },
        }));
        if (!silent) {
          useEventStore.getState().triggerNotificationSound('success');
        }
      },

      syncFromDashboard: (activeTopics?: number, hasBriefings?: boolean) => {
        // If they have briefings, they must have completed topic, source, and trigger steps
        if (hasBriefings) {
          get().markStepComplete('topic', true);
          get().markStepComplete('source', true);
          get().markStepComplete('trigger', true);
        } else if (activeTopics && activeTopics > 0) {
          get().markStepComplete('topic', true);
        }
      },

      toggleCollapsed: () => set((state) => ({ collapsed: !state.collapsed })),

      setDismissed: (dismissed: boolean) => set({ dismissed }),
    }),
    {
      name: 'povzetkar_onboarding',
      storage: createJSONStorage(() => localStorage),
      onRehydrateStorage: () => (state, error) => {
        if (error || !state) {
          // Corruption fallback: reset is handled by Zustand returning default initial state
          // TODO: In production, report this to an error tracking service (e.g. Sentry)
          console.error('[Onboarding] Critical error during state rehydration. Falling back to defaults.', { error, state });
        }
      },
    }
  )
);
