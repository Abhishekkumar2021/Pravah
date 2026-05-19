import { useCallback, useEffect } from "react";
import { useEditorStore, selectCanUndo, selectCanRedo } from "./editorStore";

export function useKeyboardShortcuts() {
  const canUndo = useEditorStore(selectCanUndo);
  const canRedo = useEditorStore(selectCanRedo);
  const selectedNodeId = useEditorStore((s) => s.selectedNodeId);
  const selectedEdgeId = useEditorStore((s) => s.selectedEdgeId);
  const clipboard = useEditorStore((s) => s.clipboard);

  const undo = useEditorStore((s) => s.undo);
  const redo = useEditorStore((s) => s.redo);
  const deleteSelected = useEditorStore((s) => s.deleteSelected);
  const copySelectedNode = useEditorStore((s) => s.copySelectedNode);
  const pasteNode = useEditorStore((s) => s.pasteNode);
  const clearSelection = useEditorStore((s) => s.clearSelection);

  const handleKeyDown = useCallback(
    (event: KeyboardEvent) => {
      const target = event.target as HTMLElement;
      const isInput =
        target.tagName === "INPUT" ||
        target.tagName === "TEXTAREA" ||
        target.isContentEditable;

      // Escape clears selection regardless of focus
      if (event.key === "Escape") {
        event.preventDefault();
        clearSelection();
        return;
      }

      // Don't handle shortcuts when typing in inputs
      if (isInput) {
        return;
      }

      const isMeta = event.metaKey || event.ctrlKey;

      // Undo: Cmd/Ctrl + Z
      if (isMeta && event.key === "z" && !event.shiftKey) {
        if (canUndo) {
          event.preventDefault();
          undo();
        }
        return;
      }

      // Redo: Cmd/Ctrl + Shift + Z or Cmd/Ctrl + Y
      if ((isMeta && event.shiftKey && event.key === "z") || (isMeta && event.key === "y")) {
        if (canRedo) {
          event.preventDefault();
          redo();
        }
        return;
      }

      // Copy: Cmd/Ctrl + C
      if (isMeta && event.key === "c") {
        if (selectedNodeId) {
          event.preventDefault();
          copySelectedNode();
        }
        return;
      }

      // Paste: Cmd/Ctrl + V
      if (isMeta && event.key === "v") {
        if (clipboard) {
          event.preventDefault();
          pasteNode();
        }
        return;
      }

      // Delete: Delete or Backspace
      if (event.key === "Delete" || event.key === "Backspace") {
        if (selectedNodeId || selectedEdgeId) {
          event.preventDefault();
          deleteSelected();
        }
        return;
      }
    },
    [
      canUndo,
      canRedo,
      selectedNodeId,
      selectedEdgeId,
      clipboard,
      undo,
      redo,
      deleteSelected,
      copySelectedNode,
      pasteNode,
      clearSelection,
    ]
  );

  useEffect(() => {
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [handleKeyDown]);
}
