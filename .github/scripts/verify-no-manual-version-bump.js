import { execSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

/**
 * Checks whether a git diff contains modifications to versionCode or versionName.
 *
 * @param {string} diffText
 * @returns {boolean} true if diff contains disallowed version edits
 */
export function hasVersionModifications(diffText) {
  if (!diffText || typeof diffText !== 'string') {
    return false;
  }
  const lines = diffText.split('\n');
  return lines.some((line) => /^\+\s*(versionCode|versionName)\s*=/.test(line));
}

/**
 * CLI runner for PR verification in CI.
 */
export function verifyNoManualVersionBump() {
  const baseRef = process.env.BASE_REF || 'origin/main';
  try {
    const diff = execSync(`git diff ${baseRef}...HEAD -- app/build.gradle.kts`, {
      encoding: 'utf8'
    });

    if (hasVersionModifications(diff)) {
      console.error(
        '[ERROR] Manual modifications to versionCode or versionName in app/build.gradle.kts are prohibited in PRs.'
      );
      console.error(
        '[INFO] Version bumping is automated by the release workflow via conventional commits.'
      );
      process.exit(1);
    }

    console.log('[INFO] No manual version modifications detected in app/build.gradle.kts.');
  } catch (err) {
    if (err.status === 1) {
      process.exit(1);
    }
    console.warn(`[WARN] Could not verify version diff against ${baseRef}: ${err.message}`);
  }
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  verifyNoManualVersionBump();
}
