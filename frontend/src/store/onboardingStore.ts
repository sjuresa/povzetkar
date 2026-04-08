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
  steps: OnboardingSteps;
  dismissed: boolean;
  collapsed: boolean;
  markStepComplete: (stepOrSteps: OnboardingStep | OnboardingStep[], silent?: boolean) => void;
  syncFromDashboard: (activeTopics?: number, hasBriefings?: boolean) => void;
  toggleCollapsed: () => void;
  setDismissed: (dismissed: boolean) => void;
  reset: () => void;
}

const initialState = {
  steps: { ...DEFAULT_STEPS },
  dismissed: false,
  collapsed: false,
};

export const useOnboardingStore = create<OnboardingState>()(
  persist(
    (set, get) => ({
      ...initialState,

      markStepComplete: (stepOrSteps: OnboardingStep | OnboardingStep[], silent = false) => {
        const stepsToMark = Array.isArray(stepOrSteps) ? stepOrSteps : [stepOrSteps];
        const currentSteps = get().steps;
        const newSteps = { ...currentSteps };
        let changed = false;

        stepsToMark.forEach((s) => {
          if (!newSteps[s]) {
            newSteps[s] = true;
            changed = true;
          }
        });

        if (changed) {
          set({ steps: newSteps });
          if (!silent) {
            useEventStore.getState().triggerNotificationSound('success');
          }
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

      toggleCollapsed: () => set((state) => ({ collapsed: !state.collapsed })),

      setDismissed: (dismissed: boolean) => set({ dismissed }),

      reset: () => {
        set(initialState);
        // Clean out LocalStorage to prevent re-corruption
        useOnboardingStore.persist.clearStorage();
      },
    }),
    {
      name: 'povzetkar_onboarding',
      storage: createJSONStorage(() => localStorage),
      onRehydrateStorage: (_state) => (rehydratedState, error) => {
        if (error || (rehydratedState && !rehydratedState.steps)) {
          // Corruption fallback: reset and report
          console.error('[Onboarding] Critical error during state rehydration. Resetting to defaults.', { error });
          
          // Reset synchronously to ensure consistency before the first render
          useOnboardingStore.getState().reset();
        }
      },
    }
  )
);
