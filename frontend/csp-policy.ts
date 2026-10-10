import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

// The production Content-Security-Policy lives in the backend's application.properties (POV-214, ADR-0012);
// `vite preview` sends the same value so the Playwright project "csp" tests the built SPA under it.
// Reading it here keeps a single source — a copy could drift from what production sends.
const PROPERTIES = fileURLToPath(new URL('../backend/src/main/resources/application.properties', import.meta.url));
const STRICT_CSP_KEY = 'quarkus.http.filter.csp-strict.header."Content-Security-Policy"';

/**
 * Returns a property value: `key=value`, `key = value` or `key:value`, joining `\` line continuations and
 * trimming leading whitespace as java.util.Properties does (no escape sequences — none occur in the CSP).
 */
export function readProperty(source: string, key: string): string | undefined {
  const lines = source.split(/\r?\n/);
  const escapedKey = key.replace(/[.*+?^${}()|[\]\\]/g, String.raw`\$&`);
  const keyPrefix = new RegExp(String.raw`^\s*${escapedKey}\s*[=:]\s*`);
  const start = lines.findIndex(line => keyPrefix.test(line));
  if (start < 0) return undefined;

  let value = '';
  for (let i = start; i < lines.length; i++) {
    const line = i === start ? lines[i].replace(keyPrefix, '') : lines[i].trimStart();
    if (!line.endsWith('\\')) return value + line;
    value += line.slice(0, -1);
  }
  return value;
}

export function strictCsp(): string {
  const value = readProperty(readFileSync(PROPERTIES, 'utf8'), STRICT_CSP_KEY);
  if (!value) throw new Error(`${STRICT_CSP_KEY} not found in ${PROPERTIES}`);
  return value;
}
