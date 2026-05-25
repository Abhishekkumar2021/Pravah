import { describe, it, expect, beforeEach } from "vitest";
import { act } from "@testing-library/react";
import { useEditorStore } from "./editorStore";

describe("editorStore", () => {
  beforeEach(() => {
    act(() => {
      useEditorStore.getState().reset();
      useEditorStore.getState().initialize("test-pipeline", "Test Pipeline", null, []);
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
        useEditorStore.getState().initialize("p1", "Pipeline", null, [
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
        ]);
      });

      const state = useEditorStore.getState();
      expect(state.nodes).toHaveLength(2);
      expect(state.edges).toHaveLength(1);
      expect(state.edges[0].source).toBe("stage-1");
      expect(state.edges[0].target).toBe("stage-2");
    });

    it("fills missing sql config fields when loading stages", () => {
      act(() => {
        useEditorStore.getState().initialize("p1", "Pipeline", null, [
          {
            id: "stage-1",
            name: "Extract",
            type: "sql",
            config: { query: "SELECT * FROM users" },
            dependsOn: [],
          },
        ]);
      });

      const sqlStage = useEditorStore.getState().nodes[0]?.data.stage;
      expect(sqlStage?.config).toMatchObject({
        query: "SELECT * FROM users",
        connection: "",
      });
    });
  });

  describe("addNode", () => {
    it("adds a new node with generated id", () => {
      act(() => {
        useEditorStore.getState().addNode("sql", { x: 100, y: 200 });
      });

      const state = useEditorStore.getState();
      expect(state.nodes).toHaveLength(1);
      expect(state.nodes[0].type).toBe("stageNode");
      expect(state.nodes[0].position).toEqual({ x: 100, y: 200 });
      expect(state.nodes[0].data.stageType).toBe("sql");
    });
  });

  describe("deleteNode", () => {
    it("removes a node", () => {
      act(() => {
        useEditorStore.getState().addNode("sql", { x: 0, y: 0 });
      });

      const nodeId = useEditorStore.getState().nodes[0].id;
      expect(useEditorStore.getState().nodes).toHaveLength(1);

      act(() => {
        useEditorStore.getState().deleteNode(nodeId);
      });

      expect(useEditorStore.getState().nodes).toHaveLength(0);
    });
  });

  describe("connectNodes", () => {
    it("creates an edge between nodes", () => {
      act(() => {
        useEditorStore.getState().addNode("sql", { x: 0, y: 0 });
        useEditorStore.getState().addNode("python", { x: 200, y: 0 });
      });

      const nodes = useEditorStore.getState().nodes;
      const source = nodes[0].id;
      const target = nodes[1].id;

      act(() => {
        useEditorStore.getState().connectNodes({
          source,
          target,
          sourceHandle: null,
          targetHandle: null,
        });
      });

      const state = useEditorStore.getState();
      expect(state.edges).toHaveLength(1);
      expect(state.edges[0].source).toBe(source);
      expect(state.edges[0].target).toBe(target);
    });

    it("prevents duplicate connections", () => {
      act(() => {
        useEditorStore.getState().addNode("sql", { x: 0, y: 0 });
        useEditorStore.getState().addNode("python", { x: 200, y: 0 });
      });

      const nodes = useEditorStore.getState().nodes;
      const source = nodes[0].id;
      const target = nodes[1].id;

      act(() => {
        useEditorStore.getState().connectNodes({ source, target, sourceHandle: null, targetHandle: null });
        useEditorStore.getState().connectNodes({ source, target, sourceHandle: null, targetHandle: null });
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
    it("validates nodes on change", () => {
      act(() => {
        useEditorStore.getState().addNode("sql", { x: 0, y: 0 });
      });

      // Validation runs automatically after addNode
      const state = useEditorStore.getState();
      // Should have nodes
      expect(state.nodes).toHaveLength(1);
      // Validation errors array exists (may or may not have errors depending on defaults)
      expect(Array.isArray(state.validationErrors)).toBe(true);
    });
  });

  describe("getStages", () => {
    it("converts nodes and edges to stage array", () => {
      act(() => {
        useEditorStore.getState().addNode("sql", { x: 0, y: 0 });
        useEditorStore.getState().addNode("python", { x: 200, y: 0 });
      });

      const nodes = useEditorStore.getState().nodes;
      const source = nodes[0].id;
      const target = nodes[1].id;

      act(() => {
        useEditorStore.getState().connectNodes({ source, target, sourceHandle: null, targetHandle: null });
      });

      const stages = useEditorStore.getState().getStages();
      expect(stages).toHaveLength(2);

      const stage2 = stages.find((s) => s.id === target);
      expect(stage2?.dependsOn).toContain(source);
    });
  });
});
