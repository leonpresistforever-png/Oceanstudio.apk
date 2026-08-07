import { useState } from 'react';
import { Gamepad2, RotateCcw, ChevronDown, ChevronUp, Plus, Trash2 } from 'lucide-react';
import { useGameCompanionStore } from '../../store/gameCompanionStore';
import {
  GAME_AFK_ACTIONS,
  GAME_CONTROL_PROFILES,
  GAME_PRESETS,
  applyGamePreset,
  type GameAfkAction,
  type GameInputMethod,
  type GameScreenRegion,
  type GameVisionProvider,
} from '../../playground/gameCompanionTypes';

interface Props {
  platform: 'electron' | 'android' | 'web';
  disabled?: boolean;
}

export default function GameCompanionConfigPanel({ platform, disabled }: Props) {
  const config = useGameCompanionStore((s) => s.config);
  const setGameConfig = useGameCompanionStore((s) => s.setGameConfig);
  const resetGameConfig = useGameCompanionStore((s) => s.resetGameConfig);
  const [expanded, setExpanded] = useState(true);
  const [showAdvanced, setShowAdvanced] = useState(false);

  function applyProfile(profileId: typeof config.controlProfile) {
    const profile = GAME_CONTROL_PROFILES.find((p) => p.id === profileId);
    setGameConfig({
      controlProfile: profileId,
      keyBindings: profile?.bindings ?? config.keyBindings,
    });
  }

  function toggleAfkAction(action: GameAfkAction) {
    const has = config.antiAfkActions.includes(action);
    setGameConfig({
      antiAfkActions: has
        ? config.antiAfkActions.filter((a) => a !== action)
        : [...config.antiAfkActions, action],
    });
  }

  function updateBinding(index: number, field: 'action' | 'keys', value: string) {
    const next = config.keyBindings.map((b, i) =>
      i === index ? { ...b, [field]: value } : b
    );
    setGameConfig({ keyBindings: next });
  }

  function addBinding() {
    setGameConfig({
      keyBindings: [...config.keyBindings, { action: 'custom_action', keys: '' }],
    });
  }

  function removeBinding(index: number) {
    setGameConfig({ keyBindings: config.keyBindings.filter((_, i) => i !== index) });
  }

  function updateRegion(index: number, patch: Partial<GameScreenRegion>) {
    setGameConfig({
      screenRegions: config.screenRegions.map((r, i) => (i === index ? { ...r, ...patch } : r)),
    });
  }

  function addRegion() {
    setGameConfig({
      screenRegions: [
        ...config.screenRegions,
        { name: 'Region', x: 0, y: 0, width: 400, height: 300 },
      ],
    });
  }

  if (platform === 'web') {
    return (
      <div className="pg-game-config pg-game-config-web">
        <Gamepad2 size={16} />
        <p>Game Companion requires Desktop (EXE) or Android (APK) for screen control and Anti-AFK.</p>
      </div>
    );
  }

  return (
    <section className="pg-game-config">
      <div className="pg-game-config-header">
        <Gamepad2 size={18} />
        <div>
          <strong>Game Companion Configuration</strong>
          <p>Full setup for Anti-AFK, screen interaction, vision, and gameplay.md learning.</p>
        </div>
        <label className="pg-game-enable">
          <input
            type="checkbox"
            checked={config.enabled}
            disabled={disabled}
            onChange={(e) => setGameConfig({ enabled: e.target.checked })}
          />
          Enable
        </label>
        <button
          type="button"
          className="pg-game-expand"
          onClick={() => setExpanded(!expanded)}
          aria-label={expanded ? 'Collapse' : 'Expand'}
        >
          {expanded ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
        </button>
      </div>

      {expanded && (
        <>
          {/* Presets */}
          <div className="pg-game-section">
            <h5>Game Presets</h5>
            <div className="pg-game-presets">
              {GAME_PRESETS.map((preset) => (
                <button
                  key={preset.id}
                  type="button"
                  className="pg-game-preset-btn"
                  disabled={disabled}
                  onClick={() => setGameConfig(applyGamePreset(preset.id, config))}
                >
                  {preset.label}
                </button>
              ))}
              <button
                type="button"
                className="pg-game-preset-btn reset"
                disabled={disabled}
                onClick={resetGameConfig}
              >
                <RotateCcw size={12} /> Reset
              </button>
            </div>
          </div>

          {/* Identity */}
          <div className="pg-game-section">
            <h5>Game Identity</h5>
            <div className="pg-game-grid">
              <label>
                <span>Game title *</span>
                <input
                  value={config.gameTitle}
                  disabled={disabled}
                  placeholder="e.g. Minecraft"
                  onChange={(e) => setGameConfig({ gameTitle: e.target.value })}
                />
              </label>
              <label>
                <span>Process name</span>
                <input
                  value={config.processName}
                  disabled={disabled}
                  placeholder="Minecraft.exe / RobloxPlayerBeta"
                  onChange={(e) => setGameConfig({ processName: e.target.value })}
                />
              </label>
              <label>
                <span>Window title (partial match)</span>
                <input
                  value={config.windowTitle}
                  disabled={disabled}
                  placeholder="Minecraft"
                  onChange={(e) => setGameConfig({ windowTitle: e.target.value })}
                />
              </label>
              {platform === 'electron' && (
                <label>
                  <span>Launcher path (optional)</span>
                  <input
                    value={config.launcherPath}
                    disabled={disabled}
                    placeholder="C:\Games\...\launcher.exe"
                    onChange={(e) => setGameConfig({ launcherPath: e.target.value })}
                  />
                </label>
              )}
              {platform === 'android' && (
                <label>
                  <span>Android package name</span>
                  <input
                    value={config.androidPackageName}
                    disabled={disabled}
                    placeholder="com.mojang.minecraftpe"
                    onChange={(e) => setGameConfig({ androidPackageName: e.target.value })}
                  />
                </label>
              )}
            </div>
          </div>

          {/* Input & Controls */}
          <div className="pg-game-section">
            <h5>Input & Controls</h5>
            <div className="pg-game-grid">
              <label>
                <span>Input method</span>
                <select
                  value={config.inputMethod}
                  disabled={disabled}
                  onChange={(e) => setGameConfig({ inputMethod: e.target.value as GameInputMethod })}
                >
                  <option value="keyboard">Keyboard</option>
                  <option value="mouse">Mouse</option>
                  <option value="controller">Controller</option>
                  {platform === 'android' && <option value="touch">Touch</option>}
                </select>
              </label>
              <label>
                <span>Control profile</span>
                <select
                  value={config.controlProfile}
                  disabled={disabled}
                  onChange={(e) => applyProfile(e.target.value as typeof config.controlProfile)}
                >
                  {GAME_CONTROL_PROFILES.map((p) => (
                    <option key={p.id} value={p.id}>{p.label}</option>
                  ))}
                </select>
              </label>
            </div>

            <div className="pg-game-bindings">
              <div className="pg-game-bindings-header">
                <span>Key bindings</span>
                <button type="button" className="pg-game-add-btn" disabled={disabled} onClick={addBinding}>
                  <Plus size={12} /> Add
                </button>
              </div>
              {config.keyBindings.map((binding, i) => (
                <div key={i} className="pg-game-binding-row">
                  <input
                    value={binding.action}
                    disabled={disabled}
                    placeholder="action"
                    onChange={(e) => updateBinding(i, 'action', e.target.value)}
                  />
                  <input
                    value={binding.keys}
                    disabled={disabled}
                    placeholder="keys (w, space, ctrl+1)"
                    onChange={(e) => updateBinding(i, 'keys', e.target.value)}
                  />
                  <button type="button" disabled={disabled} onClick={() => removeBinding(i)}>
                    <Trash2 size={12} />
                  </button>
                </div>
              ))}
            </div>
          </div>

          {/* Anti-AFK */}
          <div className="pg-game-section">
            <h5>Anti-AFK</h5>
            <label className="pg-game-toggle">
              <input
                type="checkbox"
                checked={config.antiAfkEnabled}
                disabled={disabled}
                onChange={(e) => setGameConfig({ antiAfkEnabled: e.target.checked })}
              />
              Enable Anti-AFK loop
            </label>
            <div className="pg-game-grid">
              <label>
                <span>Interval (seconds)</span>
                <input
                  type="number"
                  min={30}
                  max={600}
                  value={config.antiAfkIntervalSec}
                  disabled={disabled || !config.antiAfkEnabled}
                  onChange={(e) => setGameConfig({ antiAfkIntervalSec: Number(e.target.value) })}
                />
              </label>
              <label>
                <span>Jitter ± (seconds)</span>
                <input
                  type="number"
                  min={0}
                  max={120}
                  value={config.antiAfkJitterSec}
                  disabled={disabled || !config.antiAfkEnabled}
                  onChange={(e) => setGameConfig({ antiAfkJitterSec: Number(e.target.value) })}
                />
              </label>
            </div>
            <div className="pg-game-afk-actions">
              {GAME_AFK_ACTIONS.map((action) => (
                <button
                  key={action.id}
                  type="button"
                  className={`pg-game-afk-chip ${config.antiAfkActions.includes(action.id) ? 'active' : ''}`}
                  disabled={disabled || !config.antiAfkEnabled}
                  title={action.description}
                  onClick={() => toggleAfkAction(action.id)}
                >
                  {action.label}
                </button>
              ))}
            </div>
            {config.antiAfkActions.includes('custom_macro') && (
              <label className="pg-game-macro">
                <span>Custom macro (comma-separated keys)</span>
                <input
                  value={config.customMacro}
                  disabled={disabled}
                  placeholder="w,a,s,d,space,e"
                  onChange={(e) => setGameConfig({ customMacro: e.target.value })}
                />
              </label>
            )}
          </div>

          {/* Vision */}
          <div className="pg-game-section">
            <h5>Vision & Screen Understanding</h5>
            <label className="pg-game-toggle">
              <input
                type="checkbox"
                checked={config.visionEnabled}
                disabled={disabled}
                onChange={(e) => setGameConfig({ visionEnabled: e.target.checked })}
              />
              Enable screen capture + vision model
            </label>
            <div className="pg-game-grid">
              <label>
                <span>Vision provider</span>
                <select
                  value={config.visionProvider}
                  disabled={disabled || !config.visionEnabled}
                  onChange={(e) => setGameConfig({ visionProvider: e.target.value as GameVisionProvider })}
                >
                  <option value="auto">Auto (connected provider)</option>
                  <option value="openai">OpenAI</option>
                  <option value="anthropic">Anthropic</option>
                  <option value="google">Google Gemini</option>
                  <option value="local">Local / Ollama</option>
                </select>
              </label>
              <label>
                <span>Screenshot interval (seconds)</span>
                <input
                  type="number"
                  min={5}
                  max={300}
                  value={config.screenshotIntervalSec}
                  disabled={disabled || !config.visionEnabled}
                  onChange={(e) => setGameConfig({ screenshotIntervalSec: Number(e.target.value) })}
                />
              </label>
            </div>
          </div>

          {/* Advanced */}
          <button
            type="button"
            className="pg-game-advanced-toggle"
            onClick={() => setShowAdvanced(!showAdvanced)}
          >
            {showAdvanced ? 'Hide' : 'Show'} advanced settings
            {showAdvanced ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
          </button>

          {showAdvanced && (
            <>
              <div className="pg-game-section">
                <h5>Safety & Session</h5>
                <div className="pg-game-toggles">
                  <label>
                    <input
                      type="checkbox"
                      checked={config.pauseOnUserInput}
                      disabled={disabled}
                      onChange={(e) => setGameConfig({ pauseOnUserInput: e.target.checked })}
                    />
                    Pause bot when you use keyboard/mouse
                  </label>
                  <label>
                    <input
                      type="checkbox"
                      checked={config.safeZoneOnly}
                      disabled={disabled}
                      onChange={(e) => setGameConfig({ safeZoneOnly: e.target.checked })}
                    />
                    Restrict actions to defined screen regions
                  </label>
                  <label>
                    <input
                      type="checkbox"
                      checked={config.bringWindowToFront}
                      disabled={disabled}
                      onChange={(e) => setGameConfig({ bringWindowToFront: e.target.checked })}
                    />
                    Bring game window to front before actions
                  </label>
                  <label>
                    <input
                      type="checkbox"
                      checked={config.focusBeforeAction}
                      disabled={disabled}
                      onChange={(e) => setGameConfig({ focusBeforeAction: e.target.checked })}
                    />
                    Focus game window before each action
                  </label>
                  <label>
                    <input
                      type="checkbox"
                      checked={config.updateGameplayMd}
                      disabled={disabled}
                      onChange={(e) => setGameConfig({ updateGameplayMd: e.target.checked })}
                    />
                    Auto-update gameplay.md as bot learns
                  </label>
                </div>
                <div className="pg-game-grid">
                  <label>
                    <span>Max session (hours)</span>
                    <input
                      type="number"
                      min={1}
                      max={72}
                      value={config.maxSessionHours}
                      disabled={disabled}
                      onChange={(e) => setGameConfig({ maxSessionHours: Number(e.target.value) })}
                    />
                  </label>
                  <label>
                    <span>Emergency stop key</span>
                    <input
                      value={config.emergencyStopKey}
                      disabled={disabled}
                      onChange={(e) => setGameConfig({ emergencyStopKey: e.target.value })}
                    />
                  </label>
                  <label>
                    <span>Gameplay file path</span>
                    <input
                      value={config.gameplayMdPath}
                      disabled={disabled}
                      onChange={(e) => setGameConfig({ gameplayMdPath: e.target.value })}
                    />
                  </label>
                </div>
              </div>

              <div className="pg-game-section">
                <div className="pg-game-bindings-header">
                  <h5>Screen regions</h5>
                  <button type="button" className="pg-game-add-btn" disabled={disabled} onClick={addRegion}>
                    <Plus size={12} /> Add region
                  </button>
                </div>
                {config.screenRegions.map((region, i) => (
                  <div key={i} className="pg-game-region-row">
                    <input
                      value={region.name}
                      disabled={disabled}
                      placeholder="name"
                      onChange={(e) => updateRegion(i, { name: e.target.value })}
                    />
                    <input
                      type="number"
                      value={region.x}
                      disabled={disabled}
                      placeholder="x"
                      onChange={(e) => updateRegion(i, { x: Number(e.target.value) })}
                    />
                    <input
                      type="number"
                      value={region.y}
                      disabled={disabled}
                      placeholder="y"
                      onChange={(e) => updateRegion(i, { y: Number(e.target.value) })}
                    />
                    <input
                      type="number"
                      value={region.width}
                      disabled={disabled}
                      placeholder="w"
                      onChange={(e) => updateRegion(i, { width: Number(e.target.value) })}
                    />
                    <input
                      type="number"
                      value={region.height}
                      disabled={disabled}
                      placeholder="h"
                      onChange={(e) => updateRegion(i, { height: Number(e.target.value) })}
                    />
                  </div>
                ))}
              </div>

              <div className="pg-game-section">
                <h5>Notes for the bot</h5>
                <textarea
                  className="pg-game-notes"
                  rows={3}
                  value={config.notes}
                  disabled={disabled}
                  placeholder="Special instructions: avoid PvP zones, farm iron at coordinates X,Y, etc."
                  onChange={(e) => setGameConfig({ notes: e.target.value })}
                />
              </div>
            </>
          )}
        </>
      )}
    </section>
  );
}
