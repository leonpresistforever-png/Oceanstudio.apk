import { useCallback, useEffect, useRef, useState } from 'react';

interface SpeechRecognitionResult {
  transcript: string;
  isFinal: boolean;
}

interface UseSpeechRecognitionOpts {
  language?: string;
  continuous?: boolean;
  onResult?: (result: SpeechRecognitionResult) => void;
}

type SpeechRecognitionCtor = new () => {
  continuous: boolean;
  interimResults: boolean;
  lang: string;
  start: () => void;
  stop: () => void;
  abort: () => void;
  onresult: ((ev: { results: { [i: number]: { [j: number]: { transcript: string } }; isFinal: boolean }[] }) => void) | null;
  onerror: ((ev: { error: string }) => void) | null;
  onend: (() => void) | null;
};

function getSpeechRecognition(): SpeechRecognitionCtor | null {
  const w = window as unknown as {
    SpeechRecognition?: SpeechRecognitionCtor;
    webkitSpeechRecognition?: SpeechRecognitionCtor;
  };
  return w.SpeechRecognition ?? w.webkitSpeechRecognition ?? null;
}

export function useSpeechRecognition(opts: UseSpeechRecognitionOpts = {}) {
  const [listening, setListening] = useState(false);
  const [supported, setSupported] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const recRef = useRef<InstanceType<SpeechRecognitionCtor> | null>(null);

  useEffect(() => {
    setSupported(!!getSpeechRecognition());
  }, []);

  const stop = useCallback(() => {
    recRef.current?.stop();
    setListening(false);
  }, []);

  const start = useCallback(() => {
    const Ctor = getSpeechRecognition();
    if (!Ctor) {
      setError('Speech recognition not supported in this browser');
      return;
    }

    setError(null);
    const rec = new Ctor();
    rec.continuous = opts.continuous ?? false;
    rec.interimResults = true;
    rec.lang = opts.language ?? 'en-US';

    rec.onresult = (ev) => {
      const results = ev.results as unknown as SpeechRecognitionResultList;
      const last = results[results.length - 1];
      const transcript = last?.[0]?.transcript ?? '';
      opts.onResult?.({ transcript, isFinal: last?.isFinal ?? false });
    };

    rec.onerror = (ev) => {
      setError(ev.error);
      setListening(false);
    };

    rec.onend = () => setListening(false);

    recRef.current = rec;
    rec.start();
    setListening(true);
  }, [opts.continuous, opts.language, opts.onResult]);

  const toggle = useCallback(() => {
    if (listening) stop();
    else start();
  }, [listening, start, stop]);

  return { listening, supported, error, start, stop, toggle };
}
