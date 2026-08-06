import { useCallback, useRef } from 'react';

interface UseWakeWordOpts {
  wakePhrase: string;
  onWake: (fullTranscript: string) => void;
  onCommand?: (command: string) => void;
  enabled?: boolean;
}

/** Detect wake phrase in speech transcript and extract command after it */
export function useWakeWord({ wakePhrase, onWake, onCommand, enabled = true }: UseWakeWordOpts) {
  const wokeRef = useRef(false);
  const phrase = wakePhrase.toLowerCase().trim();

  const processTranscript = useCallback(
    (transcript: string, isFinal: boolean) => {
      if (!enabled || !phrase) return;
      const lower = transcript.toLowerCase().trim();

      if (!wokeRef.current && lower.includes(phrase)) {
        wokeRef.current = true;
        const afterWake = lower.split(phrase).slice(1).join(phrase).trim();
        onWake(transcript);
        if (afterWake && isFinal) {
          onCommand?.(afterWake);
          wokeRef.current = false;
        }
        return;
      }

      if (wokeRef.current && isFinal) {
        onCommand?.(lower);
        wokeRef.current = false;
      }
    },
    [enabled, phrase, onWake, onCommand]
  );

  const reset = useCallback(() => {
    wokeRef.current = false;
  }, []);

  return { processTranscript, reset };
}
