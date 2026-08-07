/** Provider strength profiles — used for smart role/task suggestions */

export interface ProviderProfile {
  providerId: string;
  strengths: ('coding' | 'reasoning' | 'speed' | 'review' | 'research' | 'creative' | 'ops')[];
  suggestedRole: 'head' | 'worker' | 'specialist' | 'reviewer';
  suggestedTask: string;
  suggestedThinking: 'off' | 'low' | 'medium' | 'high' | 'max';
  suggestedTemperature: number;
}

const PROFILES: Record<string, ProviderProfile> = {
  'openai.codex': {
    providerId: 'openai.codex',
    strengths: ['coding', 'ops'],
    suggestedRole: 'head',
    suggestedTask: 'Architecture, implementation, and synthesis',
    suggestedThinking: 'medium',
    suggestedTemperature: 0.6,
  },
  'google.antigravity': {
    providerId: 'google.antigravity',
    strengths: ['reasoning', 'speed', 'creative'],
    suggestedRole: 'worker',
    suggestedTask: 'Deep reasoning or fast iteration depending on model',
    suggestedThinking: 'high',
    suggestedTemperature: 0.7,
  },
  'anthropic.claude-code': {
    providerId: 'anthropic.claude-code',
    strengths: ['coding', 'review', 'reasoning'],
    suggestedRole: 'reviewer',
    suggestedTask: 'Code review, tests, documentation',
    suggestedThinking: 'high',
    suggestedTemperature: 0.5,
  },
  'github.copilot': {
    providerId: 'github.copilot',
    strengths: ['coding', 'speed'],
    suggestedRole: 'worker',
    suggestedTask: 'Inline code completion and quick fixes',
    suggestedThinking: 'low',
    suggestedTemperature: 0.4,
  },
};

export function getProviderProfile(providerId: string): ProviderProfile {
  return PROFILES[providerId] ?? {
    providerId,
    strengths: ['coding'],
    suggestedRole: 'worker',
    suggestedTask: 'General development tasks',
    suggestedThinking: 'medium',
    suggestedTemperature: 0.7,
  };
}

export function suggestForModel(providerId: string, modelId: string): Partial<{
  role: ProviderProfile['suggestedRole'];
  taskAssignment: string;
  thinkingLevel: ProviderProfile['suggestedThinking'];
  temperature: number;
}> {
  const profile = getProviderProfile(providerId);
  const isFlash = /flash/i.test(modelId);
  const isThinking = /thinking|opus/i.test(modelId);

  if (isFlash) {
    return {
      role: 'worker',
      taskAssignment: 'Fast iteration, UI polish, quick fixes',
      thinkingLevel: 'low',
      temperature: 0.85,
    };
  }
  if (isThinking) {
    return {
      role: 'specialist',
      taskAssignment: 'Deep reasoning, complex refactors, architecture',
      thinkingLevel: 'max',
      temperature: 0.6,
    };
  }
  return {
    role: profile.suggestedRole,
    taskAssignment: profile.suggestedTask,
    thinkingLevel: profile.suggestedThinking,
    temperature: profile.suggestedTemperature,
  };
}
