import { useState } from 'react';
import { Link } from '@tanstack/react-router';
import { useTranslation } from 'react-i18next';
import { ChevronDown, ChevronUp, Check } from 'lucide-react';
import { useShallow } from 'zustand/react/shallow';
import { useOnboardingStore, type OnboardingStep, ORDERED_STEPS, STEP_ROUTES } from '../../store/onboardingStore';
import { OnboardingStepModal } from './OnboardingStepModal';

export function OnboardingChecklist() {
  const { t } = useTranslation();
  const { steps, dismissed, collapsed, toggleCollapsed, setDismissed } = useOnboardingStore(
    useShallow((state) => ({
      steps: state.steps,
      dismissed: state.dismissed,
      collapsed: state.collapsed,
      toggleCollapsed: state.toggleCollapsed,
      setDismissed: state.setDismissed,
    }))
  );
  const [openModalStep, setOpenModalStep] = useState<OnboardingStep | null>(null);

  const completedCount = Object.values(steps).filter(Boolean).length;
  const progress = Math.round((completedCount / ORDERED_STEPS.length) * 100);
  const activeStep = ORDERED_STEPS.find((s) => !steps[s]) ?? null;
  const isComplete = progress === 100 && !dismissed;

  if (dismissed) return null;

  // Celebration state (100% complete) - Prioritize over collapsed state for special milestone
  if (isComplete) {
    return (
      <div
        id="onboarding-celebration"
        className="w-full glass border border-[var(--color-accent-primary)]/40 rounded-3xl p-8 text-center space-y-4 animate-in fade-in zoom-in-95 duration-500 bg-[var(--color-accent-primary-soft)]"
      >
        <div className="space-y-2">
          <p className="text-3xl font-black text-[var(--color-text-primary)]">{t('onboarding.celebration_title')}</p>
          <p className="text-[var(--color-text-secondary)]">{t('onboarding.celebration_subtitle')}</p>
        </div>
        <div className="flex justify-center gap-1">
          {ORDERED_STEPS.map((s) => (
            <Check key={s} className="w-5 h-5 text-[var(--color-accent-primary)]" />
          ))}
        </div>
        <div className="pt-2">
          <button
            onClick={() => setDismissed(true)}
            id="onboarding-celebration-done"
            className="bg-[var(--color-accent-primary)] text-white px-6 py-2.5 rounded-2xl text-interactive hover:opacity-90 active:scale-95 transition-all shadow-lg shadow-[var(--color-accent-primary)]/20"
          >
            {t('onboarding.celebration_done')}
          </button>
        </div>
      </div>
    );
  }

  // Collapsed pill state
  if (collapsed) {
    return (
      <button
        id="onboarding-checklist-pill"
        onClick={toggleCollapsed}
        className="flex items-center gap-2 px-3 py-1.5 rounded-full border border-[var(--color-border-primary)] glass hover:bg-[var(--color-surface-hover)] transition-all text-sm text-interactive text-[var(--color-text-secondary)] animate-in fade-in duration-300"
        aria-label={`${t('onboarding.expand_label')} — ${t('onboarding.progress_label', { percent: progress })}`}
      >
        <span className="w-2 h-2 rounded-full bg-[var(--color-accent-primary)] animate-pulse" />
        <span className="text-[var(--color-text-primary)] text-interactive">{t('onboarding.expand_label')}</span>
        <span className="text-xs opacity-60">·</span>
        <span className="text-xs">{t('onboarding.progress_label', { percent: progress })}</span>
        <ChevronDown className="w-3 h-3 ml-1 opacity-60" />
      </button>
    );
  }

  // Full expanded checklist
  return (
    <>
      <div
        id="onboarding-checklist"
        className="w-full glass border border-[var(--color-border-primary)]/60 rounded-3xl overflow-hidden animate-in fade-in slide-in-from-top-2 duration-500"
      >
        {/* Header */}
        <div className="flex items-center justify-between px-6 pt-5 pb-4">
          <div className="flex-1 min-w-0 mr-4">
            <h3 className="text-sm font-bold text-[var(--color-text-primary)] uppercase tracking-wider mb-1.5">
              {t('onboarding.title')}
            </h3>
            {/* Progress bar */}
            <div className="flex items-center gap-4">
              <div className="flex-1 h-3 rounded-full bg-[var(--color-bg-secondary)] border border-[var(--color-border-primary)] shadow-inner overflow-hidden">
                <div
                  className="h-full rounded-full bg-[var(--color-accent-primary)] transition-all duration-1000 ease-in-out shadow-[0_0_12px_var(--color-glow)] border-r-2 border-[var(--color-text-secondary)]/20"
                  style={{ 
                    width: `${progress}%`,
                  }}
                  role="progressbar"
                  aria-valuenow={progress}
                  aria-valuemin={0}
                  aria-valuemax={100}
                />
              </div>
              <span className="text-sm font-black text-[var(--color-accent-primary)] whitespace-nowrap min-w-[3rem] text-right">
                {progress}%
              </span>
            </div>
          </div>
          <button
            onClick={toggleCollapsed}
            id="onboarding-collapse-btn"
            className="p-1.5 rounded-xl text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-hover)] transition-colors shrink-0"
            aria-label={t('onboarding.collapse')}
          >
            <ChevronUp className="w-4 h-4" />
          </button>
        </div>

        {/* Step list */}
        <div className="px-3 pb-3 space-y-1">
          {ORDERED_STEPS.map((step, idx) => {
            const isStepComplete = steps[step];
            const isActive = step === activeStep;

            return (
              <div
                key={step}
                id={`onboarding-step-${step}`}
                className={`rounded-2xl px-4 py-3 transition-all ${
                  isActive
                    ? 'bg-[var(--color-accent-primary-soft)] border border-[var(--color-accent-primary)]/20'
                    : isStepComplete
                    ? 'opacity-60'
                    : 'hover:bg-[var(--color-surface-hover)]'
                }`}
              >
                <div className="flex items-center gap-3">
                  {/* Step icon */}
                  <div
                    className={`w-6 h-6 rounded-full flex items-center justify-center shrink-0 transition-all ${
                      isStepComplete
                        ? 'bg-[var(--color-accent-primary)] text-white'
                        : isActive
                        ? 'border-2 border-[var(--color-accent-primary)] text-[var(--color-accent-primary)]'
                        : 'border-2 border-[var(--color-border-primary)] text-[var(--color-text-secondary)]'
                    }`}
                  >
                    {isStepComplete ? (
                      <Check className="w-3.5 h-3.5" />
                    ) : (
                      <span className="text-[10px] font-bold">{idx + 1}</span>
                    )}
                  </div>

                  {/* Step title */}
                  <span
                    className={`flex-1 text-sm font-semibold ${
                      isStepComplete
                        ? 'line-through text-[var(--color-text-secondary)]'
                        : 'text-[var(--color-text-primary)]'
                    }`}
                  >
                    {t(`onboarding.step.${step}.title`)}
                  </span>

                  {/* Actions (only on incomplete steps) */}
                  {!isStepComplete && (
                    <div className="flex items-center gap-2 shrink-0">
                      <button
                        onClick={() => setOpenModalStep(step)}
                        id={`onboarding-step-${step}-how`}
                        className="text-xs text-[var(--color-accent-primary)] hover:underline text-interactive"
                      >
                        {t('onboarding.learn_more')}
                      </button>
                      <Link
                        to={STEP_ROUTES[step]}
                        id={`onboarding-step-${step}-cta`}
                        className={`text-xs text-interactive px-3 py-1 rounded-xl transition-all active:scale-95 ${
                          isActive
                            ? 'bg-[var(--color-accent-primary)] text-white hover:opacity-90'
                            : 'border border-[var(--color-border-primary)] text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-hover)]'
                        }`}
                      >
                        {t(`onboarding.step.${step}.cta`)}
                      </Link>
                    </div>
                  )}
                </div>

                {/* Active step description */}
                {isActive && (
                  <p className="mt-2 ml-9 text-xs text-[var(--color-text-secondary)] leading-relaxed animate-in fade-in slide-in-from-top-1 duration-300">
                    {t(`onboarding.step.${step}.desc`)}
                  </p>
                )}
              </div>
            );
          })}
        </div>
      </div>

      <OnboardingStepModal step={openModalStep} onClose={() => setOpenModalStep(null)} />
    </>
  );
}
