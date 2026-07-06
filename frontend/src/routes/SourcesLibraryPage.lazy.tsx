import { createLazyRoute } from '@tanstack/react-router';
import { SourcesLibraryPage } from './SourcesLibraryPage';

export const Route = createLazyRoute('/authenticated/sources')({ component: SourcesLibraryPage });
