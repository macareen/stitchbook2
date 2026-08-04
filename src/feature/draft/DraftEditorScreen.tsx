import React, { useState } from 'react';
import {
  Guide,
  GuideDraft,
  DraftNode,
  DraftNodeType,
  Project
} from '../../types';
import { saveDraft, publishDraftAsRevision } from '../../services/db';
import {
  ArrowLeft,
  Plus,
  Trash2,
  ChevronUp,
  ChevronDown,
  Save,
  Send,
  Edit3,
  Layers,
  Repeat,
  AlignLeft,
  FolderTree,
  AlertCircle
} from 'lucide-react';

interface DraftEditorScreenProps {
  project: Project;
  guide: Guide;
  initialDraft: GuideDraft;
  onClose: () => void;
  onPublished: (revisionId: string) => void;
}

export const DraftEditorScreen: React.FC<DraftEditorScreenProps> = ({
  project,
  guide,
  initialDraft,
  onClose,
  onPublished
}) => {
  const [draft, setDraft] = useState<GuideDraft>(initialDraft);
  const [editingNode, setEditingNode] = useState<DraftNode | null>(null);
  const [addingToParentId, setAddingToParentId] = useState<string | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);

  const handleSave = () => {
    saveDraft(draft);
    setValidationError(null);
  };

  const handlePublish = () => {
    if (draft.nodes.length === 0) {
      setValidationError("Cannot publish an empty pattern draft. Add at least one step or instruction.");
      return;
    }

    try {
      saveDraft(draft);
      const rev = publishDraftAsRevision(guide.id);
      onPublished(rev.id);
    } catch (e) {
      setValidationError((e as Error).message);
    }
  };

  // Node Mutation Helpers
  const addNode = (type: DraftNodeType, parentId: string | null) => {
    const newId = 'node-' + Math.random().toString(36).substring(2, 9);
    let newNode: DraftNode;

    switch (type) {
      case DraftNodeType.SECTION:
        newNode = { id: newId, type, title: 'New Section', children: [] };
        break;
      case DraftNodeType.RANGE:
        newNode = { id: newId, type, rangeUnitLabel: 'row', rangeStartInclusive: 1, rangeEndInclusive: 5, children: [] };
        break;
      case DraftNodeType.REPEAT:
        newNode = { id: newId, type, repeatCount: 2, repeatLabel: 'Repeat Motif', children: [] };
        break;
      case DraftNodeType.INSTRUCTION:
        newNode = { id: newId, type, instructionText: 'Knit all stitches across.' };
        break;
    }

    const updatedNodes = [...draft.nodes, newNode];
    let updatedRoots = [...draft.rootNodeIds];

    if (parentId) {
      const parentIndex = updatedNodes.findIndex(n => n.id === parentId);
      if (parentIndex !== -1) {
        const parent = { ...updatedNodes[parentIndex] };
        parent.children = [...(parent.children || []), newId];
        updatedNodes[parentIndex] = parent;
      }
    } else {
      updatedRoots.push(newId);
    }

    const updatedDraft = {
      ...draft,
      rootNodeIds: updatedRoots,
      nodes: updatedNodes
    };

    setDraft(updatedDraft);
    saveDraft(updatedDraft);
    setEditingNode(newNode);
    setAddingToParentId(null);
  };

  const updateNode = (updated: DraftNode) => {
    const updatedNodes = draft.nodes.map(n => n.id === updated.id ? updated : n);
    const updatedDraft = { ...draft, nodes: updatedNodes };
    setDraft(updatedDraft);
    saveDraft(updatedDraft);
    setEditingNode(null);
  };

  const deleteNode = (nodeId: string) => {
    const updatedNodes = draft.nodes.filter(n => n.id !== nodeId).map(n => ({
      ...n,
      children: n.children ? n.children.filter(c => c !== nodeId) : undefined
    }));
    const updatedRoots = draft.rootNodeIds.filter(id => id !== nodeId);

    const updatedDraft = { ...draft, rootNodeIds: updatedRoots, nodes: updatedNodes };
    setDraft(updatedDraft);
    saveDraft(updatedDraft);
  };

  const moveNode = (nodeId: string, parentId: string | null, direction: 'UP' | 'DOWN') => {
    const list = parentId
      ? (draft.nodes.find(n => n.id === parentId)?.children || [])
      : draft.rootNodeIds;

    const idx = list.indexOf(nodeId);
    if (idx === -1) return;

    const targetIdx = direction === 'UP' ? idx - 1 : idx + 1;
    if (targetIdx < 0 || targetIdx >= list.length) return;

    const newList = [...list];
    const [moved] = newList.splice(idx, 1);
    newList.splice(targetIdx, 0, moved);

    if (parentId) {
      const updatedNodes = draft.nodes.map(n => n.id === parentId ? { ...n, children: newList } : n);
      setDraft({ ...draft, nodes: updatedNodes });
    } else {
      setDraft({ ...draft, rootNodeIds: newList });
    }
  };

  return (
    <div className="min-h-screen bg-stone-100 text-stone-900 flex flex-col justify-between">
      
      {/* Top Header */}
      <header className="px-6 py-4 bg-white border-b border-stone-200 sticky top-0 z-30 flex items-center justify-between shadow-xs">
        <div className="flex items-center gap-4">
          <button
            onClick={onClose}
            className="p-2 text-stone-500 hover:text-stone-900 hover:bg-stone-100 rounded-2xl transition-colors cursor-pointer"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>
          <div>
            <span className="text-xs font-semibold text-rose-800 uppercase tracking-wider">{project.name}</span>
            <h1 className="text-lg font-serif-display font-semibold text-stone-900">{guide.name} — Draft Editor</h1>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={handleSave}
            className="flex items-center gap-2 px-4 py-2 bg-stone-200 hover:bg-stone-300 text-stone-800 rounded-xl text-xs font-medium transition-colors cursor-pointer"
          >
            <Save className="w-4 h-4" />
            <span className="hidden sm:inline">Save Draft</span>
          </button>

          <button
            onClick={handlePublish}
            className="flex items-center gap-2 px-5 py-2 bg-rose-800 hover:bg-rose-900 text-white font-medium rounded-xl text-xs shadow-md transition-all cursor-pointer"
          >
            <Send className="w-4 h-4" />
            <span>Publish Revision</span>
          </button>
        </div>
      </header>

      {/* Main Content */}
      <main className="flex-1 max-w-4xl w-full mx-auto p-6 space-y-6">
        
        {validationError && (
          <div className="p-4 bg-rose-50 border border-rose-200 text-rose-900 rounded-2xl flex items-center gap-3 text-sm">
            <AlertCircle className="w-5 h-5 text-rose-600 shrink-0" />
            <span>{validationError}</span>
          </div>
        )}

        {/* Root Add Bar */}
        <div className="bg-white p-5 rounded-2xl border border-stone-200 shadow-xs flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <FolderTree className="w-5 h-5 text-stone-600" />
            <span className="text-sm font-semibold text-stone-800">Add Root Component</span>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <button
              onClick={() => addNode(DraftNodeType.SECTION, null)}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-stone-100 hover:bg-stone-200 text-stone-800 rounded-xl text-xs font-medium cursor-pointer"
            >
              <Plus className="w-3.5 h-3.5" /> Section
            </button>
            <button
              onClick={() => addNode(DraftNodeType.RANGE, null)}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-stone-100 hover:bg-stone-200 text-stone-800 rounded-xl text-xs font-medium cursor-pointer"
            >
              <Plus className="w-3.5 h-3.5" /> Range (Rows)
            </button>
            <button
              onClick={() => addNode(DraftNodeType.REPEAT, null)}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-stone-100 hover:bg-stone-200 text-stone-800 rounded-xl text-xs font-medium cursor-pointer"
            >
              <Plus className="w-3.5 h-3.5" /> Repeat Loop
            </button>
            <button
              onClick={() => addNode(DraftNodeType.INSTRUCTION, null)}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-rose-100 hover:bg-rose-200 text-rose-900 rounded-xl text-xs font-medium cursor-pointer"
            >
              <Plus className="w-3.5 h-3.5" /> Instruction
            </button>
          </div>
        </div>

        {/* Draft Tree Display */}
        {draft.rootNodeIds.length === 0 ? (
          <div className="bg-white border border-stone-200 rounded-3xl p-12 text-center space-y-3">
            <div className="w-12 h-12 bg-stone-100 text-stone-500 rounded-2xl flex items-center justify-center mx-auto">
              <FolderTree className="w-6 h-6" />
            </div>
            <h3 className="text-base font-semibold text-stone-800">No components in draft yet</h3>
            <p className="text-xs text-stone-500 max-w-sm mx-auto">
              Use the buttons above to add sections, row ranges, repeat loops, or step instructions.
            </p>
          </div>
        ) : (
          <div className="space-y-3">
            {draft.rootNodeIds.map((rootId, idx) => (
              <RenderDraftNode
                key={rootId}
                nodeId={rootId}
                parentId={null}
                nodes={draft.nodes}
                index={idx}
                siblingsCount={draft.rootNodeIds.length}
                depth={0}
                onEdit={setEditingNode}
                onDelete={deleteNode}
                onMove={(id, pId, dir) => moveNode(id, pId, dir)}
                onStartAddChild={setAddingToParentId}
              />
            ))}
          </div>
        )}

      </main>

      {/* Edit Node Dialog */}
      {editingNode && (
        <EditNodeModal
          node={editingNode}
          onSave={updateNode}
          onClose={() => setEditingNode(null)}
        />
      )}

      {/* Add Child Dialog */}
      {addingToParentId && (
        <div className="fixed inset-0 bg-stone-900/50 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl p-6 max-w-md w-full space-y-4 shadow-xl">
            <h3 className="text-base font-bold text-stone-900">Add Child Component</h3>
            <p className="text-xs text-stone-500">Choose what to insert inside this container component:</p>
            
            <div className="grid grid-cols-2 gap-2.5 pt-2">
              <button
                onClick={() => addNode(DraftNodeType.SECTION, addingToParentId)}
                className="p-3 bg-stone-50 hover:bg-stone-100 rounded-xl text-xs font-semibold text-stone-800 border border-stone-200 flex flex-col items-center gap-1 cursor-pointer"
              >
                <FolderTree className="w-5 h-5 text-stone-600" />
                Section
              </button>
              <button
                onClick={() => addNode(DraftNodeType.RANGE, addingToParentId)}
                className="p-3 bg-stone-50 hover:bg-stone-100 rounded-xl text-xs font-semibold text-stone-800 border border-stone-200 flex flex-col items-center gap-1 cursor-pointer"
              >
                <Layers className="w-5 h-5 text-stone-600" />
                Range
              </button>
              <button
                onClick={() => addNode(DraftNodeType.REPEAT, addingToParentId)}
                className="p-3 bg-stone-50 hover:bg-stone-100 rounded-xl text-xs font-semibold text-stone-800 border border-stone-200 flex flex-col items-center gap-1 cursor-pointer"
              >
                <Repeat className="w-5 h-5 text-stone-600" />
                Repeat
              </button>
              <button
                onClick={() => addNode(DraftNodeType.INSTRUCTION, addingToParentId)}
                className="p-3 bg-rose-50 hover:bg-rose-100 rounded-xl text-xs font-semibold text-rose-900 border border-rose-200 flex flex-col items-center gap-1 cursor-pointer"
              >
                <AlignLeft className="w-5 h-5 text-rose-700" />
                Instruction
              </button>
            </div>

            <div className="pt-2 flex justify-end">
              <button
                onClick={() => setAddingToParentId(null)}
                className="px-4 py-2 text-xs font-medium text-stone-600 hover:bg-stone-100 rounded-xl cursor-pointer"
              >
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
};

interface RenderDraftNodeProps {
  nodeId: string;
  parentId: string | null;
  nodes: DraftNode[];
  index: number;
  siblingsCount: number;
  depth: number;
  onEdit: (node: DraftNode) => void;
  onDelete: (id: string) => void;
  onMove: (id: string, parentId: string | null, dir: 'UP' | 'DOWN') => void;
  onStartAddChild: (id: string) => void;
}

const RenderDraftNode: React.FC<RenderDraftNodeProps> = ({
  nodeId,
  parentId,
  nodes,
  index,
  siblingsCount,
  depth,
  onEdit,
  onDelete,
  onMove,
  onStartAddChild
}) => {
  const node = nodes.find(n => n.id === nodeId);
  if (!node) return null;

  const isContainer = node.type !== DraftNodeType.INSTRUCTION;

  return (
    <div className="space-y-2" style={{ marginLeft: `${depth * 20}px` }}>
      <div className={`p-4 rounded-2xl border transition-all flex items-center justify-between gap-3 shadow-2xs ${
        node.type === DraftNodeType.INSTRUCTION
          ? 'bg-rose-50/50 border-rose-200 text-stone-900'
          : 'bg-white border-stone-200 text-stone-900 font-medium'
      }`}>
        <div className="flex items-center gap-3 flex-1 min-w-0">
          <span className="p-1.5 bg-stone-100 rounded-lg text-stone-600 shrink-0">
            {node.type === DraftNodeType.SECTION && <FolderTree className="w-4 h-4" />}
            {node.type === DraftNodeType.RANGE && <Layers className="w-4 h-4" />}
            {node.type === DraftNodeType.REPEAT && <Repeat className="w-4 h-4" />}
            {node.type === DraftNodeType.INSTRUCTION && <AlignLeft className="w-4 h-4 text-rose-700" />}
          </span>

          <div className="truncate">
            <span className="text-xs uppercase font-bold tracking-wider text-stone-400 mr-2">{node.type}</span>
            <span className="text-sm font-semibold text-stone-900">
              {node.type === DraftNodeType.SECTION && node.title}
              {node.type === DraftNodeType.RANGE && `${node.rangeUnitLabel || 'row'} ${node.rangeStartInclusive}–${node.rangeEndInclusive}`}
              {node.type === DraftNodeType.REPEAT && `${node.repeatLabel || 'Repeat'} (${node.repeatCount}x)`}
              {node.type === DraftNodeType.INSTRUCTION && node.instructionText}
            </span>
          </div>
        </div>

        {/* Controls */}
        <div className="flex items-center gap-1 shrink-0">
          {isContainer && (
            <button
              onClick={() => onStartAddChild(node.id)}
              className="p-1.5 hover:bg-stone-100 text-stone-600 rounded-lg transition-colors cursor-pointer"
              title="Add Child Component"
            >
              <Plus className="w-4 h-4" />
            </button>
          )}

          <button
            onClick={() => onEdit(node)}
            className="p-1.5 hover:bg-stone-100 text-stone-600 rounded-lg transition-colors cursor-pointer"
            title="Edit Component"
          >
            <Edit3 className="w-4 h-4" />
          </button>

          <button
            disabled={index === 0}
            onClick={() => onMove(node.id, parentId, 'UP')}
            className="p-1.5 hover:bg-stone-100 text-stone-600 disabled:opacity-30 rounded-lg transition-colors cursor-pointer"
            title="Move Up"
          >
            <ChevronUp className="w-4 h-4" />
          </button>

          <button
            disabled={index === siblingsCount - 1}
            onClick={() => onMove(node.id, parentId, 'DOWN')}
            className="p-1.5 hover:bg-stone-100 text-stone-600 disabled:opacity-30 rounded-lg transition-colors cursor-pointer"
            title="Move Down"
          >
            <ChevronDown className="w-4 h-4" />
          </button>

          <button
            onClick={() => onDelete(node.id)}
            className="p-1.5 hover:bg-rose-100 text-rose-700 rounded-lg transition-colors cursor-pointer"
            title="Delete Component"
          >
            <Trash2 className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Children */}
      {node.children && node.children.length > 0 && (
        <div className="space-y-2">
          {node.children.map((childId, cIdx) => (
            <RenderDraftNode
              key={childId}
              nodeId={childId}
              parentId={node.id}
              nodes={nodes}
              index={cIdx}
              siblingsCount={node.children!.length}
              depth={depth + 1}
              onEdit={onEdit}
              onDelete={onDelete}
              onMove={onMove}
              onStartAddChild={onStartAddChild}
            />
          ))}
        </div>
      )}
    </div>
  );
};

interface EditNodeModalProps {
  node: DraftNode;
  onSave: (node: DraftNode) => void;
  onClose: () => void;
}

const EditNodeModal: React.FC<EditNodeModalProps> = ({ node, onSave, onClose }) => {
  const [form, setForm] = useState<DraftNode>({ ...node });

  return (
    <div className="fixed inset-0 bg-stone-900/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
      <div className="bg-white rounded-3xl p-6 max-w-md w-full space-y-4 shadow-xl">
        <h3 className="text-base font-bold text-stone-900">Edit {form.type}</h3>

        {form.type === DraftNodeType.SECTION && (
          <div>
            <label className="block text-xs font-semibold text-stone-600 mb-1">Section Title</label>
            <input
              type="text"
              value={form.title || ''}
              onChange={e => setForm({ ...form, title: e.target.value })}
              className="w-full px-3.5 py-2.5 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-800"
            />
          </div>
        )}

        {form.type === DraftNodeType.RANGE && (
          <div className="space-y-3">
            <div>
              <label className="block text-xs font-semibold text-stone-600 mb-1">Unit Label (e.g. row, round)</label>
              <input
                type="text"
                value={form.rangeUnitLabel || ''}
                onChange={e => setForm({ ...form, rangeUnitLabel: e.target.value })}
                className="w-full px-3.5 py-2.5 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-800"
              />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-semibold text-stone-600 mb-1">Start (Inclusive)</label>
                <input
                  type="number"
                  value={form.rangeStartInclusive ?? 1}
                  onChange={e => setForm({ ...form, rangeStartInclusive: parseInt(e.target.value) || 1 })}
                  className="w-full px-3.5 py-2.5 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-800"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-stone-600 mb-1">End (Inclusive)</label>
                <input
                  type="number"
                  value={form.rangeEndInclusive ?? 1}
                  onChange={e => setForm({ ...form, rangeEndInclusive: parseInt(e.target.value) || 1 })}
                  className="w-full px-3.5 py-2.5 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-800"
                />
              </div>
            </div>
          </div>
        )}

        {form.type === DraftNodeType.REPEAT && (
          <div className="space-y-3">
            <div>
              <label className="block text-xs font-semibold text-stone-600 mb-1">Repeat Label / Title</label>
              <input
                type="text"
                value={form.repeatLabel || ''}
                onChange={e => setForm({ ...form, repeatLabel: e.target.value })}
                className="w-full px-3.5 py-2.5 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-800"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-stone-600 mb-1">Repeat Iteration Count</label>
              <input
                type="number"
                value={form.repeatCount ?? 1}
                onChange={e => setForm({ ...form, repeatCount: Math.max(1, parseInt(e.target.value) || 1) })}
                className="w-full px-3.5 py-2.5 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-800"
              />
            </div>
          </div>
        )}

        {form.type === DraftNodeType.INSTRUCTION && (
          <div>
            <label className="block text-xs font-semibold text-stone-600 mb-1">Instruction Text</label>
            <textarea
              rows={3}
              value={form.instructionText || ''}
              onChange={e => setForm({ ...form, instructionText: e.target.value })}
              className="w-full px-3.5 py-2.5 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-800"
            />
          </div>
        )}

        <div className="pt-3 flex items-center justify-end gap-2">
          <button
            onClick={onClose}
            className="px-4 py-2 text-xs font-medium text-stone-600 hover:bg-stone-100 rounded-xl cursor-pointer"
          >
            Cancel
          </button>
          <button
            onClick={() => onSave(form)}
            className="px-4 py-2 bg-stone-900 text-white rounded-xl text-xs font-medium hover:bg-stone-800 cursor-pointer"
          >
            Save Changes
          </button>
        </div>
      </div>
    </div>
  );
};
