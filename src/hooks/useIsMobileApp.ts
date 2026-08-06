import { useEffect, useState } from 'react';
import { Capacitor } from '@capacitor/core';

export function useIsMobileApp(): boolean {
  const [mobile, setMobile] = useState(() => {
    if (typeof window === 'undefined') return false;
    if (Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'android') return true;
    return window.innerWidth < 768;
  });

  useEffect(() => {
    if (Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'android') {
      setMobile(true);
      return;
    }
    const onResize = () => setMobile(window.innerWidth < 768);
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  return mobile;
}
