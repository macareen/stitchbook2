import React from 'react';
import {
  Project,
  ProjectStatus,
  CraftLabels,
  NavigationDestination,
  Guide,
  DefinitionRevision,
  ExecutionState
} from '../../types';
import {
  getProjects,
  getGuidesForProject,
  getLatestRevision,
  getActiveExecutionForGuide
} from '../../services/db';
import { Play, Plus, Sparkles, Folder, Layers, BookOpen, Clock, ArrowRight } from 'lucide-react';

interface HomeScreenProps {
  onNavigate: (dest: NavigationDestination) => void;
  onOpenProject: (projectId: string) => void;
  onAddProject: () => void;
  onOpenFocusMode: (guide: Guide, revision: DefinitionRevision, execution: ExecutionState, project: Project) => void;
}

export const HomeScreen: React.FC<HomeScreenProps> = ({
  onNavigate,
  onOpenProject,
  onAddProject,
  onOpenFocusMode
}) => {
  const projects = getProjects();
  const activeProjects = projects.filter(p => p.status === ProjectStatus.ACTIVE);

  // Find most recent active execution across all active projects
  let lastActiveExecutionInfo: {
    project: Project;
    guide: Guide;
    revision: DefinitionRevision;
    execution: ExecutionState;
  } | null = null;

  for (const proj of activeProjects) {
    const guides = getGuidesForProject(proj.id);
    for (const guide of guides) {
      const activeExec = getActiveExecutionForGuide(guide.id);
      const rev = getLatestRevision(guide.id);
      if (activeExec && rev) {
        if (!lastActiveExecutionInfo || activeExec.updatedAt > lastActiveExecutionInfo.execution.updatedAt) {
          lastActiveExecutionInfo = { project: proj, guide, revision: rev, execution: activeExec };
        }
      }
    }
  }

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-8 py-8 space-y-8 animate-fade-in">
      
      {/* Welcome Banner */}
      <div className="bg-gradient-to-r from-stone-900 via-stone-800 to-stone-900 text-stone-50 rounded-3xl p-8 sm:p-10 shadow-xl relative overflow-hidden">
        <div className="absolute right-0 top-0 bottom-0 w-1/3 opacity-10 bg-[radial-gradient(#fff_1px,transparent_1px)] [background-size:16px_16px]" />
        
        <div className="relative z-10 max-w-2xl space-y-4">
          <div className="inline-flex items-center gap-2 px-3 py-1 bg-rose-950/80 text-rose-300 border border-rose-800/60 rounded-full text-xs font-medium">
            <Sparkles className="w-3.5 h-3.5" />
            <span>Private Local-First Fibre Companion</span>
          </div>

          <h1 className="text-3xl sm:text-4xl font-serif-display font-bold leading-tight">
            Welcome back to your Stitchbook
          </h1>

          <p className="text-sm text-stone-300 leading-relaxed">
            Track knitting, crochet, Tunisian, and loom projects with step-by-step pattern execution, inventory tools, and portable local storage.
          </p>

          <div className="pt-2 flex flex-wrap items-center gap-3">
            {lastActiveExecutionInfo && (
              <button
                onClick={() => onOpenFocusMode(
                  lastActiveExecutionInfo!.guide,
                  lastActiveExecutionInfo!.revision,
                  lastActiveExecutionInfo!.execution,
                  lastActiveExecutionInfo!.project
                )}
                className="flex items-center gap-2.5 px-5 py-3 bg-rose-700 hover:bg-rose-800 text-white rounded-2xl text-xs font-semibold shadow-lg transition-all cursor-pointer"
              >
                <Play className="w-4 h-4 fill-white" />
                <span>Resume Crafting: {lastActiveExecutionInfo.guide.name}</span>
              </button>
            )}

            <button
              onClick={onAddProject}
              className="flex items-center gap-2 px-5 py-3 bg-stone-800 hover:bg-stone-700 text-stone-100 rounded-2xl text-xs font-semibold border border-stone-700 transition-colors cursor-pointer"
            >
              <Plus className="w-4 h-4" />
              <span>New Project</span>
            </button>
          </div>
        </div>
      </div>

      {/* Craft Stats Grid */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <div className="bg-white p-5 rounded-3xl border border-stone-200/80 shadow-2xs space-y-1">
          <span className="text-xs font-semibold text-stone-400 uppercase tracking-wider">Active Projects</span>
          <div className="text-2xl font-serif-display font-bold text-stone-900">{activeProjects.length}</div>
        </div>
        <div className="bg-white p-5 rounded-3xl border border-stone-200/80 shadow-2xs space-y-1">
          <span className="text-xs font-semibold text-stone-400 uppercase tracking-wider">Total Projects</span>
          <div className="text-2xl font-serif-display font-bold text-stone-900">{projects.length}</div>
        </div>
        <div className="bg-white p-5 rounded-3xl border border-stone-200/80 shadow-2xs space-y-1">
          <span className="text-xs font-semibold text-stone-400 uppercase tracking-wider">Craft Types</span>
          <div className="text-2xl font-serif-display font-bold text-stone-900">
            {new Set(projects.map(p => p.craft)).size}
          </div>
        </div>
        <div className="bg-white p-5 rounded-3xl border border-stone-200/80 shadow-2xs space-y-1">
          <span className="text-xs font-semibold text-stone-400 uppercase tracking-wider">Data Mode</span>
          <div className="text-sm font-semibold text-emerald-700">100% Offline Local</div>
        </div>
      </div>

      {/* Quick Navigation Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-5">
        <div
          onClick={() => onNavigate('PROJECTS')}
          className="bg-white p-6 rounded-3xl border border-stone-200 shadow-2xs hover:shadow-md hover:border-stone-300 transition-all cursor-pointer space-y-3 group"
        >
          <div className="w-10 h-10 bg-rose-50 text-rose-800 rounded-2xl flex items-center justify-center">
            <Folder className="w-5 h-5" />
          </div>
          <div>
            <h3 className="text-base font-serif-display font-bold text-stone-900 group-hover:text-rose-800 transition-colors">
              Project Workspace
            </h3>
            <p className="text-xs text-stone-500 mt-1">
              View and manage active, planned, and completed fibre craft records.
            </p>
          </div>
          <div className="pt-2 text-xs font-semibold text-rose-800 flex items-center gap-1 group-hover:gap-2 transition-all">
            <span>Explore Projects</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </div>
        </div>

        <div
          onClick={() => onNavigate('STASH')}
          className="bg-white p-6 rounded-3xl border border-stone-200 shadow-2xs hover:shadow-md hover:border-stone-300 transition-all cursor-pointer space-y-3 group"
        >
          <div className="w-10 h-10 bg-amber-50 text-amber-800 rounded-2xl flex items-center justify-center">
            <Layers className="w-5 h-5" />
          </div>
          <div>
            <h3 className="text-base font-serif-display font-bold text-stone-900 group-hover:text-rose-800 transition-colors">
              Yarn & Tools Stash
            </h3>
            <p className="text-xs text-stone-500 mt-1">
              Keep inventory of yarn skeins, needles, hooks, and notions.
            </p>
          </div>
          <div className="pt-2 text-xs font-semibold text-rose-800 flex items-center gap-1 group-hover:gap-2 transition-all">
            <span>Manage Stash</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </div>
        </div>

        <div
          onClick={() => onNavigate('LIBRARY')}
          className="bg-white p-6 rounded-3xl border border-stone-200 shadow-2xs hover:shadow-md hover:border-stone-300 transition-all cursor-pointer space-y-3 group"
        >
          <div className="w-10 h-10 bg-emerald-50 text-emerald-800 rounded-2xl flex items-center justify-center">
            <BookOpen className="w-5 h-5" />
          </div>
          <div>
            <h3 className="text-base font-serif-display font-bold text-stone-900 group-hover:text-rose-800 transition-colors">
              Pattern Library
            </h3>
            <p className="text-xs text-stone-500 mt-1">
              Save pattern references, technique notes, and craft bookmarks.
            </p>
          </div>
          <div className="pt-2 text-xs font-semibold text-rose-800 flex items-center gap-1 group-hover:gap-2 transition-all">
            <span>Open Library</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </div>
        </div>
      </div>

      {/* Active Projects List */}
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-xl font-serif-display font-bold text-stone-900">Active Projects</h2>
          <button
            onClick={() => onNavigate('PROJECTS')}
            className="text-xs font-semibold text-rose-800 hover:underline cursor-pointer"
          >
            View All ({projects.length})
          </button>
        </div>

        {activeProjects.length === 0 ? (
          <div className="bg-white border border-stone-200 rounded-3xl p-8 text-center space-y-2">
            <p className="text-xs text-stone-500">No active projects right now.</p>
            <button
              onClick={onAddProject}
              className="text-xs font-semibold text-rose-800 hover:underline cursor-pointer"
            >
              + Start a new project
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {activeProjects.map(proj => (
              <div
                key={proj.id}
                onClick={() => onOpenProject(proj.id)}
                className="bg-white p-5 rounded-2xl border border-stone-200/90 hover:border-stone-300 shadow-2xs transition-all cursor-pointer flex items-center justify-between gap-4"
              >
                <div>
                  <span className="text-[11px] font-semibold text-rose-800 uppercase tracking-wider">
                    {CraftLabels[proj.craft]}
                  </span>
                  <h3 className="text-base font-serif-display font-semibold text-stone-900">{proj.name}</h3>
                  <p className="text-xs text-stone-500 mt-0.5">
                    Updated {new Date(proj.updatedAt).toLocaleDateString()}
                  </p>
                </div>

                <div className="p-2 bg-stone-100 rounded-xl text-stone-600">
                  <ArrowRight className="w-4 h-4" />
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

    </div>
  );
};
