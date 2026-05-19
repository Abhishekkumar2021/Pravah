import { create } from "zustand";
import { immer } from "zustand/middleware/immer";
import type { Node, Edge, XYPosition, Connection } from "@xyflow/react";
import type { StageDefinition } from "@/lib/api";

// ─────────────────────────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────────────────────────

export type StageType = "sql" | "container" | "python" | "echo" | "dbt" | "spark";

export type StageNodeData = {
  stage: StageDefinition;
  label: string;
  stageType: StageType;
};

export type ValidationError = {
  nodeId?: string;
  edgeId?: string;
  field?: string;
  message: string;
  severity: "error" | "warning";
};

type Snapshot = {
  nodes: Node<StageNodeData>[];
  edges: Edge[];
};

export type EditorState = {
  // Core data
  nodes: Node<StageNodeData>[];
  edges: Edge[];
  
  // Selection
  selectedNodeId: string | null;
  selectedEdgeId: string | null;
  
  // Clipboard
  clipboard: StageDefinition | null;
  
  // History (undo/redo)
  history: Snapshot[];
  future: Snapshot[];
  
  // Validation
  validationErrors: ValidationError[];
  
  // Dirty state
  isDirty: boolean;
  
  // Pipeline metadata
  pipelineId: string;
  pipelineName: string;
  pipelineDescription: string | null;
  
  // Counter for unique IDs
  stageCounter: number;
};

export type EditorActions = {
  // Initialization
  initialize: (
    pipelineId: string,
    pipelineName: string,
    pipelineDescription: string | null,
    stages: StageDefinition[]
  ) => void;
  reset: () => void;
  
  // Node operations
  addNode: (type: StageType, position?: XYPosition) => void;
  updateNode: (id: string, patch: Partial<StageDefinition>) => void;
  deleteNode: (id: string) => void;
  selectNode: (id: string | null) => void;
  updateNodePosition: (id: string, position: XYPosition) => void;
  
  // Edge operations
  connectNodes: (connection: Connection) => void;
  deleteEdge: (id: string) => void;
  selectEdge: (id: string | null) => void;
  
  // React Flow callbacks
  onNodesChange: (changes: import("@xyflow/react").NodeChange<Node<StageNodeData>>[]) => void;
  onEdgesChange: (changes: import("@xyflow/react").EdgeChange<Edge>[]) => void;
  
  // Clipboard
  copySelectedNode: () => void;
  pasteNode: (position?: XYPosition) => void;
  
  // History
  undo: () => void;
  redo: () => void;
  pushHistory: () => void;
  
  // Validation
  validate: () => ValidationError[];
  clearValidation: () => void;
  
  // Export
  getStages: () => StageDefinition[];
  toYaml: () => string;
  
  // Selection helpers
  deleteSelected: () => void;
  clearSelection: () => void;
};

// ─────────────────────────────────────────────────────────────────────────────
// Default configs per stage type
// ─────────────────────────────────────────────────────────────────────────────

export const STAGE_TYPE_META: Record<StageType, { label: string; description: string; icon: string }> = {
  sql: { label: "SQL", description: "Query a database connection", icon: "database" },
  container: { label: "Container", description: "Run a Docker image", icon: "container" },
  python: { label: "Python", description: "Execute a Python script", icon: "code" },
  echo: { label: "Echo", description: "Debug / placeholder stage", icon: "message" },
  dbt: { label: "dbt", description: "Run dbt models", icon: "box" },
  spark: { label: "Spark", description: "Submit a Spark job", icon: "sparkles" },
};

export const STAGE_TYPES = Object.keys(STAGE_TYPE_META) as StageType[];

function defaultConfigForType(type: StageType): Record<string, unknown> {
  switch (type) {
    case "sql":
      return { query: "SELECT 1", connectionId: "" };
    case "container":
      return { image: "alpine:3.19", command: ["echo", "hello"], env: [] };
    case "python":
      return { script: "print('hello')", requirements: [], pythonVersion: "3.11" };
    case "dbt":
      return { select: "tag:daily", exclude: "", fullRefresh: false };
    case "spark":
      return { mainClass: "com.example.App", jarPath: "", args: [], sparkConf: {} };
    default:
      return { message: "hello from echo" };
  }
}

const STAGE_NAME_PREFIX: Record<StageType, string> = {
  sql: "Query",
  container: "Run",
  echo: "Echo",
  python: "Script",
  dbt: "Transform",
  spark: "Job",
};

function createStageDefinition(type: StageType, index: number): StageDefinition {
  const prefix = STAGE_NAME_PREFIX[type];
  const id = `${type}-${index}`;
  return {
    id,
    name: `${prefix} ${index}`,
    type,
    dependsOn: [],
    config: defaultConfigForType(type),
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// Conversion helpers
// ─────────────────────────────────────────────────────────────────────────────

function stagesToNodes(stages: StageDefinition[]): Node<StageNodeData>[] {
  const colWidth = 220;
  const rowHeight = 120;
  return stages.map((stage, index) => ({
    id: stage.id,
    type: "stageNode",
    position: { x: (index % 3) * colWidth + 40, y: Math.floor(index / 3) * rowHeight + 40 },
    data: {
      stage,
      label: stage.name ?? stage.id,
      stageType: (stage.type as StageType) ?? "echo",
    },
  }));
}

function stagesToEdges(stages: StageDefinition[]): Edge[] {
  const edges: Edge[] = [];
  for (const stage of stages) {
    for (const dep of stage.dependsOn ?? stage.depends_on ?? []) {
      edges.push({
        id: `${dep}->${stage.id}`,
        source: dep,
        target: stage.id,
        animated: true,
        type: "smoothstep",
      });
    }
  }
  return edges;
}

function nodesToStages(nodes: Node<StageNodeData>[], edges: Edge[]): StageDefinition[] {
  const depsByTarget = new Map<string, string[]>();
  for (const edge of edges) {
    const list = depsByTarget.get(edge.target) ?? [];
    list.push(edge.source);
    depsByTarget.set(edge.target, list);
  }
  return nodes.map((node) => ({
    ...node.data.stage,
    dependsOn: depsByTarget.get(node.data.stage.id) ?? [],
  }));
}

// ─────────────────────────────────────────────────────────────────────────────
// Validation logic
// ─────────────────────────────────────────────────────────────────────────────

function detectCycles(nodes: Node<StageNodeData>[], edges: Edge[]): ValidationError[] {
  const errors: ValidationError[] = [];
  const adjacency = new Map<string, string[]>();
  
  for (const edge of edges) {
    const list = adjacency.get(edge.source) ?? [];
    list.push(edge.target);
    adjacency.set(edge.source, list);
  }
  
  const visited = new Set<string>();
  const recStack = new Set<string>();
  
  function dfs(nodeId: string, path: string[]): boolean {
    visited.add(nodeId);
    recStack.add(nodeId);
    
    for (const neighbor of adjacency.get(nodeId) ?? []) {
      if (!visited.has(neighbor)) {
        if (dfs(neighbor, [...path, neighbor])) return true;
      } else if (recStack.has(neighbor)) {
        errors.push({
          message: `Cycle detected: ${[...path, neighbor].join(" → ")}`,
          severity: "error",
        });
        return true;
      }
    }
    
    recStack.delete(nodeId);
    return false;
  }
  
  for (const node of nodes) {
    if (!visited.has(node.id)) {
      dfs(node.id, [node.id]);
    }
  }
  
  return errors;
}

function checkMissingDependencies(nodes: Node<StageNodeData>[], edges: Edge[]): ValidationError[] {
  const errors: ValidationError[] = [];
  const nodeIds = new Set(nodes.map((n) => n.id));
  
  for (const edge of edges) {
    if (!nodeIds.has(edge.source)) {
      errors.push({
        edgeId: edge.id,
        message: `Missing dependency: "${edge.source}" does not exist`,
        severity: "error",
      });
    }
    if (!nodeIds.has(edge.target)) {
      errors.push({
        edgeId: edge.id,
        message: `Missing target: "${edge.target}" does not exist`,
        severity: "error",
      });
    }
  }
  
  return errors;
}

function validateNodeConfigs(nodes: Node<StageNodeData>[]): ValidationError[] {
  const errors: ValidationError[] = [];
  
  for (const node of nodes) {
    const { stage } = node.data;
    const type = stage.type as StageType;
    const config = stage.config ?? {};
    
    // ID validation
    if (!stage.id || stage.id.trim() === "") {
      errors.push({
        nodeId: node.id,
        field: "id",
        message: "Stage ID is required",
        severity: "error",
      });
    } else if (!/^[a-zA-Z][a-zA-Z0-9_-]*$/.test(stage.id)) {
      errors.push({
        nodeId: node.id,
        field: "id",
        message: "Stage ID must start with a letter and contain only letters, numbers, underscores, or hyphens",
        severity: "error",
      });
    }
    
    // Type-specific validation
    switch (type) {
      case "sql":
        if (!config.query || (config.query as string).trim() === "") {
          errors.push({
            nodeId: node.id,
            field: "config.query",
            message: "SQL query is required",
            severity: "error",
          });
        }
        break;
        
      case "container":
        if (!config.image || (config.image as string).trim() === "") {
          errors.push({
            nodeId: node.id,
            field: "config.image",
            message: "Container image is required",
            severity: "error",
          });
        }
        break;
        
      case "python":
        if (!config.script || (config.script as string).trim() === "") {
          errors.push({
            nodeId: node.id,
            field: "config.script",
            message: "Python script is required",
            severity: "error",
          });
        }
        break;
        
      case "dbt":
        if (!config.select || (config.select as string).trim() === "") {
          errors.push({
            nodeId: node.id,
            field: "config.select",
            message: "dbt select expression is required",
            severity: "warning",
          });
        }
        break;
        
      case "spark":
        if (!config.mainClass || (config.mainClass as string).trim() === "") {
          errors.push({
            nodeId: node.id,
            field: "config.mainClass",
            message: "Spark main class is required",
            severity: "error",
          });
        }
        break;
    }
  }
  
  // Check for duplicate IDs
  const ids = nodes.map((n) => n.data.stage.id);
  const seen = new Set<string>();
  for (const id of ids) {
    if (seen.has(id)) {
      errors.push({
        message: `Duplicate stage ID: "${id}"`,
        severity: "error",
      });
    }
    seen.add(id);
  }
  
  return errors;
}

function checkOrphanedNodes(nodes: Node<StageNodeData>[], edges: Edge[]): ValidationError[] {
  const errors: ValidationError[] = [];
  
  if (nodes.length <= 1) return errors;
  
  const connectedNodes = new Set<string>();
  for (const edge of edges) {
    connectedNodes.add(edge.source);
    connectedNodes.add(edge.target);
  }
  
  // Find nodes that are neither source nor target of any edge
  // First node (likely the entry point) doesn't need incoming edges
  const sortedNodes = [...nodes].sort((a, b) => a.position.y - b.position.y || a.position.x - b.position.x);
  
  for (let i = 1; i < sortedNodes.length; i++) {
    const node = sortedNodes[i];
    if (!connectedNodes.has(node.id)) {
      errors.push({
        nodeId: node.id,
        message: `Stage "${node.data.label}" is not connected to the workflow`,
        severity: "warning",
      });
    }
  }
  
  return errors;
}

// ─────────────────────────────────────────────────────────────────────────────
// YAML Export
// ─────────────────────────────────────────────────────────────────────────────

function yamlQuote(value: string): string {
  if (/^[a-zA-Z0-9_-]+$/.test(value)) {
    return value;
  }
  return JSON.stringify(value);
}

function indentBlock(text: string, spaces: number): string {
  const pad = " ".repeat(spaces);
  return text
    .split("\n")
    .map((line) => (line.length > 0 ? pad + line : line))
    .join("\n");
}

function configToYaml(config: Record<string, unknown>, indent: number): string {
  const pad = " ".repeat(indent);
  const lines: string[] = [];
  for (const [key, value] of Object.entries(config)) {
    if (value === null || value === undefined || value === "") continue;
    if (typeof value === "string") {
      lines.push(`${pad}${key}: ${yamlQuote(value)}`);
    } else if (typeof value === "number" || typeof value === "boolean") {
      lines.push(`${pad}${key}: ${String(value)}`);
    } else if (Array.isArray(value)) {
      if (value.length === 0) continue;
      lines.push(`${pad}${key}:`);
      for (const item of value) {
        if (typeof item === "string") {
          lines.push(`${pad}  - ${yamlQuote(item)}`);
        } else {
          lines.push(`${pad}  - ${JSON.stringify(item)}`);
        }
      }
    } else if (typeof value === "object") {
      const nested = configToYaml(value as Record<string, unknown>, indent + 2);
      if (nested.trim()) {
        lines.push(`${pad}${key}:`);
        lines.push(nested);
      }
    }
  }
  return lines.join("\n");
}

function stageToYaml(stage: StageDefinition): string {
  const lines: string[] = [];
  lines.push(`  - id: ${yamlQuote(stage.id)}`);
  if (stage.name && stage.name !== stage.id) {
    lines.push(`    name: ${yamlQuote(stage.name)}`);
  }
  if (stage.type) {
    lines.push(`    type: ${stage.type}`);
  }
  const deps = stage.dependsOn ?? stage.depends_on ?? [];
  if (deps.length > 0) {
    lines.push("    dependsOn:");
    for (const d of deps) {
      lines.push(`      - ${yamlQuote(d)}`);
    }
  }
  if (stage.config && Object.keys(stage.config).length > 0) {
    lines.push("    config:");
    lines.push(indentBlock(configToYaml(stage.config, 6), 0));
  }
  return lines.join("\n");
}

function stagesToYaml(
  pipelineName: string,
  stages: StageDefinition[],
  description?: string | null,
): string {
  const header: string[] = [`name: ${yamlQuote(pipelineName)}`];
  if (description?.trim()) {
    header.push(`description: ${yamlQuote(description.trim())}`);
  }
  header.push("stages:");
  if (stages.length === 0) {
    header.push("  []");
  } else {
    for (const stage of stages) {
      header.push(stageToYaml(stage));
    }
  }
  return `${header.join("\n")}\n`;
}

// ─────────────────────────────────────────────────────────────────────────────
// Initial state
// ─────────────────────────────────────────────────────────────────────────────

const initialState: EditorState = {
  nodes: [],
  edges: [],
  selectedNodeId: null,
  selectedEdgeId: null,
  clipboard: null,
  history: [],
  future: [],
  validationErrors: [],
  isDirty: false,
  pipelineId: "",
  pipelineName: "",
  pipelineDescription: null,
  stageCounter: 1,
};

// ─────────────────────────────────────────────────────────────────────────────
// Store
// ─────────────────────────────────────────────────────────────────────────────

export const useEditorStore = create<EditorState & EditorActions>()(
  immer((set, get) => ({
    ...initialState,

    initialize: (pipelineId, pipelineName, pipelineDescription, stages) => {
      set((state) => {
        state.pipelineId = pipelineId;
        state.pipelineName = pipelineName;
        state.pipelineDescription = pipelineDescription;
        state.nodes = stagesToNodes(stages);
        state.edges = stagesToEdges(stages);
        state.stageCounter = stages.length + 1;
        state.history = [];
        state.future = [];
        state.isDirty = false;
        state.validationErrors = [];
        state.selectedNodeId = null;
        state.selectedEdgeId = null;
        state.clipboard = null;
      });
    },

    reset: () => set(initialState),

    addNode: (type, position) => {
      const { stageCounter, nodes } = get();
      const stage = createStageDefinition(type, stageCounter);
      
      const pos = position ?? {
        x: 80 + (nodes.length % 3) * 200,
        y: 80 + Math.floor(nodes.length / 3) * 120,
      };
      
      get().pushHistory();
      
      set((state) => {
        state.nodes.push({
          id: stage.id,
          type: "stageNode",
          position: pos,
          data: { stage, label: stage.name ?? stage.id, stageType: type },
        });
        state.stageCounter++;
        state.isDirty = true;
        state.selectedNodeId = stage.id;
        state.selectedEdgeId = null;
      });
      
      get().validate();
    },

    updateNode: (id, patch) => {
      get().pushHistory();
      
      set((state) => {
        const nodeIndex = state.nodes.findIndex((n) => n.id === id);
        if (nodeIndex === -1) return;
        
        const node = state.nodes[nodeIndex];
        const oldId = node.id;
        const updatedStage = { ...node.data.stage, ...patch };
        const newId = patch.id?.trim() || oldId;
        
        // Update node
        state.nodes[nodeIndex] = {
          ...node,
          id: newId,
          data: {
            stage: { ...updatedStage, id: newId },
            label: updatedStage.name ?? newId,
            stageType: (updatedStage.type as StageType) ?? "echo",
          },
        };
        
        // Update edges if ID changed
        if (patch.id && patch.id !== oldId) {
          for (const edge of state.edges) {
            if (edge.source === oldId) {
              edge.source = newId;
              edge.id = edge.id.replace(oldId, newId);
            }
            if (edge.target === oldId) {
              edge.target = newId;
              edge.id = edge.id.replace(oldId, newId);
            }
          }
          if (state.selectedNodeId === oldId) {
            state.selectedNodeId = newId;
          }
        }
        
        state.isDirty = true;
      });
      
      get().validate();
    },

    deleteNode: (id) => {
      get().pushHistory();
      
      set((state) => {
        state.nodes = state.nodes.filter((n) => n.id !== id);
        state.edges = state.edges.filter((e) => e.source !== id && e.target !== id);
        if (state.selectedNodeId === id) {
          state.selectedNodeId = null;
        }
        state.isDirty = true;
      });
      
      get().validate();
    },

    selectNode: (id) => {
      set((state) => {
        state.selectedNodeId = id;
        state.selectedEdgeId = null;
      });
    },

    updateNodePosition: (id, position) => {
      set((state) => {
        const node = state.nodes.find((n) => n.id === id);
        if (node) {
          node.position = position;
        }
      });
    },

    connectNodes: (connection) => {
      if (!connection.source || !connection.target) return;
      if (connection.source === connection.target) return;
      
      const { edges } = get();
      const existingEdge = edges.find(
        (e) => e.source === connection.source && e.target === connection.target
      );
      if (existingEdge) return;
      
      get().pushHistory();
      
      set((state) => {
        state.edges.push({
          id: `${connection.source}->${connection.target}`,
          source: connection.source!,
          target: connection.target!,
          animated: true,
          type: "smoothstep",
        });
        state.isDirty = true;
      });
      
      get().validate();
    },

    deleteEdge: (id) => {
      get().pushHistory();
      
      set((state) => {
        state.edges = state.edges.filter((e) => e.id !== id);
        if (state.selectedEdgeId === id) {
          state.selectedEdgeId = null;
        }
        state.isDirty = true;
      });
      
      get().validate();
    },

    selectEdge: (id) => {
      set((state) => {
        state.selectedEdgeId = id;
        state.selectedNodeId = null;
      });
    },

    onNodesChange: (changes) => {
      set((state) => {
        for (const change of changes) {
          if (change.type === "position" && change.position) {
            const node = state.nodes.find((n) => n.id === change.id);
            if (node) {
              node.position = change.position;
            }
          } else if (change.type === "select") {
            if (change.selected) {
              state.selectedNodeId = change.id;
              state.selectedEdgeId = null;
            } else if (state.selectedNodeId === change.id) {
              state.selectedNodeId = null;
            }
          } else if (change.type === "remove") {
            state.nodes = state.nodes.filter((n) => n.id !== change.id);
            state.edges = state.edges.filter((e) => e.source !== change.id && e.target !== change.id);
            if (state.selectedNodeId === change.id) {
              state.selectedNodeId = null;
            }
            state.isDirty = true;
          }
        }
      });
    },

    onEdgesChange: (changes) => {
      set((state) => {
        for (const change of changes) {
          if (change.type === "select") {
            if (change.selected) {
              state.selectedEdgeId = change.id;
              state.selectedNodeId = null;
            } else if (state.selectedEdgeId === change.id) {
              state.selectedEdgeId = null;
            }
          } else if (change.type === "remove") {
            state.edges = state.edges.filter((e) => e.id !== change.id);
            if (state.selectedEdgeId === change.id) {
              state.selectedEdgeId = null;
            }
            state.isDirty = true;
          }
        }
      });
    },

    copySelectedNode: () => {
      const { selectedNodeId, nodes } = get();
      if (!selectedNodeId) return;
      
      const node = nodes.find((n) => n.id === selectedNodeId);
      if (!node) return;
      
      set((state) => {
        state.clipboard = { ...node.data.stage };
      });
    },

    pasteNode: (position) => {
      const { clipboard, stageCounter, nodes } = get();
      if (!clipboard) return;
      
      const newId = `${clipboard.type}-${stageCounter}`;
      const newName = `${clipboard.name ?? clipboard.type} (copy)`;
      
      const pos = position ?? {
        x: 80 + (nodes.length % 3) * 200 + 20,
        y: 80 + Math.floor(nodes.length / 3) * 120 + 20,
      };
      
      get().pushHistory();
      
      set((state) => {
        const newStage: StageDefinition = {
          ...clipboard,
          id: newId,
          name: newName,
          dependsOn: [],
        };
        
        state.nodes.push({
          id: newId,
          type: "stageNode",
          position: pos,
          data: {
            stage: newStage,
            label: newName,
            stageType: (newStage.type as StageType) ?? "echo",
          },
        });
        state.stageCounter++;
        state.isDirty = true;
        state.selectedNodeId = newId;
        state.selectedEdgeId = null;
      });
      
      get().validate();
    },

    pushHistory: () => {
      const { nodes, edges, history } = get();
      const snapshot: Snapshot = {
        nodes: JSON.parse(JSON.stringify(nodes)),
        edges: JSON.parse(JSON.stringify(edges)),
      };
      
      set((state) => {
        state.history = [...history.slice(-49), snapshot];
        state.future = [];
      });
    },

    undo: () => {
      const { history, nodes, edges } = get();
      if (history.length === 0) return;
      
      const prev = history[history.length - 1];
      
      set((state) => {
        state.future = [{ nodes: JSON.parse(JSON.stringify(nodes)), edges: JSON.parse(JSON.stringify(edges)) }, ...state.future];
        state.history = state.history.slice(0, -1);
        state.nodes = prev.nodes;
        state.edges = prev.edges;
        state.selectedNodeId = null;
        state.selectedEdgeId = null;
      });
      
      get().validate();
    },

    redo: () => {
      const { future, nodes, edges } = get();
      if (future.length === 0) return;
      
      const next = future[0];
      
      set((state) => {
        state.history = [...state.history, { nodes: JSON.parse(JSON.stringify(nodes)), edges: JSON.parse(JSON.stringify(edges)) }];
        state.future = state.future.slice(1);
        state.nodes = next.nodes;
        state.edges = next.edges;
        state.selectedNodeId = null;
        state.selectedEdgeId = null;
      });
      
      get().validate();
    },

    validate: () => {
      const { nodes, edges } = get();
      
      const errors = [
        ...detectCycles(nodes, edges),
        ...checkMissingDependencies(nodes, edges),
        ...validateNodeConfigs(nodes),
        ...checkOrphanedNodes(nodes, edges),
      ];
      
      set((state) => {
        state.validationErrors = errors;
      });
      
      return errors;
    },

    clearValidation: () => {
      set((state) => {
        state.validationErrors = [];
      });
    },

    getStages: () => {
      const { nodes, edges } = get();
      return nodesToStages(nodes, edges);
    },

    toYaml: () => {
      const { pipelineName, pipelineDescription, nodes, edges } = get();
      const stages = nodesToStages(nodes, edges);
      return stagesToYaml(pipelineName, stages, pipelineDescription);
    },

    deleteSelected: () => {
      const { selectedNodeId, selectedEdgeId } = get();
      if (selectedNodeId) {
        get().deleteNode(selectedNodeId);
      } else if (selectedEdgeId) {
        get().deleteEdge(selectedEdgeId);
      }
    },

    clearSelection: () => {
      set((state) => {
        state.selectedNodeId = null;
        state.selectedEdgeId = null;
      });
    },
  }))
);

// Selectors
export const selectCanUndo = (state: EditorState) => state.history.length > 0;
export const selectCanRedo = (state: EditorState) => state.future.length > 0;
export const selectHasErrors = (state: EditorState) => 
  state.validationErrors.some((e) => e.severity === "error");
export const selectSelectedNode = (state: EditorState & EditorActions) =>
  state.nodes.find((n) => n.id === state.selectedNodeId) ?? null;
export const selectSelectedEdge = (state: EditorState & EditorActions) =>
  state.edges.find((e) => e.id === state.selectedEdgeId) ?? null;
