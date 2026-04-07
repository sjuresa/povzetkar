import { useEffect, useState } from 'react';
import { Link } from '@tanstack/react-router';
import { useTranslation } from 'react-i18next';
import { X, Sparkles, Database, Zap, BookOpen, Share2 } from 'lucide-react';
import { type OnboardingStep, STEP_ROUTES } from '../../store/onboardingStore';

interface OnboardingStepModalProps {
  step: OnboardingStep | null;
  onClose: () => void;
}

const STEP_ICONS: Record<OnboardingStep, React.ReactNode> = {
  topic: <Sparkles className="w-12 h-12" />,
  source: <Database className="w-12 h-12" />,
  trigger: <Zap className="w-12 h-12" />,
  read: <BookOpen className="w-12 h-12" />,
  share: <Share2 className="w-12 h-12" />,
};

const STEP_HOW_COUNTS: Record<OnboardingStep, number> = {
  topic: 3,
  source: 4,
  trigger: 4,
  read: 4,
  share: 4,
};

export function OnboardingStepModal({ step, onClose }: Readonly<OnboardingStepModalProps>) {
  const { t } = useTranslation();
  const [imageError, setImageError] = useState(false);

  useEffect(() => {
    setImageError(false); // Reset on step change
  }, [step]);

  useEffect(() => {
    if (!step) return;
    const handleEsc = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handleEsc);
    return () => document.removeEventListener('keydown', handleEsc);
  }, [step, onClose]);

  if (!step) return null;

  const howCount = STEP_HOW_COUNTS[step];
  const howKeys = Array.from({ length: howCount }, (_, i) => `onboarding.step.${step}.how${i + 1}`);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 animate-in fade-in duration-200"
      role="dialog"
      aria-modal="true"
      aria-labelledby="onboarding-modal-title"
    >
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-[var(--color-bg-primary)]/80 backdrop-blur-sm"
        onClick={onClose}
        aria-hidden="true"
      />

      {/* Modal panel */}
      <div className="relative z-10 w-full max-w-md glass border border-[var(--color-border-primary)] rounded-3xl shadow-xl overflow-hidden animate-in zoom-in-95 duration-200">
        {/* Illustration */}
        <div className="relative h-40 overflow-hidden bg-[var(--color-bg-secondary)] flex items-center justify-center text-[var(--color-accent-primary)]/40">
          {!imageError ? (
            <img
              src={`/onboarding/step-${step}.png`}
              alt=""
              aria-hidden="true"
              className="h-full w-full object-cover opacity-90"
              onError={() => setImageError(true)}
            />
          ) : (
            <div className="w-full h-full bg-gradient-to-br from-[var(--color-accent-primary-soft)] to-[var(--color-bg-secondary)] flex items-center justify-center">
              {STEP_ICONS[step]}
            </div>
          )}
          <div className="absolute inset-0 bg-gradient-to-t from-[var(--color-bg-primary)]/60 to-transparent" />
        </div>

        {/* Content */}
        <div className="p-6 space-y-4">
          <div className="flex items-start justify-between gap-3">
            <h2
              id="onboarding-modal-title"
              className="text-xl font-bold text-[var(--color-text-primary)]"
            >
              {t(`onboarding.step.${step}.title`)}
            </h2>
            <button
              onClick={onClose}
              className="p-1.5 rounded-xl text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-hover)] transition-colors shrink-0"
              aria-label="Close"
            >
              <X className="w-4 h-4" />
            </button>
          </div>

          <p className="text-sm text-[var(--color-text-secondary)] leading-relaxed">
            {t(`onboarding.step.${step}.desc`)}
          </p>

          {/* How-to steps */}
          <ol className="space-y-2">
            {howKeys.map((key, idx) => (
              <li key={key} className="flex items-start gap-3 text-sm">
                <span className="flex-shrink-0 w-5 h-5 rounded-full bg-[var(--color-accent-primary-soft)] text-[var(--color-accent-primary)] text-[11px] font-bold flex items-center justify-center mt-0.5">
                  {idx + 1}
                </span>
                <span className="text-[var(--color-text-secondary)]">{t(key)}</span>
              </li>
            ))}
          </ol>

          {/* Actions */}
          <div className="flex items-center gap-3 pt-2">
            <Link
              to={STEP_ROUTES[step]}
              onClick={onClose}
              className="flex-1 flex items-center justify-center px-4 py-2.5 bg-[var(--color-accent-primary)] hover:opacity-90 text-white text-interactive rounded-2xl transition-all active:scale-95 text-sm"
            >
              {t(`onboarding.step.${step}.cta`)}
            </Link>
            <button
              onClick={onClose}
              className="px-4 py-2.5 rounded-2xl border border-[var(--color-border-primary)] text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-hover)] text-interactive transition-all active:scale-95 text-sm"
            >
              {t('onboarding.close_modal')}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
