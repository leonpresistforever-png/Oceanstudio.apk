import type { AgentCombo, AgentMember, WorkflowPhaseRecord } from '../agents/types';
import type { AgentLogEntry } from '../store/appStore';
import {
  filterMcpForMember,
  filterPluginsForMember,
  serializeMemberSkills,
  serializeMemberFunctions,
} from '../agents/memberContext';
import { getProviderConnection } from '../providers/registry';
import {
  buildDesignerTask,
  enrichMemberForContext,
  isDesignFocusedPrompt,
} from '../agents/archetypeDefaults';
import { buildFusedMember, validateFusionMembers } from '../agents/fusion';

export interface OrchestratorCallbacks {
  onLog: (entry: Omit<AgentLogEntry, 'id' | 'timestamp'> & { id?: string; timestamp?: number }) => void;
  onAgentSend: (message: string, context: Record<string, unknown>) => Promise<string>;
  onRunStart?: (combo: AgentCombo, prompt: string) => string;
  onPhaseStart?: (runId: string, phase: WorkflowPhaseRecord) => void;
  onPhaseEnd?: (runId: string, phaseId: string, status: 'completed' | 'failed') => void;
  onRunComplete?: (runId: string, success: boolean) => void;
  maxConcurrentWorkers?: number;
}

export interface WorkflowResult {
  mode: 'collaborative' | 'parallel' | 'fusion';
  memberOutputs: { memberId: string; memberName: string; output: string; status: 'completed' | 'failed' }[];
  synthesis: string;
  fusedMemberId?: string;
}

function phaseId(): string {
  return crypto.randomUUID();
}

function trackPhase(
  callbacks: OrchestratorCallbacks,
  runId: string | undefined,
  label: string,
  agentName: string
): { id: string; end: (status: 'completed' | 'failed') => void } | null {
  if (!runId || !callbacks.onPhaseStart) return null;
  const id = phaseId();
  const record: WorkflowPhaseRecord = {
    id,
    label,
    agentName,
    status: 'running',
    startedAt: Date.now(),
  };
  callbacks.onPhaseStart(runId, record);
  return {
    id,
    end: (status) => callbacks.onPhaseEnd?.(runId, id, status),
  };
}

/** Decompose prompt into sub-tasks for parallel workers */
function decomposeTasks(prompt: string, workers: AgentMember[]): { memberId: string; task: string }[] {
  const keywords = ['build', 'fix', 'test', 'refactor', 'design', 'document', 'deploy', 'review'];
  const lower = prompt.toLowerCase();

  return workers.map((w, i) => {
    const focus = w.taskAssignment || w.name;
    let task = prompt;

    if (workers.length > 1) {
      if (lower.includes('review') && w.role === 'reviewer') {
        task = `Review and validate: ${prompt}`;
      } else if (w.archetype === 'designer' && isDesignFocusedPrompt(prompt)) {
        task = buildDesignerTask(prompt, focus);
      } else if (lower.includes('ui') && (w.archetype === 'designer' || focus.toLowerCase().includes('flash'))) {
        task = buildDesignerTask(prompt, focus);
      } else if (keywords.some((k) => lower.includes(k))) {
        const matched = keywords.find((k) => lower.includes(k));
        task = `[${matched}] ${focus}: ${prompt}`;
      } else {
        task = `${focus} — perspective ${i + 1}/${workers.length}: ${prompt}`;
      }
    }

    return { memberId: w.id, task };
  });
}

/** Build per-member context slice */
function buildMemberContext(
  member: AgentMember,
  combo: AgentCombo,
  baseContext: Record<string, unknown>,
  task: string
): Record<string, unknown> {
  const enrichedMember = enrichMemberForContext(member);
  const allMcp = (baseContext.mcpServers ?? {}) as Record<string, unknown>;
  const allPlugins = (baseContext.pluginTools ?? []) as { pluginId: string; name: string; description: string }[];
  const { mcpServers, connectedCount } = filterMcpForMember(allMcp, enrichedMember);
  const pluginTools = filterPluginsForMember(allPlugins, enrichedMember);
  const serializedSkills = serializeMemberSkills(enrichedMember);
  const functionTools = serializeMemberFunctions(enrichedMember);

  return {
    ...baseContext,
    mcpServers: enrichedMember.access.mcp ? mcpServers : {},
    mcpMeta: { connectedCount, memberScoped: true },
    pluginTools: enrichedMember.access.plugins ? pluginTools : [],
    terminalType: enrichedMember.access.terminal ? baseContext.terminalType : undefined,
    multiAgentMember: {
      memberId: enrichedMember.id,
      memberName: enrichedMember.name,
      role: enrichedMember.role,
      archetype: enrichedMember.archetype,
      categoryId: enrichedMember.categoryId,
      providerId: enrichedMember.providerId,
      modelId: enrichedMember.modelId,
      task,
      systemPrompt: enrichedMember.systemPrompt,
      tools: enrichedMember.tools,
      skills: enrichedMember.skills,
      skillFiles: enrichedMember.skillFiles.map((f) => f.name),
      params: enrichedMember.params,
      memory: enrichedMember.memory,
      access: enrichedMember.access,
      compatibility: enrichedMember.compatibility,
      paramFilters: enrichedMember.paramFilters,
      assignedMcpIds: enrichedMember.assignedMcpIds,
      assignedPluginIds: enrichedMember.assignedPluginIds,
      serializedSkills,
      functionCalls: functionTools,
    },
    modelConfig: {
      ...(baseContext.modelConfig as Record<string, unknown>),
      modelId: enrichedMember.modelId,
      provider: enrichedMember.providerId,
      selectedModelId: `${enrichedMember.providerId}.${enrichedMember.modelId}`,
      temperature: enrichedMember.params.temperature,
      topP: enrichedMember.params.topP,
      maxTokens: enrichedMember.params.maxTokens,
      thinkingLevel: enrichedMember.params.thinkingLevel,
      compatibilityMode: enrichedMember.compatibility,
      customInstructions: [enrichedMember.systemPrompt, serializedSkills, task].filter(Boolean).join('\n\n'),
      tools: functionTools.length > 0 ? functionTools : undefined,
    },
    comboMode: combo.mode,
    comboName: combo.name,
  };
}

/**
 * Collaborative workflow — all agents receive the same prompt/context and work simultaneously.
 * Outputs are merged into a unified response.
 */
export async function runCollaborativeWorkflow(
  combo: AgentCombo,
  prompt: string,
  baseContext: Record<string, unknown>,
  callbacks: OrchestratorCallbacks,
  systemConfig?: { collaborativeMergePrompt: string },
  runId?: string
): Promise<WorkflowResult> {
  const members = combo.members.filter((m) => m.enabled);
  const outputs: WorkflowResult['memberOutputs'] = [];
  const maxWorkers = callbacks.maxConcurrentWorkers ?? 3;

  callbacks.onLog({
    type: 'action',
    summary: `Collaborative team: ${combo.name} (${members.length} agents)`,
    detail: members.map((m) => `• ${m.name} (${m.providerName} / ${m.modelLabel})`).join('\n'),
    status: 'running',
    metadata: { comboId: combo.id, mode: 'collaborative' },
  });

  const runMember = async (member: AgentMember, memberIndex: number) => {
    const memberCtx = buildMemberContext(member, combo, baseContext, prompt);
    const tracker = trackPhase(callbacks, runId, 'Collaborative pass', member.name);

    callbacks.onLog({
      type: 'thought',
      summary: `${member.name} analyzing (collaborative)`,
      detail: `Provider: ${member.providerName}\nModel: ${member.modelLabel}`,
      status: 'running',
      metadata: { memberId: member.id, providerId: member.providerId },
    });

    try {
      const output = await callbacks.onAgentSend(
        `[Collaborative — ${member.name}] ${prompt}`,
        { ...memberCtx, _multiAgentPhase: 'collaborative', _multiAgentMemberIndex: memberIndex, _suppressHeadSynthesis: true }
      );
      tracker?.end('completed');
      outputs.push({ memberId: member.id, memberName: member.name, output, status: 'completed' });
    } catch (err) {
      tracker?.end('failed');
      outputs.push({
        memberId: member.id,
        memberName: member.name,
        output: err instanceof Error ? err.message : 'Failed',
        status: 'failed',
      });
    }
  };

  // Run in batches respecting maxConcurrentWorkers
  for (let i = 0; i < members.length; i += maxWorkers) {
    await Promise.all(
      members.slice(i, i + maxWorkers).map((m, idx) => runMember(m, i + idx))
    );
  }

  const mergePrompt = [
    systemConfig?.collaborativeMergePrompt ?? 'Merge all agent perspectives into one coherent response.',
    '',
    `Original: ${prompt}`,
    '',
    ...outputs.map((o) => `### ${o.memberName} [${o.status}]\n${o.output}`),
  ].join('\n');

  let synthesis = mergePrompt;
  const head = combo.members.find((m) => m.id === combo.headAgentId && m.enabled) ?? members[0];
  if (head) {
    const headCtx = buildMemberContext(head, combo, baseContext, mergePrompt);
    try {
      synthesis = await callbacks.onAgentSend(mergePrompt, {
        ...headCtx,
        _multiAgentPhase: 'collaborative-merge',
      });
    } catch {
      synthesis = mergePrompt;
    }
  }

  callbacks.onLog({
    type: 'result',
    summary: 'Collaborative merge complete',
    detail: synthesis,
    status: 'completed',
    metadata: { memberCount: members.length },
  });

  return { mode: 'collaborative', memberOutputs: outputs, synthesis };
}

/**
 * Parallel workflow — head agent coordinates; workers get decomposed sub-tasks.
 * Head surfaces final decisions to the user.
 */
export async function runParallelWorkflow(
  combo: AgentCombo,
  prompt: string,
  baseContext: Record<string, unknown>,
  callbacks: OrchestratorCallbacks,
  systemConfig: { headSynthesisPrompt: string },
  runId?: string
): Promise<WorkflowResult> {
  const head = combo.members.find((m) => m.id === combo.headAgentId && m.enabled);
  const workers = combo.members.filter((m) => m.enabled && m.id !== combo.headAgentId);
  const outputs: WorkflowResult['memberOutputs'] = [];

  if (!head) {
    throw new Error('Parallel mode requires a head agent');
  }

  callbacks.onLog({
    type: 'action',
    summary: `Parallel team: ${head.name} + ${workers.length} sub-agents`,
    detail: `Head: ${head.name} (${head.modelLabel})\nWorkers: ${workers.map((w) => w.name).join(', ') || 'none'}`,
    status: 'running',
    metadata: { comboId: combo.id, mode: 'parallel', headAgentId: head.id },
  });

  // Phase 1 — Head plans
  const planPrompt = `You are the head agent. Decompose this request for your team:\n\n${prompt}\n\nTeam: ${workers.map((w) => `${w.name} (${w.taskAssignment || w.role})`).join('; ')}`;
  const headPlanCtx = buildMemberContext(head, combo, baseContext, planPrompt);
  const planTracker = trackPhase(callbacks, runId, 'Planning', head.name);

  callbacks.onLog({
    type: 'thought',
    summary: `${head.name} planning team tasks`,
    detail: systemConfig.headSynthesisPrompt,
    status: 'running',
    metadata: { memberId: head.id, phase: 'plan' },
  });

  await callbacks.onAgentSend(planPrompt, { ...headPlanCtx, _multiAgentPhase: 'parallel-plan' });
  planTracker?.end('completed');

  const tasks = decomposeTasks(prompt, workers);
  const maxWorkers = callbacks.maxConcurrentWorkers ?? 3;

  const runWorker = async ({ memberId, task, workerIndex }: { memberId: string; task: string; workerIndex: number }) => {
    const member = workers.find((w) => w.id === memberId);
    if (!member) return;

    const workerTracker = trackPhase(callbacks, runId, `Worker: ${task.slice(0, 40)}`, member.name);
    callbacks.onLog({
      type: 'todo',
      summary: `${member.name}: ${task.slice(0, 80)}${task.length > 80 ? '...' : ''}`,
      status: 'running',
      metadata: { memberId: member.id, phase: 'worker' },
    });

    const memberCtx = buildMemberContext(member, combo, baseContext, task);
    try {
      const output = await callbacks.onAgentSend(
        `[Sub-agent — ${member.name}] ${task}`,
        { ...memberCtx, _multiAgentPhase: 'parallel-worker', _multiAgentMemberIndex: workerIndex, _suppressHeadSynthesis: true }
      );
      workerTracker?.end('completed');
      outputs.push({ memberId: member.id, memberName: member.name, output, status: 'completed' });
      callbacks.onLog({ type: 'result', summary: `${member.name} finished`, status: 'completed', metadata: { memberId: member.id } });
    } catch (err) {
      workerTracker?.end('failed');
      outputs.push({
        memberId: member.id,
        memberName: member.name,
        output: err instanceof Error ? err.message : 'Failed',
        status: 'failed',
      });
    }
  };

  for (let i = 0; i < tasks.length; i += maxWorkers) {
    await Promise.all(
      tasks.slice(i, i + maxWorkers).map((t, idx) => runWorker({ ...t, workerIndex: i + idx }))
    );
  }

  // Phase 3 — Head synthesizes and surfaces to user
  const synthesisPrompt = [
    systemConfig.headSynthesisPrompt,
    '',
    `Original request: ${prompt}`,
    '',
    'Sub-agent results:',
    ...outputs.map((o) => `- ${o.memberName} [${o.status}]: ${o.output}`),
    '',
    'Synthesize for the user. Confirm key decisions. Surface only what matters.',
  ].join('\n');

  const headSynthCtx = buildMemberContext(head, combo, baseContext, synthesisPrompt);
  const synthTracker = trackPhase(callbacks, runId, 'Synthesis', head.name);

  callbacks.onLog({
    type: 'thought',
    summary: `${head.name} synthesizing team output`,
    status: 'running',
    metadata: { memberId: head.id, phase: 'synthesize' },
  });

  const synthesisText = await callbacks.onAgentSend(synthesisPrompt, { ...headSynthCtx, _multiAgentPhase: 'parallel-synthesize' });
  synthTracker?.end('completed');

  const synthesis = synthesisText || [
    `## ${head.name} — team synthesis`,
    '',
    ...outputs.map((o) => `### ${o.memberName} (${o.status})\n${o.output}`),
  ].join('\n');

  callbacks.onLog({
    type: 'result',
    summary: 'Parallel workflow complete — head agent surfaced results',
    detail: synthesis,
    status: 'completed',
    metadata: { workerCount: workers.length },
  });

  return { mode: 'parallel', memberOutputs: outputs, synthesis };
}

/**
 * Fusion workflow — merge 2–5 agents into one composite identity.
 * Strategies: identity-merge (single call), capability-union / weighted-vote (parallel + merge).
 */
export async function runFusionWorkflow(
  combo: AgentCombo,
  prompt: string,
  baseContext: Record<string, unknown>,
  callbacks: OrchestratorCallbacks,
  systemConfig: { headSynthesisPrompt: string; collaborativeMergePrompt: string },
  runId?: string
): Promise<WorkflowResult> {
  const fusionConfig = combo.fusionConfig;
  if (!fusionConfig) throw new Error('Fusion mode requires fusionConfig on combo');

  const memberIds = fusionConfig.memberIds.length > 0
    ? fusionConfig.memberIds
    : combo.members.filter((m) => m.enabled).map((m) => m.id);

  const fusionMembers = combo.members.filter((m) => memberIds.includes(m.id) && m.enabled);
  const validation = validateFusionMembers(fusionMembers);
  if (validation) throw new Error(validation);

  const fused = buildFusedMember(fusionMembers, { ...fusionConfig, memberIds });
  const outputs: WorkflowResult['memberOutputs'] = [];

  callbacks.onLog({
    type: 'action',
    summary: `Fusion agent: ${fused.name} (${fusionMembers.length} providers merged)`,
    detail: fusionMembers.map((m) => `• ${m.name} — ${m.providerName}/${m.modelLabel}`).join('\n'),
    status: 'running',
    metadata: { comboId: combo.id, mode: 'fusion', strategy: fusionConfig.strategy },
  });

  const strategy = fusionConfig.strategy;

  if (strategy === 'identity-merge') {
    const tracker = trackPhase(callbacks, runId, 'Fusion (identity)', fused.name);
    const ctx = buildMemberContext(fused, combo, baseContext, prompt);
    try {
      const output = await callbacks.onAgentSend(
        `[Fusion — ${fused.name}] ${prompt}`,
        { ...ctx, _multiAgentPhase: 'fusion', _fusedMember: true, _suppressHeadSynthesis: true }
      );
      tracker?.end('completed');
      outputs.push({ memberId: fused.id, memberName: fused.name, output, status: 'completed' });
      callbacks.onLog({ type: 'result', summary: 'Fusion complete', detail: output, status: 'completed' });
      return { mode: 'fusion', memberOutputs: outputs, synthesis: output, fusedMemberId: fused.id };
    } catch (err) {
      tracker?.end('failed');
      throw err;
    }
  }

  // weighted-vote / capability-union — parallel calls per source member, then merge
  const maxWorkers = callbacks.maxConcurrentWorkers ?? 3;
  const runFusionMember = async (member: AgentMember, idx: number) => {
    const weight = fusionConfig.memberWeights[member.id] ?? 1;
    const task = `[Fusion shard — ${member.name} (w=${weight})] ${prompt}`;
    const tracker = trackPhase(callbacks, runId, `Shard: ${member.name}`, member.name);
    const ctx = buildMemberContext(member, combo, baseContext, task);
    try {
      const output = await callbacks.onAgentSend(task, {
        ...ctx,
        _multiAgentPhase: 'fusion-shard',
        _multiAgentMemberIndex: idx,
        _fusionWeight: weight,
        _suppressHeadSynthesis: true,
      });
      tracker?.end('completed');
      outputs.push({ memberId: member.id, memberName: member.name, output, status: 'completed' });
    } catch (err) {
      tracker?.end('failed');
      outputs.push({
        memberId: member.id,
        memberName: member.name,
        output: err instanceof Error ? err.message : 'Failed',
        status: 'failed',
      });
    }
  };

  for (let i = 0; i < fusionMembers.length; i += maxWorkers) {
    await Promise.all(fusionMembers.slice(i, i + maxWorkers).map((m, idx) => runFusionMember(m, i + idx)));
  }

  const mergePrompt = [
    systemConfig.collaborativeMergePrompt,
    `Fused agent: ${fused.name}`,
    `Strategy: ${strategy} | Routing: ${fusionConfig.providerRouting}`,
    '',
    `User request: ${prompt}`,
    '',
    'Provider shards:',
    ...outputs.map((o) => `### ${o.memberName} [${o.status}] (w=${fusionConfig.memberWeights[o.memberId] ?? 1})\n${o.output}`),
    '',
    'Synthesize into ONE unified response as the fused agent. Resolve conflicts; keep best ideas from each provider.',
  ].join('\n');

  const fusedCtx = buildMemberContext(fused, combo, baseContext, mergePrompt);
  const synthTracker = trackPhase(callbacks, runId, 'Fusion synthesis', fused.name);
  let synthesis = mergePrompt;
  try {
    synthesis = await callbacks.onAgentSend(mergePrompt, {
      ...fusedCtx,
      _multiAgentPhase: 'fusion-synthesize',
      _fusedMember: true,
    });
    synthTracker?.end('completed');
  } catch {
    synthTracker?.end('failed');
  }

  callbacks.onLog({
    type: 'result',
    summary: `Fusion merge complete — ${fused.name}`,
    detail: synthesis,
    status: 'completed',
    metadata: { shardCount: fusionMembers.length },
  });

  return { mode: 'fusion', memberOutputs: outputs, synthesis, fusedMemberId: fused.id };
}

/** Route to collaborative, parallel, or fusion based on combo mode */
export async function runMultiAgentWorkflow(
  combo: AgentCombo,
  prompt: string,
  baseContext: Record<string, unknown>,
  callbacks: OrchestratorCallbacks,
  systemConfig: { headSynthesisPrompt: string; collaborativeMergePrompt: string; maxParallelWorkers?: number }
): Promise<WorkflowResult> {
  const enrichedCallbacks = {
    ...callbacks,
    maxConcurrentWorkers: callbacks.maxConcurrentWorkers ?? systemConfig.maxParallelWorkers ?? 3,
  };
  const runId = callbacks.onRunStart?.(combo, prompt);
  try {
    const result = combo.mode === 'fusion'
      ? await runFusionWorkflow(combo, prompt, baseContext, enrichedCallbacks, systemConfig, runId)
      : combo.mode === 'collaborative'
      ? await runCollaborativeWorkflow(combo, prompt, baseContext, enrichedCallbacks, systemConfig, runId)
      : await runParallelWorkflow(combo, prompt, baseContext, enrichedCallbacks, systemConfig, runId);
    callbacks.onRunComplete?.(runId ?? '', true);
    return result;
  } catch (err) {
    callbacks.onRunComplete?.(runId ?? '', false);
    throw err;
  }
}

/** Serialize combo for agent context injection */
export function serializeComboForAgent(combo: AgentCombo): string {
  const enabled = combo.members.filter((x) => x.enabled);
  const lines = [
    `# Multi-Agent Combo: ${combo.name}`,
    `Mode: ${combo.mode}`,
    `Description: ${combo.description}`,
    `Shared workspace context: ${combo.sharedWorkspaceContext}`,
    `Cloud: proxy=${combo.cloudSettings.useOceanProxy}:${combo.cloudSettings.proxyPort}, terminal=${combo.cloudSettings.terminalType}, agentMode=${combo.cloudSettings.agentMode}`,
    '',
    '## Route Steps',
  ];

  enabled.forEach((m, i) => {
    const conn = getProviderConnection(m.providerId);
    const account = conn?.accountLabel ?? 'dynamic';
    const thinking = m.params.thinkingLevel !== 'off' ? ` (${m.params.thinkingLevel})` : '';
    const isHead = m.id === combo.headAgentId;
    lines.push(
      `${i + 1}. **${m.providerName}** / **${m.modelLabel}**${thinking} · account: \`${account}\`${isHead ? ' · **HEAD**' : ''}`,
      `   - Role: ${m.role} · Archetype: ${m.archetype} · Compatibility: ${m.compatibility}`,
      `   - Provider id: \`${m.providerId}\` · Model id: \`${m.modelId}\``,
      `   - Params: temp=${m.params.temperature}, top_p=${m.params.topP}, max_tokens=${m.params.maxTokens}, thinking=${m.params.thinkingLevel}, stream=${m.params.stream}`,
      `   - Memory: ${m.memory.backend}, window=${m.memory.contextWindow}, max_msgs=${m.memory.maxMessages}`,
      `   - Access: terminal=${m.access.terminal}, mcp=${m.access.mcp} (${m.access.mcpMode}), plugins=${m.access.plugins} (${m.access.pluginMode})`,
      `   - Task: ${m.taskAssignment || 'general'}`,
      `   - Tools: ${m.tools.map((t) => t.name).join(', ') || 'provider default'}`,
      `   - Skills: ${m.skills.map((s) => s.name).join(', ') || 'vault injected'}`,
      `   - Function calls: ${m.functionCalls.filter((f) => f.enabled).map((f) => f.name).join(', ') || 'none'}`,
    );
    if (m.systemPrompt?.trim()) {
      lines.push(`   - System prompt: ${m.systemPrompt.slice(0, 300)}${m.systemPrompt.length > 300 ? '…' : ''}`);
    }
    lines.push('');
  });

  lines.push('## Team Summary');
  for (const m of enabled) {
    lines.push(`- ${m.name} (${m.role}): ${m.providerName} / ${m.modelLabel}`);
  }

  if (combo.mode === 'parallel') {
    const head = combo.members.find((m) => m.id === combo.headAgentId);
    lines.push('', `**Head agent:** ${head?.name ?? 'unset'} — decomposes tasks, synthesizes worker outputs`);
    lines.push('**Workers:** execute sub-tasks with scoped MCP/plugin access');
  } else {
    lines.push('', '**Collaborative:** all agents receive identical prompt; outputs merge');
  }

  return lines.join('\n');
}
