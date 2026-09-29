import { z } from 'zod';

// The strict Content-Security-Policy has no 'unsafe-eval' (POV-214). Zod probes `new Function("")` to decide
// on JIT-compiled parsers; the probe is caught, but the browser still reports a CSP violation. jitless skips
// the probe. Read when a schema is constructed, so every module that builds Zod schemas imports this module
// first (today only TopicForm.tsx — kept out of main.tsx so zod stays in the lazy chunk). The Playwright
// project "csp" fails if a schema is built without it.
z.config({ jitless: true });
