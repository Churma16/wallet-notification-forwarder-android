import fs from 'node:fs';
import { execSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

/**
 * Parses semver string into [major, minor, patch] numbers.
 *
 * @param {string} versionString
 * @returns {{ major: number, minor: number, patch: number }}
 */
export function parseSemVer(versionString) {
  const clean = versionString.replace(/^v/, '').trim();
  const parts = clean.split('.').map((p) => parseInt(p, 10) || 0);
  return {
    major: parts[0] || 0,
    minor: parts[1] || 0,
    patch: parts[2] || 0
  };
}

/**
 * Computes next semver based on bump type.
 *
 * @param {string} currentVersion
 * @param {'patch' | 'minor' | 'major'} bumpType
 * @returns {string}
 */
export function incrementVersion(currentVersion, bumpType) {
  const { major, minor, patch } = parseSemVer(currentVersion);
  switch (bumpType) {
    case 'major':
      return `${major + 1}.0.0`;
    case 'minor':
      return `${major}.${minor + 1}.0`;
    case 'patch':
    default:
      return `${major}.${minor}.${patch + 1}`;
  }
}

/**
 * Pure function to detect bump type from commit subject strings.
 *
 * @param {string[]} subjects
 * @returns {'patch' | 'minor' | 'major'}
 */
export function detectBumpTypeFromSubjects(subjects) {
  if (!Array.isArray(subjects) || subjects.length === 0) {
    return 'patch';
  }

  let hasMinor = false;
  for (const subject of subjects) {
    if (
      subject.includes('BREAKING CHANGE') ||
      /^[a-z]+(?:\([^)]+\))?!/i.test(subject)
    ) {
      return 'major';
    }
    if (/^feat(?:\([^)]+\))?:/i.test(subject)) {
      hasMinor = true;
    }
  }

  return hasMinor ? 'minor' : 'patch';
}

/**
 * Detects bump type from commit messages since last tag.
 *
 * @param {string} [prevTag]
 * @returns {'patch' | 'minor' | 'major'}
 */
export function detectBumpTypeFromCommits(prevTag = '') {
  try {
    const revRange = prevTag ? `${prevTag}..HEAD` : 'HEAD';
    const rawCommits = execSync(`git log --no-merges --pretty=format:"%s" ${revRange}`, {
      encoding: 'utf8'
    }).trim();

    if (!rawCommits) {
      return 'patch';
    }

    const lines = rawCommits.split('\n');
    return detectBumpTypeFromSubjects(lines);
  } catch {
    return 'patch';
  }
}

/**
 * Updates app/build.gradle.kts with new versionCode and versionName.
 *
 * @param {string} gradleFilePath
 * @param {number} nextCode
 * @param {string} nextVersion
 */
export function updateGradleVersion(gradleFilePath, nextCode, nextVersion) {
  const content = fs.readFileSync(gradleFilePath, 'utf8');

  let updated = content.replace(
    /versionCode\s*=\s*\d+/,
    `versionCode = ${nextCode}`
  );
  updated = updated.replace(
    /versionName\s*=\s*"[^"]+"/,
    `versionName = "${nextVersion}"`
  );

  fs.writeFileSync(gradleFilePath, updated);
}

/**
 * Appends new version entry to CHANGELOG.md.
 *
 * @param {string} changelogPath
 * @param {string} nextVersion
 * @param {string} commitsText
 */
export function updateChangelog(changelogPath, nextVersion, commitsText) {
  const today = new Date().toISOString().split('T')[0];
  const newSection = `## [${nextVersion}] - ${today}\n\n${commitsText}\n\n`;

  let existing = '';
  if (fs.existsSync(changelogPath)) {
    existing = fs.readFileSync(changelogPath, 'utf8');
  } else {
    existing = '# Changelog\n\nAll notable changes to this project will be documented in this file.\n\n';
  }

  const updated = existing.replace(/^(# Changelog\s*\n\n)/i, `$1${newSection}`);
  fs.writeFileSync(changelogPath, updated.length > existing.length ? updated : `${newSection}${existing}`);
}

/**
 * Main coordinator for version bump and changelog generation.
 */
export async function bumpVersion() {
  const releaseTypeInput = (process.env.RELEASE_TYPE || 'auto').toLowerCase();
  const dryRun = process.env.DRY_RUN === 'true';
  const gradlePath = process.env.GRADLE_FILE || 'app/build.gradle.kts';
  const changelogPath = process.env.CHANGELOG_FILE || 'CHANGELOG.md';
  const notesPath = process.env.NOTES_FILE || 'release-notes.md';

  if (!fs.existsSync(gradlePath)) {
    throw new Error(`Gradle build file not found at: ${gradlePath}`);
  }

  const gradleContent = fs.readFileSync(gradlePath, 'utf8');
  const codeMatch = gradleContent.match(/versionCode\s*=\s*(\d+)/);
  const versionMatch = gradleContent.match(/versionName\s*=\s*"([^"]+)"/);

  if (!codeMatch || !versionMatch) {
    throw new Error('Could not parse versionCode or versionName from app/build.gradle.kts');
  }

  const currentCode = parseInt(codeMatch[1], 10);
  const currentVersion = versionMatch[1];

  let prevTag = '';
  try {
    // Exclude snapshot tags so git describe finds the last official stable release tag
    prevTag = execSync(
      'git describe --tags --abbrev=0 --match="v[0-9]*.[0-9]*.[0-9]*" --exclude="*snapshot*" 2>/dev/null || true',
      { encoding: 'utf8' }
    ).trim();
  } catch {
    prevTag = '';
  }

  let bumpType = releaseTypeInput;
  if (bumpType === 'auto') {
    bumpType = detectBumpTypeFromCommits(prevTag);
  }

  const nextVersion = incrementVersion(currentVersion, bumpType);
  const nextCode = currentCode + 1;
  const nextTag = `v${nextVersion}`;

  console.log(`[INFO] Current version: ${currentVersion} (code: ${currentCode}), previous stable tag: ${prevTag || '(none)'}`);
  console.log(`[INFO] Bump type: ${bumpType} -> Next version: ${nextVersion} (code: ${nextCode})`);

  // Collect commits for release notes and changelog
  let commitLines = [];
  try {
    const revRange = prevTag ? `${prevTag}..HEAD` : 'HEAD';
    const log = execSync(`git log --no-merges --pretty=format:"* %s" ${revRange}`, {
      encoding: 'utf8'
    }).trim();
    if (log) {
      commitLines = log
        .split('\n')
        .filter((line) => !line.includes('chore(release):') && !line.includes('chore(main):'));
    }
  } catch {
    commitLines = ['* Maintenance release and stability updates'];
  }

  const commitsSummary = commitLines.length > 0
    ? commitLines.join('\n')
    : '* Maintenance release and stability updates';

  // Write release notes for action-gh-release
  const releaseNotesContent = `### Changes in ${nextTag}\n\n${commitsSummary}\n`;
  fs.writeFileSync(notesPath, releaseNotesContent);
  console.log(`[INFO] Wrote ${notesPath}`);

  if (dryRun) {
    console.log('[INFO] Dry run enabled. Skipping file modifications and git tag.');
    return { nextVersion, nextCode, nextTag };
  }

  // Update gradle and changelog
  updateGradleVersion(gradlePath, nextCode, nextVersion);
  console.log(`[INFO] Updated ${gradlePath} to ${nextVersion} (${nextCode})`);

  updateChangelog(changelogPath, nextVersion, commitsSummary);
  console.log(`[INFO] Updated ${changelogPath}`);

  // Commit and tag
  execSync(`git add ${gradlePath} ${changelogPath}`);
  execSync(`git commit -m "chore(release): ${nextTag}"`);
  execSync(`git tag -a "${nextTag}" -m "Release ${nextTag}"`);
  console.log(`[INFO] Created git commit and tag ${nextTag}`);

  if (process.env.GITHUB_ENV) {
    fs.appendFileSync(process.env.GITHUB_ENV, `RELEASE_TAG=${nextTag}\n`);
    fs.appendFileSync(process.env.GITHUB_ENV, `VERSION_NAME=${nextVersion}\n`);
    fs.appendFileSync(process.env.GITHUB_ENV, `VERSION_CODE=${nextCode}\n`);
  }

  return { nextVersion, nextCode, nextTag };
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  bumpVersion().catch((err) => {
    console.error(`[ERROR] Bump version failed: ${err.message}`);
    process.exit(1);
  });
}
