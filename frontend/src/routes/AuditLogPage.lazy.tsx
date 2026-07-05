import { createLazyRoute } from '@tanstack/react-router';
import { AuditLogPage } from './AuditLogPage';

export const Route = createLazyRoute('/authenticated/admin/audit-log')({ component: AuditLogPage });
