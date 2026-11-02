import fs from 'node:fs';
import { execSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

/**
 * Sanitizes raw AI output into a clean, concise single-line release title.
 * Strips reasoning blocks (<think>...</think>), markdown formatting, enclosing quotes
 * (including unicode smart quotes), emojis, and unwanted title prefixes.
 *
 * @param {string | null | undefined} rawTitle - Raw title string from model output.
 * @returns {string} Sanitized title.
 */
export function sanitizeTitle(rawTitle) {
  if (!rawTitle || typeof rawTitle !== 'string') {
    return '';
  }

  // Strip reasoning blocks emitted by thinking/reasoning models (e.g. DeepSeek-R1)
  const strippedTitle = rawTitle.replace(/<think>[\s\S]*?<\/think>/gi, '').trim();

  // Take the first non-empty line
  const firstLine = strippedTitle
    .split('\n')
    .map((line) => line.trim())
    .find((line) => line.length > 0) || '';

  let cleaned = firstLine;

  // Remove bold/italic markdown indicators
  cleaned = cleaned.replace(/[*_~`]/g, '');

  // Strip wrapping quotes and unicode smart quotes before prefix check
  cleaned = cleaned.replace(/^["'“”‘’]+|["'“”‘’]+$/g, '').trim();

  // Remove common title prefixes and markdown headers
  cleaned = cleaned.replace(/^(?:release\s*title|title|release|headline|summary)\s*:\s*/i, '');
  cleaned = cleaned.replace(/^(?:#+|\*|-)\s*/, '');

  // Strip any lingering wrapping quotes after prefix removal
  cleaned = cleaned.replace(/^["'“”‘’]+|["'“”‘’]+$/g, '').trim();

  // Strip emojis and non-standard symbol pictographs (human-facing rule)
  cleaned = cleaned.replace(/[\p{Extended_Pictographic}\uFE0F]/gu, '');

  // Normalize internal whitespace
  cleaned = cleaned.replace(/\s+/g, ' ').trim();

  // Strip trailing punctuation
  cleaned = cleaned.replace(/[.!?:,]+$/, '').trim();

  return cleaned;
}

/**
 * Extracts a truncated snippet of release notes or commit messages for AI context.
 *
 * @param {string} notesFilePath - Path to release notes or changelog file.
 * @param {string} [tagName] - Current release tag.
 * @param {number} [maxCharacters=2000] - Maximum characters to return.
 * @returns {string} Extracted text snippet.
 */
export function extractReleaseSnippet(notesFilePath, tagName = '', maxCharacters = 2000) {
  if (notesFilePath && fs.existsSync(notesFilePath)) {
    try {
      const content = fs.readFileSync(notesFilePath, 'utf8').trim();
      if (content.length > 0) {
        return content.slice(0, maxCharacters);
      }
    } catch (readError) {
      console.warn(`[WARN] Could not read notes file (${notesFilePath}): ${readError.message}`);
    }
  }

  // Fallback to git log between tags
  if (tagName) {
    try {
      let prevTag = '';
      try {
        prevTag = execSync(`git describe --tags --abbrev=0 "${tagName}^" 2>/dev/null || true`, {
          encoding: 'utf8'
        }).trim();
      } catch {
        prevTag = '';
      }

      const revRange = prevTag ? `${prevTag}..${tagName}` : tagName;
      const rawCommits = execSync(`git log --pretty=format:"* %s" ${revRange} -n 20`, {
        encoding: 'utf8'
      }).trim();

      if (rawCommits) {
        return rawCommits.slice(0, maxCharacters);
      }
    } catch (gitError) {
      console.warn(`[WARN] Could not read git commits: ${gitError.message}`);
    }
  }

  return 'Minor updates, bug fixes, and performance improvements.';
}

/**
 * Requests a concise release title from OpenRouter chat completions.
 *
 * @param {object} params
 * @param {string} params.apiKey - OpenRouter API key.
 * @param {string} [params.model] - Model slug or router (defaults to 'openrouter/free').
 * @param {string} params.changelogSnippet - Truncated changelog content.
 * @param {string} [params.repository] - GitHub repository (owner/repo).
 * @param {number} [params.timeoutMs=15000] - Timeout in milliseconds.
 * @param {typeof fetch} [params.fetchFn=fetch] - Fetch function for testing injection.
 * @returns {Promise<string | null>} Sanitized title or null on failure.
 */
export async function requestOpenRouterTitle({
  apiKey,
  model = 'openrouter/free',
  changelogSnippet,
  repository = '',
  timeoutMs = 15000,
  fetchFn = fetch
}) {
  if (!apiKey) {
    return null;
  }

  const systemPrompt =
    'You are a software release title generator. Given release notes or a changelog, ' +
    'generate a single, concise release title (4 to 8 words, Title Case) highlighting the main feature or fix. ' +
    'Output ONLY the plain title text without quotes, brackets, prefixes, markdown, or emojis.';

  const userPrompt = `Changelog:\n${changelogSnippet}`;

  const payload = {
    model: model || 'openrouter/free',
    messages: [
      { role: 'system', content: systemPrompt },
      { role: 'user', content: userPrompt }
    ],
    max_tokens: 500,
    temperature: 0.3
  };

  const headers = {
    Authorization: `Bearer ${apiKey}`,
    'Content-Type': 'application/json',
    'HTTP-Referer': repository ? `https://github.com/${repository}` : 'https://github.com/wallet-notification-forwarder-android',
    'X-Title': 'Wallet Forwarder Release Title Generator'
  };

  const abortController = new AbortController();
  const timeoutId = setTimeout(() => abortController.abort(), timeoutMs);

  try {
    const response = await fetchFn('https://openrouter.ai/api/v1/chat/completions', {
      method: 'POST',
      headers,
      body: JSON.stringify(payload),
      signal: abortController.signal
    });

    if (!response.ok) {
      console.warn(`[WARN] OpenRouter API responded with HTTP status ${response.status}`);
      return null;
    }

    const data = await response.json();
    const rawContent = data?.choices?.[0]?.message?.content;

    if (!rawContent || typeof rawContent !== 'string') {
      console.warn('[WARN] OpenRouter response did not contain message content.');
      return null;
    }

    const cleanTitle = sanitizeTitle(rawContent);
    return cleanTitle.length >= 3 ? cleanTitle : null;
  } catch (error) {
    console.warn(`[WARN] OpenRouter request failed: ${error.message}`);
    return null;
  } finally {
    clearTimeout(timeoutId);
  }
}

/**
 * Coordinates generating release notes and requesting AI title from OpenRouter,
 * writing the resulting RELEASE_NAME to $GITHUB_ENV and release-notes.md.
 *
 * @param {object} [options={}] - Config overrides.
 * @returns {Promise<{ releaseName: string, releaseBody: string }>} Resulting release metadata.
 */
export async function generateReleaseTitle(options = {}) {
  const apiKey = options.apiKey ?? process.env.OPENROUTER_API_KEY ?? '';
  const model = options.model ?? process.env.OPENROUTER_MODEL ?? 'openrouter/free';
  const tagName = options.tagName ?? process.env.TAG_NAME ?? '';
  const repository = options.repository ?? process.env.GITHUB_REPOSITORY ?? '';
  const notesFile = options.notesFile ?? process.env.NOTES_FILE ?? 'release-notes.md';
  const outputTitleFile = options.outputTitleFile ?? process.env.OUTPUT_TITLE_FILE ?? 'release-title.txt';
  const githubEnvPath = options.githubEnvPath ?? process.env.GITHUB_ENV ?? '';
  const fetchFn = options.fetchFn ?? fetch;

  console.log(`[INFO] Generating release title for tag: ${tagName || '(none)'} with model: ${model}`);

  const snippet = extractReleaseSnippet(notesFile, tagName);
  let aiTitle = null;

  if (!apiKey) {
    console.warn('[WARN] OPENROUTER_API_KEY is not set. Falling back to tag name as title.');
  } else {
    aiTitle = await requestOpenRouterTitle({
      apiKey,
      model,
      changelogSnippet: snippet,
      repository,
      fetchFn
    });
  }

  let finalReleaseName = tagName || 'Release';

  if (aiTitle) {
    let deduplicatedAiTitle = aiTitle;
    if (tagName) {
      const escapedTag = tagName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      deduplicatedAiTitle = deduplicatedAiTitle.replace(new RegExp(`^${escapedTag}\\s*[-:]*\\s*`, 'i'), '');
    }
    deduplicatedAiTitle = deduplicatedAiTitle.replace(/^v?\d+\.\d+\.\d+(?:-[a-zA-Z0-9.]+)?\s*[-:]*\s*/i, '');

    if (deduplicatedAiTitle.length >= 3) {
      finalReleaseName = tagName ? `${tagName} - ${deduplicatedAiTitle}` : deduplicatedAiTitle;
    }
  }

  // Ensure release-notes.md exists
  let releaseBody = '';
  if (fs.existsSync(notesFile)) {
    try {
      releaseBody = fs.readFileSync(notesFile, 'utf8').trim();
    } catch {
      releaseBody = '';
    }
  }

  if (!releaseBody) {
    releaseBody = `### Changes in ${tagName || 'this release'}\n\n${snippet}\n`;
    try {
      fs.writeFileSync(notesFile, releaseBody);
      console.log(`[INFO] Wrote generated release notes to ${notesFile}`);
    } catch (notesError) {
      console.warn(`[WARN] Could not write to ${notesFile}: ${notesError.message}`);
    }
  }

  // Write to GitHub Actions environment file if present
  if (githubEnvPath) {
    try {
      fs.appendFileSync(githubEnvPath, `RELEASE_NAME=${finalReleaseName}\n`);
      console.log(`[INFO] Wrote RELEASE_NAME to GITHUB_ENV: ${finalReleaseName}`);
    } catch (envError) {
      console.warn(`[WARN] Could not write to GITHUB_ENV: ${envError.message}`);
    }
  }

  // Write to local title file
  if (outputTitleFile) {
    try {
      fs.writeFileSync(outputTitleFile, finalReleaseName);
      console.log(`[INFO] Wrote release title to ${outputTitleFile}`);
    } catch (fileError) {
      console.warn(`[WARN] Could not write to output title file: ${fileError.message}`);
    }
  }

  console.log(`[INFO] Final release title resolved: ${finalReleaseName}`);
  return { releaseName: finalReleaseName, releaseBody };
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  generateReleaseTitle().catch((fatalError) => {
    console.error(`[ERROR] Fatal error generating release title: ${fatalError.message}`);
    const fallbackTag = process.env.TAG_NAME || 'Release';
    if (process.env.GITHUB_ENV) {
      try {
        fs.appendFileSync(process.env.GITHUB_ENV, `RELEASE_NAME=${fallbackTag}\n`);
      } catch {
        // Ignore fallback write error
      }
    }
  });
}
