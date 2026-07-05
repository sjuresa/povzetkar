import { createLazyRoute } from '@tanstack/react-router';
import { BriefingLibraryPage } from './BriefingLibraryPage';

export const Route = createLazyRoute('/authenticated/briefings')({ component: BriefingLibraryPage });
