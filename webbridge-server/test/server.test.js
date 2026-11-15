import test from 'node:test';
import assert from 'node:assert/strict';
import { discoverBrowserPath } from '../src/browser.js';
import { classifyRequest } from '../src/classifier.js';
import { selectPlatform, verifyRoute, getSupportedTaskTypes } from '../src/router.js';
import { CredentialVault } from '../src/credentials.js';
import { packageOutput } from '../src/output.js';

test('Browser discovery executes cleanly without throwing', () => {
  const browserPath = discoverBrowserPath();
  assert.ok(browserPath === null || typeof browserPath === 'string');
});

test('Classifier properly categorizes requests', () => {
  assert.equal(classifyRequest('generate an image of a cybernetic cat'), 'IMAGE_GENERATION');
  assert.equal(classifyRequest('create an audio voiceover for this video'), 'AUDIO_GENERATION');
  assert.equal(classifyRequest('make a 10 second video clip of waves'), 'VIDEO_GENERATION');
  assert.equal(classifyRequest('search the web for latest quantum computing news'), 'WEB_SEARCH');
  assert.equal(classifyRequest('write a python script to parse logs'), 'CODE_GENERATION');
  assert.equal(classifyRequest('hello how are you today'), 'GENERAL_TEXT');
});

test('Router selects appropriate platform', () => {
  const vault = new CredentialVault();
  const imagePlatform = selectPlatform('IMAGE_GENERATION', 'auto', vault);
  assert.ok(imagePlatform && imagePlatform.name);

  const searchPlatform = selectPlatform('WEB_SEARCH', 'auto', vault);
  assert.ok(searchPlatform && searchPlatform.name);

  const types = getSupportedTaskTypes();
  assert.ok(types.includes('IMAGE_GENERATION'));
  assert.ok(types.includes('WEB_SEARCH'));
});

test('CredentialVault manages session credentials securely', () => {
  const vault = new CredentialVault();
  assert.equal(vault.has('test_platform'), false);
  vault.set('test_platform', 'user@example.com', 'secret_token_123');
  assert.equal(vault.has('test_platform'), true);
  const creds = vault.get('test_platform');
  assert.equal(creds.email, 'user@example.com');
  assert.equal(creds.password, 'secret_token_123');
  vault.remove('test_platform');
  assert.equal(vault.has('test_platform'), false);
});

test('Output packager includes metadata and failover context', () => {
  const extracted = { type: 'text', text: 'Generated answer' };
  const packaged = packageOutput(extracted, {
    taskType: 'GENERAL_TEXT',
    platformName: 'ChatGPT',
    platformUrl: 'https://chatgpt.com',
    userPrompt: 'Tell me a joke',
    triggeredBy: 'app_watchdog',
    failoverContext: {
      primary_ai_status: 'timeout',
      silence_duration_seconds: 15.0
    },
    timeElapsed: 250
  });

  assert.equal(packaged.status, 'SUCCESS');
  assert.equal(packaged.mode, 'FAILOVER');
  assert.equal(packaged.site_used, 'ChatGPT');
  assert.equal(packaged.output.text_content, 'Generated answer');
  assert.equal(packaged.metadata.time_elapsed_ms, 250);
  assert.ok(packaged.failover_metadata);
  assert.equal(packaged.failover_metadata.primary_ai_status, 'timeout');
});
