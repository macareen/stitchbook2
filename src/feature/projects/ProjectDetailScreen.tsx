import React, { useState } from 'react';
import {
  Project,
  Guide,
  GuideEntryAction,
  CraftLabels,
  ProjectStatusLabels,
  ProjectTypeLabels,
  DefinitionRevision,
  ExecutionState
} from '../../types';
import {
  getGuidesForProject,
  createGuide,
  deleteProject,
  getLatestRevision,
  getActiveExecutionForGuide,
  saveExecution
} from '../../services/db';
import { ExecutionEngine } from '../../domain/executionEngine';
import {
  ArrowLeft,
  Edit3,
  Trash2,
  Plus,
  Play,
  FileEdit,
  Calendar,
  Layers,
  Sparkles,
  ChevronRight,
  BookOpen
} from 'lucide-react';

interface ProjectDetailScreenProps {
  project: Project;
  onBack: () => void;
  onEditProject: () => void;
  onProjectDeleted: () => void;
  onOpenFocusMode: (guide: Guide, revision: DefinitionRevision, execution: ExecutionState) => void;
  onEditDraft: (guide: Guide) => void;
}

export const ProjectDetailScreen: React.FC<ProjectDetailScreenProps> = ({
  project,
  onBack,
  onEditProject,
  onProjectDeleted,
  onOpenFocusMode,
  onEditDraft
}) => {
  const [guides, setGuides] = useState<Guide[]>(() => getGuidesForProject(project.id));
  const [showAddGuideModal, setShowAddGuideModal] = useState(false);
  const [newGuideName, setNewGuideName] = useState('');
  const [showDeleteModal, setShowDeleteModal] = useState(false);

  const refreshGuides = () => {
    setGuides(getGuidesForProject(project.id));
  };

  const handleCreateGuide = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newGuideName.trim()) return;

    const newGuide = createGuide(project.id, newGuideName.trim());
    setNewGuideName('');
    setShowAddGuideModal(false);
    refreshGuides();
    // Open draft editor for newly created guide
    onEditDraft(newGuide);
  };

  const handleDeleteProject = () => {
    deleteProject(project.id);
    onProjectDeleted();
  };

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-8 py-8 space-y-8 animate-fade-in">
      
      {/* Top Bar Navigation */}
      <div className="flex items-center justify-between">
        <button
          onClick={onBack}
          className="flex items-center gap-2 text-stone-600 hover:text-stone-900 font-medium text-sm transition-colors cursor-pointer"
        >
          <ArrowLeft className="w-4 h-4" />
          <span>Back to Projects</span>
        </button>

        <div className="flex items-center gap-2">
          <button
            onClick={onEditProject}
            className="flex items-center gap-1.5 px-3.5 py-2 bg-stone-100 hover:bg-stone-200 text-stone-800 rounded-xl text-xs font-semibold transition-colors cursor-pointer"
          >
            <Edit3 className="w-3.5 h-3.5" /> Edit Project
          </button>
          <button
            onClick={() => setShowDeleteModal(true)}
            className="flex items-center gap-1.5 px-3.5 py-2 bg-rose-50 hover:bg-rose-100 text-rose-800 rounded-xl text-xs font-semibold transition-colors cursor-pointer"
          >
            <Trash2 className="w-3.5 h-3.5" /> Delete
          </button>
        </div>
      </div>

      {/* Project Header Card */}
      <div className="bg-white rounded-3xl p-6 sm:p-8 border border-stone-200 shadow-2xs space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2.5 mb-2">
              <span className="text-xs font-semibold px-3 py-1 rounded-full bg-stone-100 text-stone-700 border border-stone-200">
                {CraftLabels[project.craft] || project.craft}
              </span>
              <span className="text-xs font-semibold px-3 py-1 rounded-full bg-rose-100 text-rose-900 border border-rose-200">
                {ProjectStatusLabels[project.status] || project.status}
              </span>
            </div>
            <h1 className="text-2xl sm:text-3xl font-serif-display font-bold text-stone-900">{project.name}</h1>
            <p className="text-sm font-medium text-stone-500 mt-1">
              {ProjectTypeLabels[project.projectType] || project.projectType}
            </p>
          </div>

          <div className="text-xs text-stone-500 space-y-1 sm:text-right border-t sm:border-t-0 pt-3 sm:pt-0 border-stone-100">
            <div className="flex items-center sm:justify-end gap-1.5">
              <Calendar className="w-3.5 h-3.5 text-stone-400" />
              <span>Created {new Date(project.createdAt).toLocaleDateString()}</span>
            </div>
            <div>Updated {new Date(project.updatedAt).toLocaleDateString()}</div>
          </div>
        </div>

        {project.notes && (
          <div className="p-4 bg-stone-50 rounded-2xl border border-stone-200/80 text-sm text-stone-700 leading-relaxed whitespace-pre-line">
            <strong className="block text-xs uppercase tracking-wider text-stone-400 font-semibold mb-1">Project Notes</strong>
            {project.notes}
          </div>
        )}
      </div>

      {/* Guides Section */}
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-xl font-serif-display font-bold text-stone-900">Pattern Guides</h2>
            <p className="text-xs text-stone-500">Step-by-step instructions and active execution tracking for this project</p>
          </div>

          <button
            onClick={() => setShowAddGuideModal(true)}
            className="flex items-center gap-2 px-4 py-2 bg-stone-900 hover:bg-stone-800 text-white rounded-xl text-xs font-medium transition-colors cursor-pointer"
          >
            <Plus className="w-4 h-4" /> Add Pattern Guide
          </button>
        </div>

        {guides.length === 0 ? (
          <div className="bg-white border border-stone-200 rounded-3xl p-8 text-center space-y-3">
            <div className="w-10 h-10 bg-stone-100 text-stone-400 rounded-xl flex items-center justify-center mx-auto">
              <BookOpen className="w-5 h-5" />
            </div>
            <p className="text-xs text-stone-500">No pattern guides added to this project yet.</p>
            <button
              onClick={() => setShowAddGuideModal(true)}
              className="text-xs font-semibold text-rose-800 hover:underline cursor-pointer"
            >
              + Create Guide
            </button>
          </div>
        ) : (
          <div className="space-y-3">
            {guides.map(guide => (
              <GuideListItem
                key={guide.id}
                guide={guide}
                onOpenFocusMode={onOpenFocusMode}
                onEditDraft={onEditDraft}
              />
            ))}
          </div>
        )}
      </div>

      {/* Add Guide Modal */}
      {showAddGuideModal && (
        <div className="fixed inset-0 bg-stone-900/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <form onSubmit={handleCreateGuide} className="bg-white rounded-3xl p-6 max-w-md w-full space-y-4 shadow-xl">
            <h3 className="text-lg font-serif-display font-bold text-stone-900">Add Pattern Guide</h3>
            <p className="text-xs text-stone-500">Give your new guide a title (e.g. "Body & Collar", "Sleeves", "Lace Edging")</p>

            <input
              type="text"
              required
              placeholder="Guide Name..."
              value={newGuideName}
              onChange={e => setNewGuideName(e.target.value)}
              className="w-full px-3.5 py-2.5 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-800"
            />

            <div className="pt-2 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setShowAddGuideModal(false)}
                className="px-4 py-2 text-xs font-medium text-stone-600 hover:bg-stone-100 rounded-xl cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="px-4 py-2 bg-rose-800 hover:bg-rose-900 text-white font-medium rounded-xl text-xs cursor-pointer"
              >
                Create Guide Draft
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Delete Confirmation Modal */}
      {showDeleteModal && (
        <div className="fixed inset-0 bg-stone-900/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl p-6 max-w-md w-full space-y-4 shadow-xl">
            <h3 className="text-lg font-serif-display font-bold text-rose-900">Delete Project?</h3>
            <p className="text-xs text-stone-600 leading-relaxed">
              Are you sure you want to delete <strong className="text-stone-900">{project.name}</strong>? All associated pattern guides and execution states will be permanently removed.
            </p>

            <div className="pt-3 flex items-center justify-end gap-2">
              <button
                onClick={() => setShowDeleteModal(false)}
                className="px-4 py-2 text-xs font-medium text-stone-600 hover:bg-stone-100 rounded-xl cursor-pointer"
              >
                Cancel
              </button>
              <button
                onClick={handleDeleteProject}
                className="px-4 py-2 bg-rose-700 hover:bg-rose-800 text-white rounded-xl text-xs font-medium cursor-pointer"
              >
                Delete Project
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
};

interface GuideListItemProps {
  guide: Guide;
  onOpenFocusMode: (guide: Guide, revision: DefinitionRevision, execution: ExecutionState) => void;
  onEditDraft: (guide: Guide) => void;
}

const GuideListItem: React.FC<GuideListItemProps> = ({
  guide,
  onOpenFocusMode,
  onEditDraft
}) => {
  const latestRevision = getLatestRevision(guide.id);
  const activeExecution = getActiveExecutionForGuide(guide.id);

  let action: GuideEntryAction = GuideEntryAction.NOT_EXECUTABLE;
  if (latestRevision) {
    if (activeExecution) {
      action = GuideEntryAction.CONTINUE;
    } else {
      action = GuideEntryAction.START;
    }
  }

  const handleActionClick = () => {
    if (action === GuideEntryAction.NOT_EXECUTABLE) {
      onEditDraft(guide);
      return;
    }

    if (latestRevision) {
      let exec = activeExecution;
      if (!exec) {
        const engine = ExecutionEngine.forDefinition(latestRevision.definition);
        exec = engine.newExecution('exec-' + Math.random().toString(36).substring(2, 9));
        saveExecution(exec);
      }
      onOpenFocusMode(guide, latestRevision, exec);
    }
  };

  return (
    <div className="bg-white rounded-2xl p-5 border border-stone-200 shadow-2xs hover:border-stone-300 transition-all flex flex-col sm:flex-row sm:items-center justify-between gap-4">
      <div className="space-y-1">
        <h3 className="text-base font-serif-display font-semibold text-stone-900">{guide.name}</h3>
        <p className="text-xs text-stone-500">
          {latestRevision ? `Revision #${latestRevision.revisionNumber}` : 'Draft Mode — Not yet published'}
        </p>
      </div>

      <div className="flex items-center gap-2 self-start sm:self-auto">
        <button
          onClick={() => onEditDraft(guide)}
          className="px-3 py-2 bg-stone-100 hover:bg-stone-200 text-stone-700 rounded-xl text-xs font-medium transition-colors flex items-center gap-1.5 cursor-pointer"
        >
          <FileEdit className="w-3.5 h-3.5" /> Edit Draft
        </button>

        {action === GuideEntryAction.CONTINUE && (
          <button
            onClick={handleActionClick}
            className="px-4 py-2 bg-rose-800 hover:bg-rose-900 text-white rounded-xl text-xs font-semibold transition-all shadow-2xs flex items-center gap-2 cursor-pointer"
          >
            <Play className="w-3.5 h-3.5 fill-white" /> Continue Crafting
          </button>
        )}

        {action === GuideEntryAction.START && (
          <button
            onClick={handleActionClick}
            className="px-4 py-2 bg-stone-900 hover:bg-stone-800 text-white rounded-xl text-xs font-semibold transition-all shadow-2xs flex items-center gap-2 cursor-pointer"
          >
            <Play className="w-3.5 h-3.5 fill-white" /> Start Guide
          </button>
        )}
      </div>
    </div>
  );
};
