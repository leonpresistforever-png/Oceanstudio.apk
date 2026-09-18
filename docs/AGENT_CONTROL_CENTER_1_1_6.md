# OceanStudio 1.1.6 — Agent Control Center checkpoint

This checkpoint extends the existing 1.1.6 crash-survival, Runtime Ports, Device Access, and APK Lab work.

## Agent Settings

Native white settings page with persistent controls for:

- temperature
- top P
- reasoning / thinking effort (Default, Low, Medium, High)
- max output tokens
- provider connect timeout
- provider response timeout
- command timeout
- max agent rounds
- max tool calls
- recent-session context retention
- extended response-timeout mode
- persistent user instructions

The configured values are consumed by the live agent runtime. Settings changes are part of the conversation configuration digest, so a changed configuration starts a new compatible conversation state automatically.

Provider mapping:
- Google: generationConfig plus optional thinkingConfig.thinkingLevel.
- Anthropic: generation parameters plus optional adaptive thinking / output effort.
- OpenAI: optional reasoning_effort for explicit non-default reasoning settings.
- Custom OpenAI-compatible endpoints receive standard generation parameters; provider-specific reasoning fields are not forced.

## Right-side Agent Controls

The chat header now exposes a white right-side Agent Controls drawer. It summarizes the active model, temperature, top P, token limit, response timeout, and session-context mode, with direct navigation to:

- Agent Settings
- Plugins
- Models & APIs
- Device Access
- Runtime Ports

## Plugins

A white Plugins page shows built-in Ocean integrations and installed state. Connect/disconnect state persists locally and is enforced in the agent runtime for:

- Ocean Terminal
- Runtime Ports
- Device Access

Disconnecting one prevents agent workflows from using that integration without uninstalling its files.

The new executable `ocean-plugin` command provides persistent terminal registration:

```
ocean-plugin register <id> <name> <command> [description]
ocean-plugin list
ocean-plugin remove <id>
```

Registered manifests are stored under `$HOME/.ocean/plugins` and automatically appear in the Plugins page.

## App Access Profiles

Device Access now links to App Access Profiles.

Restriction mode is off by default, preserving existing behavior. When enabled, the user selects per package whether Ocean may:

- inspect the visible accessibility tree
- interact with the visible app
- capture Accessibility screenshots

The DeviceControlService enforces the policy against the active/target package. This scopes user-authorized visible-screen automation; it does not change Android UID isolation, private app storage, DRM, or secure-screen protections.

## Validation

Source-level consistency audit completed after the changes:

- Java brace counts balanced for all changed classes.
- activity_main.xml well-formed; Agent Controls drawer occurs exactly once.
- activity_agent_settings.xml well-formed.
- activity_plugins.xml well-formed.
- AndroidManifest.xml well-formed.
- Unit/source tests added for configurable generation/reasoning/session behavior and Agent Control Center/App Access integration.

A new APK has not been produced in this checkpoint because the repository's GitHub Actions quota remains unavailable.
