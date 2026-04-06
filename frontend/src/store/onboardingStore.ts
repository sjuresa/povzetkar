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
  markStepComplete: (step: OnboardingStep) => void;
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

      markStepComplete: (step: OnboardingStep) => {
        if (get().steps[step]) return; // idempotent — no duplicate sound
        set((state) => ({
          steps: { ...state.steps, [step]: true },
        }));
        useEventStore.getState().triggerNotificationSound('success');
      },

      syncFromDashboard: (activeTopics?: number, hasBriefings?: boolean) => {
        if (activeTopics && activeTopics > 0) {
          get().markStepComplete('topic');
        }
        // Step 3 fallback for returning users who triggered before installing this feature
        if (hasBriefings && !get().steps.trigger) {
          get().markStepComplete('trigger');
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
          console.warn('[Onboarding] Failed to rehydrate state, resetting to defaults.', error);
        }
      },
    }
  )
);
