import type { TtsProvider } from '../prompt/types';

export interface TtsOptions {
  voiceUri?: string;
  rate?: number;
  pitch?: number;
  stripMarkdown?: boolean;
  provider?: TtsProvider;
}

let speakingUtterance: SpeechSynthesisUtterance | null = null;

export function stripMarkdownForSpeech(text: string): string {
  return text
    .replace(/```[\s\S]*?```/g, ' code block ')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/#{1,6}\s/g, '')
    .replace(/\*\*([^*]+)\*\*/g, '$1')
    .replace(/\*([^*]+)\*/g, '$1')
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
    .replace(/[-*•]\s/g, '')
    .replace(/\n{2,}/g, '. ')
    .trim();
}

export function getBrowserVoices(): SpeechSynthesisVoice[] {
  if (typeof speechSynthesis === 'undefined') return [];
  return speechSynthesis.getVoices();
}

export function stopSpeaking(): void {
  if (typeof speechSynthesis !== 'undefined') {
    speechSynthesis.cancel();
  }
  speakingUtterance = null;
}

export function isSpeaking(): boolean {
  return typeof speechSynthesis !== 'undefined' && speechSynthesis.speaking;
}

export function speakText(text: string, opts: TtsOptions = {}): Promise<void> {
  return new Promise((resolve, reject) => {
    if (typeof speechSynthesis === 'undefined') {
      reject(new Error('Speech synthesis not supported'));
      return;
    }

    stopSpeaking();

    const content = opts.stripMarkdown !== false ? stripMarkdownForSpeech(text) : text;
    if (!content) {
      resolve();
      return;
    }

    const utterance = new SpeechSynthesisUtterance(content);
    utterance.rate = opts.rate ?? 1;
    utterance.pitch = opts.pitch ?? 1;

    const voices = getBrowserVoices();
    if (opts.voiceUri) {
      const voice = voices.find((v) => v.voiceURI === opts.voiceUri);
      if (voice) utterance.voice = voice;
    } else {
      const en = voices.find((v) => v.lang.startsWith('en') && v.localService);
      if (en) utterance.voice = en;
    }

    utterance.onend = () => {
      speakingUtterance = null;
      resolve();
    };
    utterance.onerror = (e) => {
      speakingUtterance = null;
      reject(new Error(e.error ?? 'TTS failed'));
    };

    speakingUtterance = utterance;
    speechSynthesis.speak(utterance);
  });
}
