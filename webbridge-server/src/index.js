import express from 'express';
import { v4 as uuidv4 } from 'uuid';
import 'dotenv/config';
import { classifyRequest } from './classifier.js';
import { selectPlatform, verifyRoute } from './router.js';
import { CredentialVault } from './credentials.js';
import { BrowserManager } from './browser.js';
import { packageOutput } from './output.js';
import { logger } from './utils.js';

const app = express();
app.use(express.json({ limit: '50mb' }));

const PORT = parseInt(process.env.PORT || '3000');
const HOST = process.env.HOST || '0.0.0.0';

const tasks = new Map();
const vault = new CredentialVault();
const browserManager = new BrowserManager();

function taskResponse(task) {
  return {
    task_id: task.task_id,
    status: task.status,
    mode: task.mode || 'NORMAL',
    site_used: task.site_used || '',
    target_site_used: task.site_used || '',
    capability: task.capability || '',
    output_type: task.output_type || 'text',
    output: task.output || null,
    metadata: task.metadata || null,
    error: task.error || null
  };
}

app.post('/task', async (req, res) => {
  try {
    const body = req.body;
    const taskId = body.task_id || uuidv4();

    let task = {
      task_id: taskId,
      status: 'PENDING',
      mode: 'NORMAL',
      site_used: '',
      capability: '',
      output_type: 'text',
      output: null,
      metadata: null,
      error: null,
      created_at: Date.now()
    };
    tasks.set(taskId, task);

    logger.info(`Task ${taskId} received: type=${body.task_type}, site=${body.target_site}, prompt=${body.user_prompt?.substring(0, 60)}...`);

    const userPrompt = body.user_prompt || body.prompt || '';
    const requestedType = body.task_type || '';
    const requestedSite = body.target_site || body.preferred_site || 'auto';
    const outputFormat = body.output_format || body.constraints?.output_format || 'text';

    let taskType = requestedType;
    if (taskType === 'auto' || !taskType) {
      taskType = classifyRequest(userPrompt);
      logger.info(`Task ${taskId}: classified as ${taskType}`);
    }

    if (taskType.startsWith('DOCUMENT_')) {
      task.status = 'SUCCESS';
      task.output = {
        text_content: JSON.stringify({ handled_by: 'host', task_type: taskType }),
        description: `Document creation (${taskType}) routed back to host application`
      };
      task.output_type = 'text';
      return res.json(taskResponse(task));
    }

    const platform = selectPlatform(taskType, requestedSite, vault);
    logger.info(`Task ${taskId}: routed to ${platform.name} (${platform.url})`);

    task.site_used = platform.name;
    task.capability = taskType;

    if (platform.requires_login && !vault.has(platform.key)) {
      task.status = 'AWAITING_CREDENTIALS';
      task.needs_user_action = {
        reason: `Login required for ${platform.name}`,
        instructions: JSON.stringify({
          agent_action: 'CREDENTIAL_REQUEST',
          platform: platform.name,
          platform_url: platform.url,
          fields_required: ['email', 'password'],
          security_notice: 'Credentials are used only for this session and are not stored persistently. They are passed directly to the browser automation layer.',
          user_message: `To complete your request on ${platform.name}, I need your login credentials. These will be used only for this single session.`
        })
      };
      return res.json(taskResponse(task));
    }

    await browserManager.initialize();
    const credentials = platform.requires_login ? vault.get(platform.key) : null;

    const automationTimeout = body.timeout_seconds || (body.triggered_by !== 'primary_ai' ? 20 : 120);

    const result = await runAutomation(taskId, platform, userPrompt, {
      credentials,
      outputFormat,
      timeout: automationTimeout,
      attachments: body.attachments || [],
      additionalInstructions: body.additional_instructions || null,
      triggeredBy: body.triggered_by || 'primary_ai',
      newConversation: body.new_conversation !== false,
      failoverContext: body.failover_context || null
    });

    Object.assign(task, result);
    task.status = result.status || 'SUCCESS';
    tasks.set(taskId, task);

    res.json(taskResponse(task));
  } catch (err) {
    logger.error(`Task processing error: ${err.message}`);
    const taskId = req.body?.task_id || 'unknown';
    const task = tasks.get(taskId) || {
      task_id: taskId,
      status: 'FAILED',
      mode: 'NORMAL',
      site_used: '',
      capability: '',
      output_type: 'text',
      output: null,
      metadata: null,
      error: null
    };
    task.status = 'FAILED';
    task.error = err.message;
    task.output = {
      text_content: `⚠️ WebBridge automation failed: ${err.message}`,
      description: `Automation error: ${err.message}`
    };
    tasks.set(taskId, task);
    res.status(200).json(taskResponse(task));
  }
});

app.get('/task/:taskId/status', (req, res) => {
  const task = tasks.get(req.params.taskId);
  if (!task) return res.status(404).json({ error: 'Task not found' });
  res.json({
    task_id: task.task_id,
    status: task.status,
    progress: task.status === 'PENDING' ? 'Processing...' : (task.status === 'SUCCESS' ? 'Complete' : task.status),
    result: task
  });
});

app.post('/credentials', (req, res) => {
  try {
    const { platform, email, secret, extra } = req.body;
    if (!platform || !email || !secret) {
      return res.status(400).json({ success: false, message: 'platform, email, and secret are required' });
    }
    vault.set(platform, email, secret, extra);
    logger.info(`Credentials stored for ${platform} (${email})`);

    const pendingTasks = Array.from(tasks.values()).filter(
      t => t.status === 'AWAITING_CREDENTIALS' && t.needs_user_action?.instructions?.includes(platform)
    );
    for (const task of pendingTasks) {
      task.status = 'PENDING';
      task.needs_user_action = null;
    }

    res.json({ success: true, message: `Credentials for ${platform} stored for this session` });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
});

app.get('/health', (req, res) => {
  res.json({ status: 'ok', uptime: process.uptime() });
});

async function runAutomation(taskId, platform, userPrompt, opts) {
  const { credentials, outputFormat, timeout, attachments, additionalInstructions, triggeredBy, newConversation, failoverContext } = opts;

  let playbook;
  try {
    playbook = await import(`./playbooks/${platform.key}.js`);
  } catch {
    playbook = await import(`./generic.js`);
  }

  let lastError = null;
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      const page = await browserManager.newPage(newConversation);

      if (credentials) {
        await playbook.login(page, credentials);
      }

      await playbook.navigate(page, platform.url);
      const extracted = await playbook.execute(page, userPrompt, {
        outputFormat,
        timeout,
        attachments,
        additionalInstructions,
        triggeredBy
      });

      return packageOutput(extracted, {
        taskType: classifyRequest(userPrompt),
        platformName: platform.name,
        platformUrl: platform.url,
        userPrompt,
        triggeredBy,
        failoverContext,
        timeElapsed: Date.now() - Date.now()
      });
    } catch (err) {
      lastError = err;
      logger.warn(`Task ${taskId} attempt ${attempt + 1} failed: ${err.message}`);
      if (err.message.includes('RATE_LIMIT') && attempt === 0) {
        logger.info(`Task ${taskId}: rate limited, waiting 30s before retry...`);
        await new Promise(r => setTimeout(r, 30000));
        continue;
      }
      break;
    }
  }

  throw lastError || new Error('Automation failed after all attempts');
}

process.on('SIGINT', async () => {
  logger.info('Shutting down...');
  await browserManager.shutdown();
  process.exit(0);
});

app.listen(PORT, HOST, () => {
  logger.info(`WebBridge server v1.1 listening on http://${HOST}:${PORT}`);
});
