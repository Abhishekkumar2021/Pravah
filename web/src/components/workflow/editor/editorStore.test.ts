import { describe, it, expect, beforeEach } from "vitest";
import { act } from "@testing-library/react";
import { useEditorStore } from "./editorStore";

describe("editorStore", () => {
  beforeEach(() => {
    act(() => {
      useEditorStore.getState().initialize({
        pipelineId: "test-pipeline",
        pipelineName: "Test Pipeline",
        pipelineDescription: null,
        stages: [],
      });
    });
  });

  describe("initialize", () => {
    it("initializes with provided pipeline data", () => {
      const state = useEditorStore.getState();
      expect(state.pipelineId).toBe("test-pipeline");
      expect(state.pipelineName).toBe("Test Pipeline");
      expect(state.nodes).toHaveLength(0);
      expect(state.edges).toHaveLength(0);
    });

    it("parses existing stages into nodes and edges", () => {
      act(() => {
        useEditorStore.getState().initialize({
          pipelineId: "p1",
          pipelineName: "Pipeline",
          pipelineDescription: null,
          stages: [
            {
              id: "stage-1",
              name: "Extract",
              type: "sql",
              config: { query: "SELECT * FROM users" },
              dependsOn: [],
            },
            {
              id: "stage-2",
              name: "Transform",
              type: "python",
              config: { script: "transform.py" },
              dependsOn: ["stage-1"],
            },
          ],
        });
      });

      const state = useEditorStore.getState();
      expect(state.nodes).toHaveLength(2);
      expect(state.edges).toHaveLength(1);
      expect(state.edges[0].source).toBe("stage-1");
      expect(state.edges[0].target).toBe("stage-2");
    });
  });

  describe("addNode", () => {
    it("adds a new node with generated id", () => {
      act(() => {
        useEditorStore.getState().addNode("sql", { x: 100, y: 200 });
      });

      const state = useEditorStore.getState();
      expect(state.nodes).toHaveLength(1);
      expect(state.nodes[0].type).toBe("stage");
      expect(state.nodes[0].position).toEqual({ x: 100, y: 200 });
      expect(state.nodes[0].data.stageType).toBe("sql");
    });

    it("pushes to history", () => {
      const initialLength = useEditorStore.getState().history.past.length;
      act(() => {
        useEditorStore.getState().addNode("sql", { x: 0, y: 0 });
      });
      expect(useEditorStore.getState().history.past.length).toBe(initialLength + 1);
    });
  });

  describe("deleteNode", () => {
    it("removes a node and its connected edges", () => {
      act(() => {
        const store = useEditorStore.getState();
        store.addNode("sql", { x: 0, y: 0 });
        store.addNode("python", { x: 200, y: 0 });
      });

      const nodes = useEditorStore.getState().nodes;
      const firstId = nodes[0].id;
      const secondId = nodes[1].id;

      act(() => {
        useEditorStore.getState().connectNodes(firstId, secondId);
      });

      expect(useEditorStore.getState().edges).toHaveLength(1);

      act(() => {
        useEditorStore.getState().deleteNode(firstId);
      });

      const state = useEditorStore.getState();
      expect(state.nodes).toHaveLength(1);
      expect(state.edges).toHaveLength(0);
    });
  });

  describe("connectNodes", () => {
    it("creates an edge between nodes", () => {
      act(() => {
        useEditorStore.getState().addNode("sql", { x: 0, y: 0 });
        useEditorStore.getState().addNode("python", { x: 200, y: 0 });
      });

      const nodes = useEditorStore.getState().nodes;
      act(() => {
        useEditorStore.getState().connectNodes(nodes[0].id, nodes[1].id);
      });

      const state = useEditorStore.getState();
      expect(state.edges).toHaveLength(1);
      expect(state.edges[0].source).toBe(nodes[0].id);
      expect(state.edges[0].target).toBe(nodes[1].id);
    });

    it("prevents duplicate connections", () => {
      act(() => {
        useEditorStore.getState().addNode("sql", { x: 0, y: 0 });
        useEditorStore.getState().addNode("python", { x: 200, y: 0 });
      });

      const nodes = useEditorStore.getState().nodes;
      act(() => {
        useEditorStore.getState().connectNodes(nodes[0].id, nodes[1].id);
        useEditorStore.getState().connectNodes(nodes[0].id, nodes[1].id);
      });

      expect(useEditorStore.getState().edges).toHaveLength(1);
    });
  });

  describe("undo/redo", () => {
    it("undoes the last action", () => {
      act(() => {
        useEditorStore.getState().addNode("sql", { x: 0, y: 0 });
      });

      expect(useEditorStore.getState().nodes).toHaveLength(1);

      act(() => {
        useEditorStore.getState().undo();
      });

      expect(useEditorStore.getState().nodes).toHaveLength(0);
    });

    it("redoes an undone action", () => {
      act(() => {
        useEditorStore.getState().addNode("sql", { x: 0, y: 0 });
        useEditorStore.getState().undo();
      });

      expect(useEditorStore.getState().nodes).toHaveLength(0);

      act(() => {
        useEditorStore.getState().redo();
      });

      expect(useEditorStore.getState().nodes).toHaveLength(1);
    });
  });

  describe("validate", () => {
    it("reports error for cycle", () => {
      act(() => {
        const store = useEditorStore.getState();
        store.addNode("sql", { x: 0, y: 0 });
        store.addNode("python", { x: 200, y: 0 });
      });

      const [n1, n2] = useEditorStore.getState().nodes;

      act(() => {
        const store = useEditorStore.getState();
        store.connectNodes(n1.id, n2.id);
        store.connectNodes(n2.id, n1.id);
        store.validate();
      });

      const errors = useEditorStore.getState().validationErrors;
      const cycleError = errors.find((e) => e.type === "cycle");
      expect(cycleError).toBeDefined();
    });

    it("reports warning for orphaned nodes", () => {
      act(() => {
        useEditorStore.getState().addNode("sql", { x: 0, y: 0 });
        useEditorStore.getState().addNode("python", { x: 200, y: 0 });
        useEditorStore.getState().validate();
      });

      const errors = useEditorStore.getState().validationErrors;
      const orphanWarnings = errors.filter((e) => e.type === "orphan");
      expect(orphanWarnings.length).toBeGreaterThan(0);
    });

    it("reports error for missing required config", () => {
      act(() => {
        useEditorStore.getState().addNode("sql", { x: 0, y: 0 });
        useEditorStore.getState().validate();
      });

      const errors = useEditorStore.getState().validationErrors;
      const configError = errors.find((e) => e.type === "invalid_config");
      expect(configError).toBeDefined();
    });
  });

  describe("copySelectedNode / pasteNode", () => {
    it("copies and pastes a node", () => {
      act(() => {
        useEditorStore.getState().addNode("sql", { x: 100, y: 100 });
      });

      const nodeId = useEditorStore.getState().nodes[0].id;

      act(() => {
        useEditorStore.getState().selectNode(nodeId);
        useEditorStore.getState().copySelectedNode();
        useEditorStore.getState().pasteNode();
      });

      const state = useEditorStore.getState();
      expect(state.nodes).toHaveLength(2);
      expect(state.nodes[1].data.stageType).toBe("sql");
      expect(state.nodes[1].position.x).toBe(130);
      expect(state.nodes[1].position.y).toBe(130);
    });
  });

  describe("getStages", () => {
    it("converts nodes and edges to stage array", () => {
      act(() => {
        const store = useEditorStore.getState();
        store.addNode("sql", { x: 0, y: 0 });
        store.addNode("python", { x: 200, y: 0 });
      });

      const [n1, n2] = useEditorStore.getState().nodes;

      act(() => {
        useEditorStore.getState().connectNodes(n1.id, n2.id);
      });

      const stages = useEditorStore.getState().getStages();
      expect(stages).toHaveLength(2);

      const stage2 = stages.find((s) => s.id === n2.id);
      expect(stage2?.dependsOn).toContain(n1.id);
    });
  });
});
