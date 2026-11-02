import test from 'node:test';
import assert from 'node:assert';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {
  parseSemVer,
  incrementVersion,
  updateGradleVersion,
  updateChangelog
} from '../.github/scripts/bump-version.js';
import {
  sanitizeTitle,
  extractReleaseSnippet,
  requestOpenRouterTitle,
  generateReleaseTitle
} from '../.github/scripts/generate-release-title.js';

test('parseSemVer correctly splits versions', () => {
  assert.deepStrictEqual(parseSemVer('1.0.7'), { major: 1, minor: 0, patch: 7 });
  assert.deepStrictEqual(parseSemVer('v2.4.1'), { major: 2, minor: 4, patch: 1 });
});

test('incrementVersion calculates semver bumps', () => {
  assert.strictEqual(incrementVersion('1.0.7', 'patch'), '1.0.8');
  assert.strictEqual(incrementVersion('1.0.7', 'minor'), '1.1.0');
  assert.strictEqual(incrementVersion('1.0.7', 'major'), '2.0.0');
});

test('updateGradleVersion modifies versionCode and versionName in build.gradle.kts', () => {
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'gradle-test-'));
  const tempGradleFile = path.join(tempDir, 'build.gradle.kts');

  const initialContent = `
    defaultConfig {
        applicationId = "codes.churma.walletforwarder"
        minSdk = 26
        targetSdk = 34
        versionCode = 8
        versionName = "1.0.7"
    }
  `;
  fs.writeFileSync(tempGradleFile, initialContent);

  updateGradleVersion(tempGradleFile, 9, '1.0.8');

  const updatedContent = fs.readFileSync(tempGradleFile, 'utf8');
  assert.match(updatedContent, /versionCode = 9/);
  assert.match(updatedContent, /versionName = "1\.0\.8"/);

  fs.rmSync(tempDir, { recursive: true, force: true });
});

test('updateChangelog prepends new release entry', () => {
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'changelog-test-'));
  const tempChangelog = path.join(tempDir, 'CHANGELOG.md');

  updateChangelog(tempChangelog, '1.0.8', '* feat: new forwarder logic');

  const content = fs.readFileSync(tempChangelog, 'utf8');
  assert.match(content, /## \[1\.0\.8\]/);
  assert.match(content, /\* feat: new forwarder logic/);

  fs.rmSync(tempDir, { recursive: true, force: true });
});

test('sanitizeTitle strips reasoning tags, quotes, prefixes, and emojis', () => {
  const inputWithThink = '<think>\nThinking about changes...\n</think>\n"Release Title: Realtime Forwarding 🚀"';
  const result = sanitizeTitle(inputWithThink);

  assert.strictEqual(result, 'Realtime Forwarding');
  assert.doesNotMatch(result, /<think>/);
  assert.doesNotMatch(result, /🚀/);
  assert.doesNotMatch(result, /Release Title:/i);
});

test('sanitizeTitle supports unicode smart quotes', () => {
  assert.strictEqual(sanitizeTitle('“Material UI Polish”'), 'Material UI Polish');
  assert.strictEqual(sanitizeTitle('‘In-Place App Update’'), 'In-Place App Update');
});

test('requestOpenRouterTitle handles missing key and mock responses', async () => {
  const noKey = await requestOpenRouterTitle({ apiKey: '', changelogSnippet: 'test' });
  assert.strictEqual(noKey, null);

  const mockFetch = async () => ({
    ok: true,
    json: async () => ({
      choices: [{ message: { content: '"Push Notification Forwarding"' } }]
    })
  });

  const title = await requestOpenRouterTitle({
    apiKey: 'mock-key',
    changelogSnippet: 'feat: add push notifications',
    fetchFn: mockFetch
  });

  assert.strictEqual(title, 'Push Notification Forwarding');
});

test('generateReleaseTitle formats and deduplicates release titles', async () => {
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'title-gen-test-'));
  const notesPath = path.join(tempDir, 'notes.md');
  const titlePath = path.join(tempDir, 'title.txt');
  fs.writeFileSync(notesPath, '* feat: test feature');

  const mockFetch = async () => ({
    ok: true,
    json: async () => ({
      choices: [{ message: { content: 'v1.0.8 - Realtime Sync Engine' } }]
    })
  });

  const result = await generateReleaseTitle({
    apiKey: 'mock-key',
    tagName: 'v1.0.8',
    notesFile: notesPath,
    outputTitleFile: titlePath,
    fetchFn: mockFetch
  });

  assert.strictEqual(result.releaseName, 'v1.0.8 - Realtime Sync Engine');
  assert.strictEqual(fs.readFileSync(titlePath, 'utf8'), 'v1.0.8 - Realtime Sync Engine');

  fs.rmSync(tempDir, { recursive: true, force: true });
});
