import type { ChatCompletionRequest, StrategicWorkflow } from './types.js';
import { cursorToOpenAI } from './translator.js';
import { compressMessages } from './context-compressor.js';

export interface WorkflowContext {
  trigger: 'chat' | 'agent' | 'playground' | 'ide';
  body: ChatCompletionRequest;
  workflows: StrategicWorkflow[];
}

export interface WorkflowResult {
  body: ChatCompletionRequest;
  comboId?: string;
  maxAttempts?: number;
  appliedSteps: string[];
  compressionSavedChars: number;
}

export function executeWorkflow(ctx: WorkflowContext): WorkflowResult {
  const active = ctx.workflows.filter((w) => w.enabled && w.triggers.includes(ctx.trigger));
  const workflow = active[0];

  let body = { ...ctx.body };
  let comboId: string | undefined;
  let maxAttempts = 1;
  const appliedSteps: string[] = [];
  let compressionSavedChars = 0;

  if (!workflow) {
    return { body, appliedSteps, compressionSavedChars };
  }

  for (const step of workflow.steps) {
    switch (step.type) {
      case 'transform':
        if (step.config.bridge === 'cursor') {
          body = cursorToOpenAI(body);
          appliedSteps.push('cursor-bridge');
        }
        break;
      case 'compress': {
        const msgs = body.messages ?? [];
        const { messages, savedChars } = compressMessages(msgs, {
          caveman: step.config.caveman as boolean | undefined,
          rtk: step.config.rtk as boolean | undefined,
          maxCharsPerMessage: step.config.maxCharsPerMessage as number | undefined,
        });
        body = { ...body, messages };
        compressionSavedChars += savedChars;
        appliedSteps.push('compress');
        break;
      }
      case 'route':
        comboId = step.config.comboId as string | undefined;
        appliedSteps.push(`route:${comboId}`);
        break;
      case 'fallback':
        maxAttempts = (step.config.maxAttempts as number) ?? 3;
        appliedSteps.push(`fallback:${maxAttempts}`);
        break;
      default:
        appliedSteps.push(step.type);
    }
  }

  return { body, comboId, maxAttempts, appliedSteps, compressionSavedChars };
}
