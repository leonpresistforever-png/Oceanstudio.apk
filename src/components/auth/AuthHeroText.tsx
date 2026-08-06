import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';

const HERO_SLIDES = [
  { quote: 'Welcome to Ocean.studio', text: 'Build with real execution.' },
  { quote: 'Your workspace awaits', text: 'Multi-agent teams. One workspace.' },
  { quote: 'Everything connected', text: 'Terminal. MCP. Plugins. Connected.' },
  { quote: 'Ship with confidence', text: 'Hardware-level coding intelligence.' },
  { quote: 'Dive in', text: 'Ocean.studio' },
];

/** Top-layer heading — opacity-only transitions; does not shift the background scene */
export default function AuthHeroText() {
  const [index, setIndex] = useState(0);

  useEffect(() => {
    const t = setInterval(() => setIndex((i) => (i + 1) % HERO_SLIDES.length), 7000);
    return () => clearInterval(t);
  }, []);

  const slide = HERO_SLIDES[index];

  return (
    <div className="auth-hero-text-layer" aria-live="polite">
      <div className="auth-hero-text-slot">
        <AnimatePresence mode="wait">
          <motion.div
            key={index}
            className="auth-hero-text-content"
            initial={{ opacity: 0, filter: 'blur(8px)' }}
            animate={{ opacity: 1, filter: 'blur(0px)' }}
            exit={{ opacity: 0, filter: 'blur(6px)' }}
            transition={{ duration: 1.1, ease: [0.25, 0.1, 0.25, 1] }}
          >
            <p className="auth-hero-quote">{slide.quote}</p>
            <h2 className="auth-hero-big-heading">{slide.text}</h2>
          </motion.div>
        </AnimatePresence>
      </div>
    </div>
  );
}
