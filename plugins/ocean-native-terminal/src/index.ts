import { registerPlugin } from '@capacitor/core';
import type { OceanNativeTerminalPlugin } from './definitions';

const OceanNativeTerminal = registerPlugin<OceanNativeTerminalPlugin>('OceanNativeTerminal', {
  web: () => import('./web').then((m) => new m.OceanNativeTerminalWeb()),
});

export * from './definitions';
export { OceanNativeTerminal };
