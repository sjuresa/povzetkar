// Large landing-page call-to-action buttons. Colours are separate so a variant never carries two bg-* classes
// (with Tailwind the stylesheet order, not the class order, would decide which one wins).
const CTA_BASE =
  'inline-flex items-center gap-2 px-6 py-3.5 rounded-xl md:rounded-2xl font-semibold transition-colors active:scale-[0.98]';

/** On the page background: the action colour. */
export const CTA_PRIMARY = `${CTA_BASE} bg-[var(--color-action-primary)] hover:bg-[var(--color-action-hover)] text-[var(--color-action-primary-text)]`;

/** On an action-coloured band: the page colours. */
export const CTA_INVERTED = `${CTA_BASE} bg-[var(--color-background-base)] hover:bg-[var(--color-background-alt)] text-[var(--color-text-primary)]`;
