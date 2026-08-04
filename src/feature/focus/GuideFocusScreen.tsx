import React, { useState } from 'react';
import {
  Guide,
  Project,
  DefinitionRevision,
  ExecutionState,
  ExecutionStatus,
  ExecutionAddress
} from '../../types';
import {
  ExecutionEngine,
  GuideDefinitionValidator,
  GuideTraversal,
  areAddressesEqual
} from '../../domain/executionEngine';
import { PatternMapModal } from '../../components/PatternMapModal';
import { saveExecution } from '../../services/db';
import {
  ArrowLeft,
  CheckCircle,
  RotateCcw,
  Compass,
  Sparkles,
  ChevronRight,
  ListTodo,
  Check
} from 'lucide-react';

interface GuideFocusScreenProps {
  project: Project;
  guide: Guide;
  revision: DefinitionRevision;
  initialExecution: ExecutionState;
  onClose: () => void;
}

export const GuideFocusScreen: React.FC<GuideFocusScreenProps> = ({
  project,
  guide,
  revision,
  initialExecution,
  onClose
}) => {
  const [execution, setExecution] = useState<ExecutionState>(initialExecution);
  const [isPatternMapOpen, setIsPatternMapOpen] = useState<boolean>(false);

  const engine = ExecutionEngine.forDefinition(revision.definition);
  const validated = GuideDefinitionValidator.validate(revision.definition);
  const traversal = new GuideTraversal(validated);

  const handleComplete = () => {
    const res = engine.complete(execution);
    if (res.type === 'CHANGED') {
      setExecution(res.state);
      saveExecution(res.state);
    }
  };

  const handlePrevious = () => {
    const res = engine.previous(execution);
    if (res.type === 'CHANGED') {
      setExecution(res.state);
      saveExecution(res.state);
    }
  };

  const handleJump = (targetAddr: ExecutionAddress) => {
    const res = engine.jump(execution, targetAddr);
    if (res.type === 'CHANGED') {
      setExecution(res.state);
      saveExecution(res.state);
    }
  };

  const handleReset = () => {
    const newExec = engine.newExecution(execution.executionId);
    setExecution(newExec);
    saveExecution(newExec);
  };

  // Resolved current occurrence info
  let currentInstructionText = '';
  let breadcrumbs: string[] = [];
  let rangeInfoText = '';
  let repeatInfoText = '';

  if (execution.status === ExecutionStatus.ACTIVE && execution.currentAddress) {
    try {
      const occurrence = traversal.resolve(execution.currentAddress);
      currentInstructionText = occurrence.instruction.instructionText;

      const nodePath = traversal.ancestryNodePath(execution.currentAddress);
      breadcrumbs = nodePath
        .map(nodeId => {
          const n = validated.node(nodeId);
          if (n?.type === 'SECTION') return n.title;
          return null;
        })
        .filter(Boolean) as string[];

      // Positions from ancestry frames
      execution.currentAddress.ancestryFrames.forEach(frame => {
        const container = validated.node(frame.containerNodeId);
        if (container?.type === 'RANGE') {
          rangeInfoText = `${container.unitLabel.toUpperCase()} ${frame.value} of ${container.startInclusive}–${container.endInclusive}`;
        }
        if (container?.type === 'REPEAT') {
          repeatInfoText = `${container.label || 'Repeat'} ${frame.iteration} of ${container.count}`;
        }
      });
    } catch (e) {
      console.error("Failed to resolve current address", e);
    }
  }

  const allOccurrences = traversal.occurrences();
  const completedCount = execution.completedAddresses.length;
  const totalCount = allOccurrences.length;
  const progressPercent = totalCount > 0 ? Math.round((completedCount / totalCount) * 100) : 0;

  return (
    <div className="min-h-screen bg-stone-900 text-stone-100 flex flex-col justify-between selection:bg-rose-900">
      
      {/* Top Header */}
      <header className="px-6 py-4 border-b border-stone-800 bg-stone-950/80 backdrop-blur-md sticky top-0 z-30 flex items-center justify-between">
        <div className="flex items-center gap-4">
          <button
            onClick={onClose}
            className="p-2 text-stone-400 hover:text-white hover:bg-stone-800 rounded-2xl transition-colors cursor-pointer"
            title="Exit Focus Mode"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>
          <div>
            <div className="flex items-center gap-2">
              <span className="text-xs uppercase tracking-wider text-rose-400 font-semibold">{project.name}</span>
            </div>
            <h1 className="text-lg font-serif-display font-semibold text-white">{guide.name}</h1>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={() => setIsPatternMapOpen(true)}
            className="flex items-center gap-2 px-3.5 py-2 bg-stone-800 hover:bg-stone-700 text-stone-200 rounded-xl text-xs font-medium border border-stone-700/80 transition-all cursor-pointer"
          >
            <Compass className="w-4 h-4 text-rose-400" />
            <span className="hidden sm:inline">Pattern Map</span>
          </button>

          <div className="flex items-center gap-2 bg-stone-800/90 px-3 py-1.5 rounded-xl border border-stone-700 text-xs text-stone-300">
            <ListTodo className="w-3.5 h-3.5 text-stone-400" />
            <span>{completedCount} / {totalCount} ({progressPercent}%)</span>
          </div>
        </div>
      </header>

      {/* Main Craft Reader Area */}
      <main className="flex-1 max-w-4xl w-full mx-auto p-6 sm:p-10 flex flex-col justify-center">
        {execution.status === ExecutionStatus.COMPLETED ? (
          /* COMPLETION VIEW */
          <div className="bg-stone-800/90 border border-stone-700/80 rounded-3xl p-8 sm:p-12 text-center space-y-6 shadow-2xl animate-fade-in">
            <div className="w-16 h-16 bg-emerald-950 text-emerald-400 border border-emerald-800/60 rounded-full flex items-center justify-center mx-auto shadow-inner">
              <Sparkles className="w-8 h-8" />
            </div>
            <div className="space-y-2">
              <h2 className="text-2xl sm:text-3xl font-serif-display font-bold text-white">Guide Completed!</h2>
              <p className="text-sm text-stone-400 max-w-md mx-auto">
                You have finished all {totalCount} steps in <strong className="text-stone-200">{guide.name}</strong>.
              </p>
            </div>

            <div className="pt-4 flex flex-col sm:flex-row items-center justify-center gap-4">
              <button
                onClick={handleReset}
                className="w-full sm:w-auto px-6 py-3 bg-stone-700 hover:bg-stone-600 text-stone-100 font-medium rounded-2xl text-sm transition-all flex items-center justify-center gap-2 cursor-pointer"
              >
                <RotateCcw className="w-4 h-4" />
                <span>Restart Guide</span>
              </button>
              <button
                onClick={onClose}
                className="w-full sm:w-auto px-6 py-3 bg-rose-700 hover:bg-rose-800 text-white font-semibold rounded-2xl text-sm transition-all shadow-lg cursor-pointer"
              >
                Return to Project
              </button>
            </div>
          </div>
        ) : (
          /* ACTIVE STEP DISPLAY */
          <div className="space-y-8 animate-fade-in">
            
            {/* Breadcrumb Section Header */}
            {breadcrumbs.length > 0 && (
              <div className="flex items-center gap-2 text-xs font-medium text-rose-400/90 uppercase tracking-wider">
                {breadcrumbs.map((crumb, idx) => (
                  <React.Fragment key={idx}>
                    {idx > 0 && <ChevronRight className="w-3.5 h-3.5 text-stone-600" />}
                    <span>{crumb}</span>
                  </React.Fragment>
                ))}
              </div>
            )}

            {/* Position Indicators */}
            {(rangeInfoText || repeatInfoText) && (
              <div className="flex flex-wrap gap-2.5">
                {rangeInfoText && (
                  <span className="px-3 py-1 bg-rose-950/80 text-rose-300 border border-rose-800/60 rounded-full text-xs font-medium">
                    {rangeInfoText}
                  </span>
                )}
                {repeatInfoText && (
                  <span className="px-3 py-1 bg-amber-950/80 text-amber-300 border border-amber-800/60 rounded-full text-xs font-medium">
                    {repeatInfoText}
                  </span>
                )}
              </div>
            )}

            {/* Main Instruction Display */}
            <div className="bg-stone-800/90 border border-stone-700/70 rounded-3xl p-8 sm:p-12 shadow-2xl relative overflow-hidden">
              <div className="absolute top-0 left-0 right-0 h-1 bg-gradient-to-r from-rose-500 via-amber-500 to-emerald-500" />
              <p className="text-2xl sm:text-3xl font-serif-display font-medium text-stone-50 leading-relaxed">
                {currentInstructionText || 'No instruction text'}
              </p>
            </div>

            {/* Action Controls */}
            <div className="pt-4 flex flex-col sm:flex-row items-center justify-between gap-4">
              <button
                onClick={handlePrevious}
                className="w-full sm:w-auto px-6 py-3.5 bg-stone-800 hover:bg-stone-700 text-stone-300 font-medium rounded-2xl text-sm border border-stone-700/80 transition-all flex items-center justify-center gap-2 cursor-pointer"
              >
                <ArrowLeft className="w-4 h-4" />
                <span>Previous Step</span>
              </button>

              <button
                onClick={handleComplete}
                className="w-full sm:w-1/2 px-8 py-4 bg-rose-700 hover:bg-rose-800 text-white font-semibold rounded-2xl text-base shadow-xl hover:shadow-2xl transition-all flex items-center justify-center gap-3 cursor-pointer"
              >
                <Check className="w-5 h-5 stroke-[2.5]" />
                <span>Complete & Next Step</span>
              </button>
            </div>

          </div>
        )}
      </main>

      {/* Progress Bar Footer */}
      <footer className="px-6 py-4 bg-stone-950 border-t border-stone-800 flex items-center justify-between text-xs text-stone-500">
        <span>Stitchbook Focus Mode — Local Execution Engine</span>
        <div className="w-32 bg-stone-800 h-1.5 rounded-full overflow-hidden">
          <div
            className="bg-rose-500 h-full transition-all duration-300"
            style={{ width: `${progressPercent}%` }}
          />
        </div>
      </footer>

      {/* Pattern Map Drawer/Modal */}
      <PatternMapModal
        isOpen={isPatternMapOpen}
        onClose={() => setIsPatternMapOpen(false)}
        definition={revision.definition}
        executionState={execution}
        onJumpToAddress={handleJump}
      />

    </div>
  );
};
