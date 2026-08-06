export type EnhanceStyle = 'balanced' | 'detailed' | 'concise' | 'technical' | 'creative';

export type CompressLevel = 'off' | 'light' | 'medium' | 'aggressive';

export type TtsProvider = 'browser' | 'openai' | 'elevenlabs' | 'google';

export interface PromptEnhancerConfig {
  /** Auto-enhance before every send */
  autoEnhance: boolean;
  /** Auto-compress to save tokens */
  autoCompress: boolean;
  enhanceStyle: EnhanceStyle;
  compressLevel: CompressLevel;
  /** Max tokens for outgoing user message (estimate) */
  maxInputTokens: number;
  /** Custom system instruction for AI enhance */
  customEnhancePrompt: string;
  /** Strip markdown from TTS */
  ttsStripMarkdown: boolean;
  ttsProvider: TtsProvider;
  ttsVoiceUri: string;
  ttsRate: number;
  ttsPitch: number;
  /** STT language */
  sttLanguage: string;
  sttContinuous: boolean;
  /** Wake phrase for background voice assistant (default: "ocean") */
  wakePhrase?: string;
  /** Screen narrator auto-describe interval */
  narratorIntervalSec?: number;
  /** Narrator personality tone */
  narratorTone?: 'helper' | 'storyteller' | 'technical' | 'casual';
  /** Background assistant enabled */
  backgroundAssistant?: boolean;
}

export const DEFAULT_PROMPT_ENHANCER_CONFIG: PromptEnhancerConfig = {
  autoEnhance: false,
  autoCompress: true,
  enhanceStyle: 'balanced',
  compressLevel: 'light',
  maxInputTokens: 4000,
  customEnhancePrompt: '',
  ttsStripMarkdown: true,
  ttsProvider: 'browser',
  ttsVoiceUri: '',
  ttsRate: 1,
  ttsPitch: 1,
  sttLanguage: 'en-US',
  sttContinuous: false,
  wakePhrase: 'ocean',
  narratorIntervalSec: 30,
  narratorTone: 'helper',
  backgroundAssistant: false,
};

export interface PromptProcessResult {
  original: string;
  processed: string;
  enhanced: boolean;
  compressed: boolean;
  estimatedTokensBefore: number;
  estimatedTokensAfter: number;
  savedTokens: number;
}
