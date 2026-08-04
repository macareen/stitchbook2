import React, { useState } from 'react';
import {
  GuideDefinition,
  ExecutionAddress,
  ExecutionState,
  ContainerProgressStatus
} from '../types';
import {
  ValidatedGuideDefinition,
  GuideDefinitionValidator,
  GuideTraversal,
  DerivedProgressCalculator,
  areAddressesEqual
} from '../domain/executionEngine';
import { X, CheckCircle2, Circle, PlayCircle, ChevronRight, ChevronDown, Compass } from 'lucide-react';

interface PatternMapModalProps {
  isOpen: boolean;
  onClose: () => void;
  definition: GuideDefinition;
  executionState: ExecutionState;
  onJumpToAddress: (address: ExecutionAddress) => void;
}

export const PatternMapModal: React.FC<PatternMapModalProps> = ({
  isOpen,
  onClose,
  definition,
  executionState,
  onJumpToAddress
}) => {
  if (!isOpen) return null;

  const [expandedNodes, setExpandedNodes] = useState<Record<string, boolean>>({});

  let validated: ValidatedGuideDefinition;
  try {
    validated = GuideDefinitionValidator.validate(definition);
  } catch (e) {
    return (
      <div className="fixed inset-0 bg-stone-900/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
        <div className="bg-white rounded-2xl p-6 max-w-md w-full">
          <h3 className="text-lg font-bold text-red-600">Error Loading Pattern Map</h3>
          <p className="text-sm text-stone-600 mt-2">{(e as Error).message}</p>
          <button onClick={onClose} className="mt-4 px-4 py-2 bg-stone-200 rounded-xl text-sm font-medium">Close</button>
        </div>
      </div>
    );
  }

  const traversal = new GuideTraversal(validated);
  const progressCalc = new DerivedProgressCalculator(validated);
  const occurrences = traversal.getOccurrenceRecords();

  const toggleExpand = (id: string) => {
    setExpandedNodes(prev => ({ ...prev, [id]: !prev[id] }));
  };

  const isCurrentAddress = (addr: ExecutionAddress) => {
    return areAddressesEqual(executionState.currentAddress, addr);
  };

  const isCompletedAddress = (addr: ExecutionAddress) => {
    return executionState.completedAddresses.some(a => areAddressesEqual(a, addr));
  };

  return (
    <div className="fixed inset-0 bg-stone-950/60 backdrop-blur-xs z-50 flex items-center justify-center p-4 sm:p-6 animate-fade-in">
      <div className="bg-white rounded-3xl shadow-2xl max-w-3xl w-full max-h-[85vh] flex flex-col overflow-hidden border border-stone-200">
        
        {/* Modal Header */}
        <div className="px-6 py-5 border-b border-stone-200 flex items-center justify-between bg-stone-50">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-stone-200 text-stone-800 rounded-xl">
              <Compass className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-serif-display font-semibold text-stone-900">Pattern Navigation Map</h2>
              <p className="text-xs text-stone-500">View complete step structure and jump to any position</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-2 text-stone-400 hover:text-stone-700 hover:bg-stone-200/60 rounded-xl transition-colors cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Modal Content - Tree Walk */}
        <div className="p-6 overflow-y-auto space-y-4 divide-y divide-stone-100">
          {definition.rootNodeIds.map(rootId => {
            const rootNode = validated.node(rootId);
            if (!rootNode) return null;

            return (
              <div key={rootId} className="pt-2 first:pt-0">
                <NodeTreeItem
                  nodeId={rootId}
                  validated={validated}
                  progressCalc={progressCalc}
                  executionState={executionState}
                  expandedNodes={expandedNodes}
                  onToggleExpand={toggleExpand}
                  onJumpToAddress={(addr) => {
                    onJumpToAddress(addr);
                    onClose();
                  }}
                  depth={0}
                />
              </div>
            );
          })}
        </div>

        {/* Modal Footer */}
        <div className="px-6 py-4 bg-stone-50 border-t border-stone-200 flex items-center justify-between text-xs text-stone-500">
          <div className="flex items-center gap-4">
            <span className="flex items-center gap-1.5"><CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" /> Completed</span>
            <span className="flex items-center gap-1.5"><PlayCircle className="w-3.5 h-3.5 text-rose-600" /> Current Step</span>
            <span className="flex items-center gap-1.5"><Circle className="w-3.5 h-3.5 text-stone-300" /> Pending</span>
          </div>
          <button
            onClick={onClose}
            className="px-4 py-2 bg-stone-900 text-white rounded-xl font-medium text-xs hover:bg-stone-800 cursor-pointer"
          >
            Close Map
          </button>
        </div>

      </div>
    </div>
  );
};

interface NodeTreeItemProps {
  nodeId: string;
  validated: ValidatedGuideDefinition;
  progressCalc: DerivedProgressCalculator;
  executionState: ExecutionState;
  expandedNodes: Record<string, boolean>;
  onToggleExpand: (id: string) => void;
  onJumpToAddress: (address: ExecutionAddress) => void;
  depth: number;
}

const NodeTreeItem: React.FC<NodeTreeItemProps> = ({
  nodeId,
  validated,
  progressCalc,
  executionState,
  expandedNodes,
  onToggleExpand,
  onJumpToAddress,
  depth
}) => {
  const node = validated.node(nodeId);
  if (!node) return null;

  const isExpanded = expandedNodes[nodeId] ?? true;

  if (node.type === 'SECTION' || node.type === 'RANGE' || node.type === 'REPEAT') {
    const progress = progressCalc.progressFor(node.id, executionState.completedAddresses, executionState.currentAddress);

    let badgeBg = 'bg-stone-100 text-stone-600';
    let statusText = 'Not Started';
    if (progress.status === ContainerProgressStatus.COMPLETE) {
      badgeBg = 'bg-emerald-100 text-emerald-800 border border-emerald-200';
      statusText = 'Complete';
    } else if (progress.status === ContainerProgressStatus.IN_PROGRESS) {
      badgeBg = 'bg-amber-100 text-amber-800 border border-amber-200';
      statusText = `In Progress (${progress.completedCount}/${progress.totalCount})`;
    }

    return (
      <div className="space-y-2">
        <div
          onClick={() => onToggleExpand(node.id)}
          className={`flex items-center justify-between p-3 rounded-2xl hover:bg-stone-100/80 transition-colors cursor-pointer border border-stone-200/60 ${
            depth === 0 ? 'bg-stone-50 font-medium' : 'bg-white'
          }`}
          style={{ marginLeft: `${depth * 16}px` }}
        >
          <div className="flex items-center gap-2">
            <button className="text-stone-400 hover:text-stone-700">
              {isExpanded ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
            </button>
            <span className="font-semibold text-stone-800 text-sm">
              {node.type === 'SECTION' && node.title}
              {node.type === 'RANGE' && `${node.unitLabel.toUpperCase()} ${node.startInclusive}–${node.endInclusive}`}
              {node.type === 'REPEAT' && (node.label || `Repeat ${node.count}x`)}
            </span>
          </div>

          <span className={`text-xs px-2.5 py-1 rounded-full font-medium ${badgeBg}`}>
            {statusText}
          </span>
        </div>

        {isExpanded && (
          <div className="space-y-1.5">
            {node.children.map(childId => (
              <NodeTreeItem
                key={childId}
                nodeId={childId}
                validated={validated}
                progressCalc={progressCalc}
                executionState={executionState}
                expandedNodes={expandedNodes}
                onToggleExpand={onToggleExpand}
                onJumpToAddress={onJumpToAddress}
                depth={depth + 1}
              />
            ))}
          </div>
        )}
      </div>
    );
  }

  // INSTRUCTION NODE
  const traversal = new GuideTraversal(validated);
  const allOccurrences = traversal.occurrences().filter(occ => occ.instruction.id === node.id);

  return (
    <div className="space-y-1" style={{ marginLeft: `${depth * 16}px` }}>
      {allOccurrences.map((occ, idx) => {
        const isCurrent = areAddressesEqual(executionState.currentAddress, occ.address);
        const isCompleted = executionState.completedAddresses.some(a => areAddressesEqual(a, occ.address));

        return (
          <div
            key={idx}
            className={`flex items-center justify-between p-3 rounded-xl border transition-all text-xs ${
              isCurrent
                ? 'bg-rose-50/90 border-rose-300 text-rose-900 font-medium ring-1 ring-rose-400/40'
                : isCompleted
                ? 'bg-emerald-50/50 border-emerald-200/80 text-stone-700'
                : 'bg-white border-stone-200/60 text-stone-800 hover:border-stone-300'
            }`}
          >
            <div className="flex items-center gap-2.5 flex-1 pr-2">
              {isCurrent ? (
                <PlayCircle className="w-4 h-4 text-rose-600 shrink-0" />
              ) : isCompleted ? (
                <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
              ) : (
                <Circle className="w-4 h-4 text-stone-300 shrink-0" />
              )}
              <span className="leading-snug">{node.instructionText}</span>
            </div>

            {!isCurrent && (
              <button
                onClick={() => onJumpToAddress(occ.address)}
                className="px-2.5 py-1 bg-stone-100 hover:bg-stone-200 text-stone-700 rounded-lg text-[11px] font-medium transition-colors shrink-0 cursor-pointer"
              >
                Jump Here
              </button>
            )}
          </div>
        );
      })}
    </div>
  );
};
