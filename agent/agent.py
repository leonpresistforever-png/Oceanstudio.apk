#!/usr/bin/env python3
"""Ocean.studio Agent — hardware-level coding agent backend."""

import json
import os
import sys
import time
import uuid
from pathlib import Path


def emit_event(event_type: str, summary: str, detail: str = "", status: str = "completed", metadata: dict | None = None):
    event = {
        "type": event_type,
        "id": str(uuid.uuid4()),
        "timestamp": int(time.time() * 1000),
        "summary": summary,
        "detail": detail,
        "status": status,
        "metadata": metadata or {},
    }
    print(f"OCEAN_EVENT:{json.dumps(event)}", flush=True)


def resolve_terminal_guidance(terminal_type: str) -> str:
    guides = {
        "shell": (
            "SHELL terminal — real host OS shell via node-pty.\n"
            "- All commands run without whitelist restriction\n"
            "- Windows: PowerShell (WSL/bash if WSL installed)\n"
            "- Linux/Mac: Bash/Zsh with apt/brew/pip and all host binaries"
        ),
        "cloud": (
            "CLOUD terminal — Google Cloud Shell (Debian VM in GCP).\n"
            "- OAuth grants user-level GCP access\n"
            "- apt, pip, gcloud, docker, persistent $HOME"
        ),
        "native": (
            "NATIVE terminal — mobile Linux via proot (APK builds).\n"
            "- Full package manager without root\n"
            "- All Linux commands available in proot environment"
        ),
    }
    return guides.get(terminal_type, f"Unknown terminal type: {terminal_type}")


def format_model_context(model_config: dict) -> str:
    if not model_config:
        return "Model: default"
    lines = [
        f"Model: {model_config.get('modelId', 'unknown')}",
        f"Temperature: {model_config.get('temperature', 0.7)}",
        f"Thinking: {model_config.get('thinkingLevel', 'medium')}",
        f"Strictness: {model_config.get('strictness', 'normal')}",
        f"Scraping: {model_config.get('scrapingProvider', 'firecrawl')}",
    ]
    if model_config.get("bypassDefaultInstructions"):
        lines.append("Bypass default instructions: yes")
    if model_config.get("customInstructions"):
        lines.append(f"Custom instructions: {str(model_config['customInstructions'])[:200]}")
    skills = model_config.get("skills") or []
    if skills:
        lines.append(f"Skills loaded: {len(skills)}")
    webhooks = model_config.get("webhooks") or []
    if webhooks:
        lines.append(f"Webhooks: {len(webhooks)}")
    custom_fn = model_config.get("customFunctions") or []
    if custom_fn:
        lines.append(f"Custom functions: {len(custom_fn)}")
    if model_config.get("postgresConnectionString"):
        lines.append("PostgreSQL: configured")
    return "\n".join(lines)


def is_catastrophic_command(command: str) -> bool:
    destructive = ["rm -rf /", "sudo rm", "mkfs.", "dd if=", "of=/dev/"]
    lower = command.lower()
    return any(d in lower for d in destructive)


def main():
    payload_str = os.environ.get("OCEAN_AGENT_PAYLOAD", "{}")
    payload = json.loads(payload_str)
    message = payload.get("message", "")
    context = payload.get("context", {})
    mode = payload.get("mode", "review")

    workspace = context.get("workspacePath", "")
    active_file = context.get("activeFile", "")
    terminal_type = context.get("terminalType", "shell")
    agent_terminal_id = context.get("agentTerminalId", "ocean-agent-terminal")
    model_config = context.get("modelConfig") or {}
    mcp_servers = context.get("mcpServers") or {}
    mcp_meta = context.get("mcpMeta") or {}
    plugin_tools = context.get("pluginTools") or []
    capabilities = context.get("capabilities") or {}
    platform_info = context.get("platform") or {}
    provider_meta = context.get("providerMeta") or {}
    provider_context = context.get("providerContext") or {}
    providers = context.get("providers") or {}
    multi_agent = context.get("multiAgent") or {}

    thought_detail = "\n".join([
        f"Message: {message}",
        f"Terminal: {terminal_type}",
        f"Mode: {mode}",
        f"Platform: {platform_info.get('platform', 'unknown')}",
        format_model_context(model_config),
        f"MCP connectors: {mcp_meta.get('connectedCount', len(mcp_servers))}",
        f"Plugin tools: {len(plugin_tools)}",
        f"Providers connected: {provider_meta.get('connectedCount', len(providers))}",
        f"Active providers: {', '.join(provider_meta.get('activeProviderIds') or []) or 'none'}",
        f"Multi-agent: {multi_agent.get('comboName', 'off')} ({multi_agent.get('mode', 'single')})" if multi_agent.get('enabled') else "Multi-agent: disabled",
        "",
        resolve_terminal_guidance(terminal_type),
    ])

    if provider_context.get("serialized"):
        emit_event(
            "action",
            "Provider agent context loaded",
            str(provider_context.get("serialized", ""))[:500],
            "completed",
            {"activeProviders": provider_meta.get("activeProviderIds", [])},
        )

    if multi_agent.get("enabled"):
        emit_event(
            "action",
            f"Multi-agent team: {multi_agent.get('comboName', 'team')}",
            str(multi_agent.get("serializedCombo", ""))[:600],
            "running",
            {"mode": multi_agent.get("mode"), "memberCount": len(multi_agent.get("members") or [])},
        )

    member_ctx = context.get("multiAgentMember")
    phase = context.get("_multiAgentPhase")
    if member_ctx:
        emit_event(
            "action",
            f"Sub-agent: {member_ctx.get('memberName', 'agent')}",
            f"Role: {member_ctx.get('role')}\nProvider: {member_ctx.get('providerId')}\nModel: {member_ctx.get('modelId')}\nPhase: {phase}",
            "running",
            {"memberId": member_ctx.get("memberId"), "phase": phase},
        )

    emit_event("thought", "Analyzing request", thought_detail, "completed")

    if mcp_servers:
        emit_event(
            "action",
            f"Using {len(mcp_servers)} MCP connector(s)",
            ", ".join(mcp_servers.keys()),
            "completed",
            {"mcpCount": len(mcp_servers)},
        )

    if plugin_tools:
        emit_event(
            "action",
            f"Loaded {len(plugin_tools)} plugin tool(s)",
            ", ".join(t.get("name", "?") for t in plugin_tools[:5]),
            "completed",
        )

    skills = model_config.get("skills") or []
    if skills:
        emit_event(
            "action",
            f"Applied {len(skills)} skill file(s)",
            ", ".join(s.get("name", "?") for s in skills[:5]),
            "completed",
        )

    trimmed = message.strip().lstrip("$").strip()
    looks_like_command = bool(trimmed) and len(trimmed) < 500 and not trimmed.startswith("{")
    command_keywords = ["run", "install", "npm", "pip", "apt", "gcloud", "git", "docker", "curl", "wget"]

    if looks_like_command and (any(kw in trimmed.lower() for kw in command_keywords) or " " in trimmed):
        emit_event("action", "Preparing shell command", f"Target terminal: {terminal_type}", "running")
        time.sleep(0.2)

        requires_approval = mode == "review" and is_catastrophic_command(trimmed)

        emit_event(
            "command",
            f"Execute in {terminal_type} terminal",
            f"$ {trimmed[:200]}\nSession: {agent_terminal_id}\nRequires approval: {requires_approval}",
            "completed" if not requires_approval else "running",
            {"requiresApproval": requires_approval, "terminalType": terminal_type},
        )

    elif "preview" in message.lower() or "port" in message.lower():
        emit_event("action", "Checking port status", "Scanning terminal output for active ports", "running")
        time.sleep(0.2)
        emit_event("result", "Port scan complete", "Ports auto-register in Preview panel from terminal output", "completed")

    elif workspace:
        ws_path = Path(workspace)
        files = list(ws_path.rglob("*")) if ws_path.exists() else []
        emit_event(
            "command",
            f"Scanned workspace ({len(files)} items)",
            f"Workspace: {workspace}\nActive file: {active_file or 'none'}\nTerminal: {terminal_type}",
            "completed",
        )
    else:
        caps_platform = capabilities.get("platform", "electron")
        emit_event(
            "result",
            "Ready to assist",
            f"Terminal: {terminal_type}\nPlatform: {caps_platform}\n"
            f"Scraping providers: {', '.join(capabilities.get('scrapingProviders', []))}\n"
            "I can edit files, run commands, use MCP tools, and manage previews.",
            "completed",
        )

    emit_event("status", "Agent idle", "Waiting for next instruction", "completed")

    # Emit substantive response for orchestrator capture
    member_ctx = context.get("multiAgentMember") or {}
    response_lines = [f"## Response\n"]
    if member_ctx:
        response_lines.append(f"**{member_ctx.get('memberName', 'Agent')}** ({member_ctx.get('role', 'worker')})")
        if member_ctx.get("task"):
            response_lines.append(f"Task: {member_ctx.get('task')}")
    response_lines.append(f"\nRequest: {message[:600]}")
    if workspace and Path(workspace).exists():
        py_files = list(Path(workspace).rglob("*.py"))[:5]
        ts_files = list(Path(workspace).rglob("*.ts"))[:5]
        response_lines.append(f"\nWorkspace scan: {len(py_files)} Python, {len(ts_files)} TypeScript files nearby")
    if mcp_servers:
        response_lines.append(f"MCP active: {', '.join(list(mcp_servers.keys())[:5])}")
    if plugin_tools:
        response_lines.append(f"Plugins: {len(plugin_tools)} tools loaded")
    response_lines.append(f"\nTerminal ({terminal_type}): ready for command execution")
    if phase:
        response_lines.append(f"Workflow phase: {phase}")

    response_content = "\n".join(response_lines)
    print(f"OCEAN_RESPONSE:{json.dumps({'content': response_content})}", flush=True)


if __name__ == "__main__":
    main()
