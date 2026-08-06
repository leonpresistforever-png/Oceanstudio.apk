// AUTO-GENERATED — 51 builtin skills. Run: node scripts/generate-skills-catalog.mjs
import type { SkillDefinition } from './types';

export const BUILTIN_SKILLS: SkillDefinition[] = [
  {
    "id": "builtin.terminal-routing",
    "name": "Terminal Routing",
    "description": "Terminal Routing — ready-to-install agent skill for coding",
    "category": "coding",
    "scope": "main",
    "tags": [
      "terminal",
      "shell"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-terminal-routing\ndescription: Terminal Routing skill for Ocean.studio agents — terminal, shell.\n---\n\n# Terminal Routing\n\nUse this skill when working on **terminal routing** tasks in Ocean.studio.\n\n## When to use\n- User asks about terminal or related coding workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for coding tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- terminal\n- shell\n"
  },
  {
    "id": "builtin.code-review",
    "name": "Code Review",
    "description": "Code Review — ready-to-install agent skill for coding",
    "category": "coding",
    "scope": "main",
    "tags": [
      "review",
      "quality"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-code-review\ndescription: Code Review skill for Ocean.studio agents — review, quality.\n---\n\n# Code Review\n\nUse this skill when working on **code review** tasks in Ocean.studio.\n\n## When to use\n- User asks about review or related coding workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for coding tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- review\n- quality\n"
  },
  {
    "id": "builtin.refactor-safe",
    "name": "Safe Refactoring",
    "description": "Safe Refactoring — ready-to-install agent skill for coding",
    "category": "coding",
    "scope": "main",
    "tags": [
      "refactor"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-refactor-safe\ndescription: Safe Refactoring skill for Ocean.studio agents — refactor.\n---\n\n# Safe Refactoring\n\nUse this skill when working on **safe refactoring** tasks in Ocean.studio.\n\n## When to use\n- User asks about refactor or related coding workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for coding tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- refactor\n"
  },
  {
    "id": "builtin.git-workflow",
    "name": "Git Workflow",
    "description": "Git Workflow — ready-to-install agent skill for coding",
    "category": "coding",
    "scope": "main",
    "tags": [
      "git",
      "pr"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-git-workflow\ndescription: Git Workflow skill for Ocean.studio agents — git, pr.\n---\n\n# Git Workflow\n\nUse this skill when working on **git workflow** tasks in Ocean.studio.\n\n## When to use\n- User asks about git or related coding workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for coding tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- git\n- pr\n"
  },
  {
    "id": "builtin.debug-systematic",
    "name": "Systematic Debugging",
    "description": "Systematic Debugging — ready-to-install agent skill for coding",
    "category": "coding",
    "scope": "main",
    "tags": [
      "debug"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-debug-systematic\ndescription: Systematic Debugging skill for Ocean.studio agents — debug.\n---\n\n# Systematic Debugging\n\nUse this skill when working on **systematic debugging** tasks in Ocean.studio.\n\n## When to use\n- User asks about debug or related coding workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for coding tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- debug\n"
  },
  {
    "id": "builtin.test-driven",
    "name": "Test-Driven Development",
    "description": "Test-Driven Development — ready-to-install agent skill for testing",
    "category": "testing",
    "scope": "main",
    "tags": [
      "tdd",
      "jest"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-test-driven\ndescription: Test-Driven Development skill for Ocean.studio agents — tdd, jest.\n---\n\n# Test-Driven Development\n\nUse this skill when working on **test-driven development** tasks in Ocean.studio.\n\n## When to use\n- User asks about tdd or related testing workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for testing tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- tdd\n- jest\n"
  },
  {
    "id": "builtin.api-design",
    "name": "API Design",
    "description": "API Design — ready-to-install agent skill for api",
    "category": "api",
    "scope": "main",
    "tags": [
      "rest",
      "openapi"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-api-design\ndescription: API Design skill for Ocean.studio agents — rest, openapi.\n---\n\n# API Design\n\nUse this skill when working on **api design** tasks in Ocean.studio.\n\n## When to use\n- User asks about rest or related api workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for api tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- rest\n- openapi\n"
  },
  {
    "id": "builtin.security-audit",
    "name": "Security Audit",
    "description": "Security Audit — ready-to-install agent skill for security",
    "category": "security",
    "scope": "main",
    "tags": [
      "owasp"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-security-audit\ndescription: Security Audit skill for Ocean.studio agents — owasp.\n---\n\n# Security Audit\n\nUse this skill when working on **security audit** tasks in Ocean.studio.\n\n## When to use\n- User asks about owasp or related security workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for security tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- owasp\n"
  },
  {
    "id": "builtin.firebase-setup",
    "name": "Firebase Setup",
    "description": "Firebase Setup — ready-to-install agent skill for firebase",
    "category": "firebase",
    "scope": "main",
    "tags": [
      "firebase"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-firebase-setup\ndescription: Firebase Setup skill for Ocean.studio agents — firebase.\n---\n\n# Firebase Setup\n\nUse this skill when working on **firebase setup** tasks in Ocean.studio.\n\n## When to use\n- User asks about firebase or related firebase workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for firebase tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- firebase\n"
  },
  {
    "id": "builtin.firestore-queries",
    "name": "Firestore Queries",
    "description": "Firestore Queries — ready-to-install agent skill for firebase",
    "category": "firebase",
    "scope": "main",
    "tags": [
      "firestore"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-firestore-queries\ndescription: Firestore Queries skill for Ocean.studio agents — firestore.\n---\n\n# Firestore Queries\n\nUse this skill when working on **firestore queries** tasks in Ocean.studio.\n\n## When to use\n- User asks about firestore or related firebase workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for firebase tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- firestore\n"
  },
  {
    "id": "builtin.figma-to-code",
    "name": "Figma to Code",
    "description": "Figma to Code — ready-to-install agent skill for figma",
    "category": "figma",
    "scope": "main",
    "tags": [
      "design"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-figma-to-code\ndescription: Figma to Code skill for Ocean.studio agents — design.\n---\n\n# Figma to Code\n\nUse this skill when working on **figma to code** tasks in Ocean.studio.\n\n## When to use\n- User asks about design or related figma workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for figma tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- design\n"
  },
  {
    "id": "builtin.deployment-vercel",
    "name": "Vercel Deploy",
    "description": "Vercel Deploy — ready-to-install agent skill for deployment",
    "category": "deployment",
    "scope": "main",
    "tags": [
      "vercel"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-deployment-vercel\ndescription: Vercel Deploy skill for Ocean.studio agents — vercel.\n---\n\n# Vercel Deploy\n\nUse this skill when working on **vercel deploy** tasks in Ocean.studio.\n\n## When to use\n- User asks about vercel or related deployment workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for deployment tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- vercel\n"
  },
  {
    "id": "builtin.deployment-railway",
    "name": "Railway Deploy",
    "description": "Railway Deploy — ready-to-install agent skill for deployment",
    "category": "deployment",
    "scope": "main",
    "tags": [
      "railway"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-deployment-railway\ndescription: Railway Deploy skill for Ocean.studio agents — railway.\n---\n\n# Railway Deploy\n\nUse this skill when working on **railway deploy** tasks in Ocean.studio.\n\n## When to use\n- User asks about railway or related deployment workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for deployment tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- railway\n"
  },
  {
    "id": "builtin.docker-compose",
    "name": "Docker Compose",
    "description": "Docker Compose — ready-to-install agent skill for deployment",
    "category": "deployment",
    "scope": "main",
    "tags": [
      "docker"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-docker-compose\ndescription: Docker Compose skill for Ocean.studio agents — docker.\n---\n\n# Docker Compose\n\nUse this skill when working on **docker compose** tasks in Ocean.studio.\n\n## When to use\n- User asks about docker or related deployment workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for deployment tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- docker\n"
  },
  {
    "id": "builtin.postgres-schema",
    "name": "PostgreSQL Schema",
    "description": "PostgreSQL Schema — ready-to-install agent skill for database",
    "category": "database",
    "scope": "main",
    "tags": [
      "sql"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-postgres-schema\ndescription: PostgreSQL Schema skill for Ocean.studio agents — sql.\n---\n\n# PostgreSQL Schema\n\nUse this skill when working on **postgresql schema** tasks in Ocean.studio.\n\n## When to use\n- User asks about sql or related database workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for database tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- sql\n"
  },
  {
    "id": "builtin.prisma-migrate",
    "name": "Prisma Migrate",
    "description": "Prisma Migrate — ready-to-install agent skill for database",
    "category": "database",
    "scope": "main",
    "tags": [
      "prisma"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-prisma-migrate\ndescription: Prisma Migrate skill for Ocean.studio agents — prisma.\n---\n\n# Prisma Migrate\n\nUse this skill when working on **prisma migrate** tasks in Ocean.studio.\n\n## When to use\n- User asks about prisma or related database workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for database tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- prisma\n"
  },
  {
    "id": "builtin.react-patterns",
    "name": "React Patterns",
    "description": "React Patterns — ready-to-install agent skill for coding",
    "category": "coding",
    "scope": "main",
    "tags": [
      "react"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-react-patterns\ndescription: React Patterns skill for Ocean.studio agents — react.\n---\n\n# React Patterns\n\nUse this skill when working on **react patterns** tasks in Ocean.studio.\n\n## When to use\n- User asks about react or related coding workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for coding tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- react\n"
  },
  {
    "id": "builtin.nextjs-app-router",
    "name": "Next.js App Router",
    "description": "Next.js App Router — ready-to-install agent skill for coding",
    "category": "coding",
    "scope": "main",
    "tags": [
      "nextjs"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-nextjs-app-router\ndescription: Next.js App Router skill for Ocean.studio agents — nextjs.\n---\n\n# Next.js App Router\n\nUse this skill when working on **next.js app router** tasks in Ocean.studio.\n\n## When to use\n- User asks about nextjs or related coding workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for coding tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- nextjs\n"
  },
  {
    "id": "builtin.electron-ipc",
    "name": "Electron IPC",
    "description": "Electron IPC — ready-to-install agent skill for coding",
    "category": "coding",
    "scope": "main",
    "tags": [
      "electron"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-electron-ipc\ndescription: Electron IPC skill for Ocean.studio agents — electron.\n---\n\n# Electron IPC\n\nUse this skill when working on **electron ipc** tasks in Ocean.studio.\n\n## When to use\n- User asks about electron or related coding workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for coding tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- electron\n"
  },
  {
    "id": "builtin.capacitor-android",
    "name": "Capacitor Android",
    "description": "Capacitor Android — ready-to-install agent skill for coding",
    "category": "coding",
    "scope": "main",
    "tags": [
      "android"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-capacitor-android\ndescription: Capacitor Android skill for Ocean.studio agents — android.\n---\n\n# Capacitor Android\n\nUse this skill when working on **capacitor android** tasks in Ocean.studio.\n\n## When to use\n- User asks about android or related coding workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for coding tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- android\n"
  },
  {
    "id": "builtin.mcp-integration",
    "name": "MCP Integration",
    "description": "MCP Integration — ready-to-install agent skill for api",
    "category": "api",
    "scope": "main",
    "tags": [
      "mcp"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-mcp-integration\ndescription: MCP Integration skill for Ocean.studio agents — mcp.\n---\n\n# MCP Integration\n\nUse this skill when working on **mcp integration** tasks in Ocean.studio.\n\n## When to use\n- User asks about mcp or related api workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for api tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- mcp\n"
  },
  {
    "id": "builtin.provider-oauth",
    "name": "Provider OAuth",
    "description": "Provider OAuth — ready-to-install agent skill for api",
    "category": "api",
    "scope": "main",
    "tags": [
      "oauth"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-provider-oauth\ndescription: Provider OAuth skill for Ocean.studio agents — oauth.\n---\n\n# Provider OAuth\n\nUse this skill when working on **provider oauth** tasks in Ocean.studio.\n\n## When to use\n- User asks about oauth or related api workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for api tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- oauth\n"
  },
  {
    "id": "builtin.gateway-routing",
    "name": "Gateway Routing",
    "description": "Gateway Routing — ready-to-install agent skill for gateway",
    "category": "gateway",
    "scope": "main",
    "tags": [
      "gateway"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-gateway-routing\ndescription: Gateway Routing skill for Ocean.studio agents — gateway.\n---\n\n# Gateway Routing\n\nUse this skill when working on **gateway routing** tasks in Ocean.studio.\n\n## When to use\n- User asks about gateway or related gateway workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for gateway tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- gateway\n"
  },
  {
    "id": "builtin.multi-agent-orch",
    "name": "Multi-Agent Orchestration",
    "description": "Multi-Agent Orchestration — ready-to-install agent skill for multi-agent",
    "category": "multi-agent",
    "scope": "main",
    "tags": [
      "agents"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-multi-agent-orch\ndescription: Multi-Agent Orchestration skill for Ocean.studio agents — agents.\n---\n\n# Multi-Agent Orchestration\n\nUse this skill when working on **multi-agent orchestration** tasks in Ocean.studio.\n\n## When to use\n- User asks about agents or related multi-agent workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for multi-agent tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- agents\n"
  },
  {
    "id": "builtin.research-web",
    "name": "Web Research",
    "description": "Web Research — ready-to-install agent skill for research",
    "category": "research",
    "scope": "both",
    "tags": [
      "research"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-research-web\ndescription: Web Research skill for Ocean.studio agents — research.\n---\n\n# Web Research\n\nUse this skill when working on **web research** tasks in Ocean.studio.\n\n## When to use\n- User asks about research or related research workflows\n- Scope: main workspace and playground\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for research tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- research\n"
  },
  {
    "id": "builtin.firecrawl-scrape",
    "name": "Firecrawl Scrape",
    "description": "Firecrawl Scrape — ready-to-install agent skill for research",
    "category": "research",
    "scope": "both",
    "tags": [
      "scrape"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-firecrawl-scrape\ndescription: Firecrawl Scrape skill for Ocean.studio agents — scrape.\n---\n\n# Firecrawl Scrape\n\nUse this skill when working on **firecrawl scrape** tasks in Ocean.studio.\n\n## When to use\n- User asks about scrape or related research workflows\n- Scope: main workspace and playground\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for research tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- scrape\n"
  },
  {
    "id": "builtin.pdf-extract",
    "name": "PDF Extract",
    "description": "PDF Extract — ready-to-install agent skill for research",
    "category": "research",
    "scope": "both",
    "tags": [
      "pdf"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-pdf-extract\ndescription: PDF Extract skill for Ocean.studio agents — pdf.\n---\n\n# PDF Extract\n\nUse this skill when working on **pdf extract** tasks in Ocean.studio.\n\n## When to use\n- User asks about pdf or related research workflows\n- Scope: main workspace and playground\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for research tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- pdf\n"
  },
  {
    "id": "builtin.image-prompt",
    "name": "Image Prompt Engineering",
    "description": "Image Prompt Engineering — ready-to-install agent skill for media",
    "category": "media",
    "scope": "playground",
    "tags": [
      "image"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-image-prompt\ndescription: Image Prompt Engineering skill for Ocean.studio agents — image.\n---\n\n# Image Prompt Engineering\n\nUse this skill when working on **image prompt engineering** tasks in Ocean.studio.\n\n## When to use\n- User asks about image or related media workflows\n- Scope: playground\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for media tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- image\n"
  },
  {
    "id": "builtin.video-storyboard",
    "name": "Video Storyboard",
    "description": "Video Storyboard — ready-to-install agent skill for media",
    "category": "media",
    "scope": "playground",
    "tags": [
      "video"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-video-storyboard\ndescription: Video Storyboard skill for Ocean.studio agents — video.\n---\n\n# Video Storyboard\n\nUse this skill when working on **video storyboard** tasks in Ocean.studio.\n\n## When to use\n- User asks about video or related media workflows\n- Scope: playground\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for media tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- video\n"
  },
  {
    "id": "builtin.3d-pipeline",
    "name": "3D Pipeline",
    "description": "3D Pipeline — ready-to-install agent skill for media",
    "category": "media",
    "scope": "playground",
    "tags": [
      "3d"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-3d-pipeline\ndescription: 3D Pipeline skill for Ocean.studio agents — 3d.\n---\n\n# 3D Pipeline\n\nUse this skill when working on **3d pipeline** tasks in Ocean.studio.\n\n## When to use\n- User asks about 3d or related media workflows\n- Scope: playground\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for media tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- 3d\n"
  },
  {
    "id": "builtin.music-composition",
    "name": "Music Composition",
    "description": "Music Composition — ready-to-install agent skill for media",
    "category": "media",
    "scope": "playground",
    "tags": [
      "music"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-music-composition\ndescription: Music Composition skill for Ocean.studio agents — music.\n---\n\n# Music Composition\n\nUse this skill when working on **music composition** tasks in Ocean.studio.\n\n## When to use\n- User asks about music or related media workflows\n- Scope: playground\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for media tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- music\n"
  },
  {
    "id": "builtin.tts-voice",
    "name": "TTS Voice Design",
    "description": "TTS Voice Design — ready-to-install agent skill for media",
    "category": "media",
    "scope": "playground",
    "tags": [
      "tts"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-tts-voice\ndescription: TTS Voice Design skill for Ocean.studio agents — tts.\n---\n\n# TTS Voice Design\n\nUse this skill when working on **tts voice design** tasks in Ocean.studio.\n\n## When to use\n- User asks about tts or related media workflows\n- Scope: playground\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for media tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- tts\n"
  },
  {
    "id": "builtin.active-bot-screen",
    "name": "Active Bot Screen Control",
    "description": "Active Bot Screen Control — ready-to-install agent skill for playground",
    "category": "playground",
    "scope": "playground",
    "tags": [
      "bot"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-active-bot-screen\ndescription: Active Bot Screen Control skill for Ocean.studio agents — bot.\n---\n\n# Active Bot Screen Control\n\nUse this skill when working on **active bot screen control** tasks in Ocean.studio.\n\n## When to use\n- User asks about bot or related playground workflows\n- Scope: playground\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for playground tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- bot\n"
  },
  {
    "id": "builtin.game-companion",
    "name": "Game Companion",
    "description": "Game Companion — ready-to-install agent skill for playground",
    "category": "playground",
    "scope": "playground",
    "tags": [
      "game"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-game-companion\ndescription: Game Companion skill for Ocean.studio agents — game.\n---\n\n# Game Companion\n\nUse this skill when working on **game companion** tasks in Ocean.studio.\n\n## When to use\n- User asks about game or related playground workflows\n- Scope: playground\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for playground tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- game\n"
  },
  {
    "id": "builtin.recording-pro",
    "name": "Pro Screen Recording",
    "description": "Pro Screen Recording — ready-to-install agent skill for playground",
    "category": "playground",
    "scope": "playground",
    "tags": [
      "recording"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-recording-pro\ndescription: Pro Screen Recording skill for Ocean.studio agents — recording.\n---\n\n# Pro Screen Recording\n\nUse this skill when working on **pro screen recording** tasks in Ocean.studio.\n\n## When to use\n- User asks about recording or related playground workflows\n- Scope: playground\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for playground tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- recording\n"
  },
  {
    "id": "builtin.schedule-cron",
    "name": "Schedule & Cron",
    "description": "Schedule & Cron — ready-to-install agent skill for productivity",
    "category": "productivity",
    "scope": "both",
    "tags": [
      "schedule"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-schedule-cron\ndescription: Schedule & Cron skill for Ocean.studio agents — schedule.\n---\n\n# Schedule & Cron\n\nUse this skill when working on **schedule & cron** tasks in Ocean.studio.\n\n## When to use\n- User asks about schedule or related productivity workflows\n- Scope: main workspace and playground\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for productivity tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- schedule\n"
  },
  {
    "id": "builtin.notion-sync",
    "name": "Notion Sync",
    "description": "Notion Sync — ready-to-install agent skill for productivity",
    "category": "productivity",
    "scope": "main",
    "tags": [
      "notion"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-notion-sync\ndescription: Notion Sync skill for Ocean.studio agents — notion.\n---\n\n# Notion Sync\n\nUse this skill when working on **notion sync** tasks in Ocean.studio.\n\n## When to use\n- User asks about notion or related productivity workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for productivity tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- notion\n"
  },
  {
    "id": "builtin.linear-issues",
    "name": "Linear Issues",
    "description": "Linear Issues — ready-to-install agent skill for productivity",
    "category": "productivity",
    "scope": "main",
    "tags": [
      "linear"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-linear-issues\ndescription: Linear Issues skill for Ocean.studio agents — linear.\n---\n\n# Linear Issues\n\nUse this skill when working on **linear issues** tasks in Ocean.studio.\n\n## When to use\n- User asks about linear or related productivity workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for productivity tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- linear\n"
  },
  {
    "id": "builtin.stripe-payments",
    "name": "Stripe Payments",
    "description": "Stripe Payments — ready-to-install agent skill for api",
    "category": "api",
    "scope": "main",
    "tags": [
      "stripe"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-stripe-payments\ndescription: Stripe Payments skill for Ocean.studio agents — stripe.\n---\n\n# Stripe Payments\n\nUse this skill when working on **stripe payments** tasks in Ocean.studio.\n\n## When to use\n- User asks about stripe or related api workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for api tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- stripe\n"
  },
  {
    "id": "builtin.aws-lambda",
    "name": "AWS Lambda",
    "description": "AWS Lambda — ready-to-install agent skill for deployment",
    "category": "deployment",
    "scope": "main",
    "tags": [
      "aws"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-aws-lambda\ndescription: AWS Lambda skill for Ocean.studio agents — aws.\n---\n\n# AWS Lambda\n\nUse this skill when working on **aws lambda** tasks in Ocean.studio.\n\n## When to use\n- User asks about aws or related deployment workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for deployment tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- aws\n"
  },
  {
    "id": "builtin.gcp-cloudrun",
    "name": "GCP Cloud Run",
    "description": "GCP Cloud Run — ready-to-install agent skill for deployment",
    "category": "deployment",
    "scope": "main",
    "tags": [
      "gcp"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-gcp-cloudrun\ndescription: GCP Cloud Run skill for Ocean.studio agents — gcp.\n---\n\n# GCP Cloud Run\n\nUse this skill when working on **gcp cloud run** tasks in Ocean.studio.\n\n## When to use\n- User asks about gcp or related deployment workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for deployment tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- gcp\n"
  },
  {
    "id": "builtin.k8s-manifests",
    "name": "Kubernetes Manifests",
    "description": "Kubernetes Manifests — ready-to-install agent skill for deployment",
    "category": "deployment",
    "scope": "main",
    "tags": [
      "k8s"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-k8s-manifests\ndescription: Kubernetes Manifests skill for Ocean.studio agents — k8s.\n---\n\n# Kubernetes Manifests\n\nUse this skill when working on **kubernetes manifests** tasks in Ocean.studio.\n\n## When to use\n- User asks about k8s or related deployment workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for deployment tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- k8s\n"
  },
  {
    "id": "builtin.ci-github-actions",
    "name": "GitHub Actions CI",
    "description": "GitHub Actions CI — ready-to-install agent skill for deployment",
    "category": "deployment",
    "scope": "main",
    "tags": [
      "ci"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-ci-github-actions\ndescription: GitHub Actions CI skill for Ocean.studio agents — ci.\n---\n\n# GitHub Actions CI\n\nUse this skill when working on **github actions ci** tasks in Ocean.studio.\n\n## When to use\n- User asks about ci or related deployment workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for deployment tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- ci\n"
  },
  {
    "id": "builtin.lint-fix",
    "name": "Lint & Fix",
    "description": "Lint & Fix — ready-to-install agent skill for coding",
    "category": "coding",
    "scope": "main",
    "tags": [
      "eslint"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-lint-fix\ndescription: Lint & Fix skill for Ocean.studio agents — eslint.\n---\n\n# Lint & Fix\n\nUse this skill when working on **lint & fix** tasks in Ocean.studio.\n\n## When to use\n- User asks about eslint or related coding workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for coding tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- eslint\n"
  },
  {
    "id": "builtin.accessibility-a11y",
    "name": "Accessibility (a11y)",
    "description": "Accessibility (a11y) — ready-to-install agent skill for coding",
    "category": "coding",
    "scope": "main",
    "tags": [
      "a11y"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-accessibility-a11y\ndescription: Accessibility (a11y) skill for Ocean.studio agents — a11y.\n---\n\n# Accessibility (a11y)\n\nUse this skill when working on **accessibility (a11y)** tasks in Ocean.studio.\n\n## When to use\n- User asks about a11y or related coding workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for coding tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- a11y\n"
  },
  {
    "id": "builtin.i18n-localize",
    "name": "i18n Localization",
    "description": "i18n Localization — ready-to-install agent skill for coding",
    "category": "coding",
    "scope": "main",
    "tags": [
      "i18n"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-i18n-localize\ndescription: i18n Localization skill for Ocean.studio agents — i18n.\n---\n\n# i18n Localization\n\nUse this skill when working on **i18n localization** tasks in Ocean.studio.\n\n## When to use\n- User asks about i18n or related coding workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for coding tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- i18n\n"
  },
  {
    "id": "builtin.performance-profile",
    "name": "Performance Profiling",
    "description": "Performance Profiling — ready-to-install agent skill for coding",
    "category": "coding",
    "scope": "main",
    "tags": [
      "perf"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-performance-profile\ndescription: Performance Profiling skill for Ocean.studio agents — perf.\n---\n\n# Performance Profiling\n\nUse this skill when working on **performance profiling** tasks in Ocean.studio.\n\n## When to use\n- User asks about perf or related coding workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for coding tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- perf\n"
  },
  {
    "id": "builtin.docs-writer",
    "name": "Documentation Writer",
    "description": "Documentation Writer — ready-to-install agent skill for productivity",
    "category": "productivity",
    "scope": "both",
    "tags": [
      "docs"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-docs-writer\ndescription: Documentation Writer skill for Ocean.studio agents — docs.\n---\n\n# Documentation Writer\n\nUse this skill when working on **documentation writer** tasks in Ocean.studio.\n\n## When to use\n- User asks about docs or related productivity workflows\n- Scope: main workspace and playground\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for productivity tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- docs\n"
  },
  {
    "id": "builtin.changelog-gen",
    "name": "Changelog Generator",
    "description": "Changelog Generator — ready-to-install agent skill for productivity",
    "category": "productivity",
    "scope": "main",
    "tags": [
      "changelog"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-changelog-gen\ndescription: Changelog Generator skill for Ocean.studio agents — changelog.\n---\n\n# Changelog Generator\n\nUse this skill when working on **changelog generator** tasks in Ocean.studio.\n\n## When to use\n- User asks about changelog or related productivity workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for productivity tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- changelog\n"
  },
  {
    "id": "builtin.semver-release",
    "name": "Semver Release",
    "description": "Semver Release — ready-to-install agent skill for productivity",
    "category": "productivity",
    "scope": "main",
    "tags": [
      "release"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-semver-release\ndescription: Semver Release skill for Ocean.studio agents — release.\n---\n\n# Semver Release\n\nUse this skill when working on **semver release** tasks in Ocean.studio.\n\n## When to use\n- User asks about release or related productivity workflows\n- Scope: main\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for productivity tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- release\n"
  },
  {
    "id": "builtin.playground-media",
    "name": "Playground Media Jobs",
    "description": "Playground Media Jobs — ready-to-install agent skill for playground",
    "category": "playground",
    "scope": "playground",
    "tags": [
      "media"
    ],
    "source": "builtin",
    "verified": true,
    "content": "---\nname: ocean-playground-media\ndescription: Playground Media Jobs skill for Ocean.studio agents — media.\n---\n\n# Playground Media Jobs\n\nUse this skill when working on **playground media jobs** tasks in Ocean.studio.\n\n## When to use\n- User asks about media or related playground workflows\n- Scope: playground\n\n## Instructions\n1. Read workspace context via `buildAgentContext()`\n2. Use connected providers and MCP tools for playground tasks\n3. Follow Ocean.studio conventions in agent/skill.md\n4. Prefer streaming responses and batch tool calls\n\n## Platform notes\n- Electron: full filesystem + terminal + native shell\n- Web: Cloud Shell terminal\n- Android: proot Linux terminal\n\n## Tags\n- media\n"
  }
] as SkillDefinition[];

export const BUILTIN_SKILL_COUNT = 51;
