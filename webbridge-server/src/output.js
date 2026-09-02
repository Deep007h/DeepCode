export function packageOutput(extracted, opts) {
  const { taskType, platformName, platformUrl, userPrompt, triggeredBy, failoverContext, timeElapsed } = opts;

  const base = {
    site_used: platformName,
    target_site_used: platformName,
    capability: taskType,
    output_type: extracted.type || 'text',
    metadata: {
      prompt_submitted: userPrompt,
      time_elapsed_ms: timeElapsed || 0,
      platform_used: platformName,
      platform_url: platformUrl,
      fallback_used: false,
      fallback_reason: null
    }
  };

  if (failoverContext) {
    base.mode = 'FAILOVER';
    base.failover_metadata = {
      primary_ai_status: failoverContext.primary_ai_status || 'unknown',
      silence_duration_seconds: failoverContext.silence_duration_seconds || 0,
      fallback_site_selected: platformName,
      failover_triggered_at: new Date().toISOString(),
      context_passed_to_site: true,
      user_notified: failoverContext.notify_user || false
    };
    base.recovery_hint = {
      primary_ai_should_resume: true,
      buffered_exchange: failoverContext.conversation_history || null
    };
  }

  if (extracted.error) {
    return {
      ...base,
      status: 'FAILED',
      error: extracted.error,
      output: {
        text_content: `⚠️ Automation failed on ${platformName}: ${extracted.error}`,
        description: `Error on ${platformName}: ${extracted.error}`
      }
    };
  }

  const output = {};

  switch (extracted.type) {
    case 'image': {
      output.image_urls = extracted.imageUrls || [];
      output.base64_images = extracted.base64Images || [];
      output.text_content = extracted.text || null;
      output.description = extracted.description || `Generated image from ${platformName}`;
      break;
    }
    case 'video': {
      output.video_url = extracted.videoUrl || null;
      output.text_content = extracted.text || null;
      output.description = extracted.description || `Generated video from ${platformName}`;
      break;
    }
    case 'audio': {
      output.audio_url = extracted.audioUrl || null;
      output.text_content = extracted.text || null;
      output.description = extracted.description || `Generated audio from ${platformName}`;
      break;
    }
    case 'code': {
      output.text_content = extracted.code || extracted.text || '';
      output.description = extracted.description || `Generated code from ${platformName}`;
      break;
    }
    default: {
      output.text_content = extracted.text || extracted.result || '';
      output.image_urls = extracted.imageUrls || [];
      output.description = extracted.description || `Response from ${platformName}`;
    }
  }

  return {
    ...base,
    status: 'SUCCESS',
    output,
    error: null
  };
}
