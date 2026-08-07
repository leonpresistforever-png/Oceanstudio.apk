import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'studio.ocean.app',
  appName: 'Ocean.studio',
  webDir: 'dist',
  android: {
    allowMixedContent: true,
    captureInput: true,
  },
  plugins: {
    OceanNativeTerminal: {
      linuxDistro: 'ocean',
      autoSetup: true,
    },
  },
};

export default config;
