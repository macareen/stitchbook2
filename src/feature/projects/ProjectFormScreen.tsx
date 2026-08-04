import React, { useState } from 'react';
import {
  Project,
  Craft,
  CraftLabels,
  ProjectStatus,
  ProjectStatusLabels,
  ProjectType,
  ProjectTypeLabels
} from '../../types';
import { saveProject } from '../../services/db';
import { ArrowLeft, Save, AlertCircle } from 'lucide-react';

interface ProjectFormScreenProps {
  initialProject?: Project | null;
  onSaved: (project: Project) => void;
  onCancel: () => void;
}

export const ProjectFormScreen: React.FC<ProjectFormScreenProps> = ({
  initialProject,
  onSaved,
  onCancel
}) => {
  const isEditing = !!initialProject;

  const [name, setName] = useState(initialProject?.name || '');
  const [craft, setCraft] = useState<Craft>(initialProject?.craft || Craft.KNITTING);
  const [projectType, setProjectType] = useState<ProjectType>(initialProject?.projectType || ProjectType.SWEATER);
  const [status, setStatus] = useState<ProjectStatus>(initialProject?.status || ProjectStatus.ACTIVE);
  const [notes, setNotes] = useState(initialProject?.notes || '');

  const [showDiscardModal, setShowDiscardModal] = useState(false);
  const [nameError, setNameError] = useState(false);

  const hasUnsavedChanges =
    name !== (initialProject?.name || '') ||
    craft !== (initialProject?.craft || Craft.KNITTING) ||
    projectType !== (initialProject?.projectType || ProjectType.SWEATER) ||
    status !== (initialProject?.status || ProjectStatus.ACTIVE) ||
    notes !== (initialProject?.notes || '');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      setNameError(true);
      return;
    }

    const saved = saveProject({
      id: initialProject?.id,
      name: name.trim(),
      craft,
      projectType,
      status,
      notes: notes.trim() || null
    });

    onSaved(saved);
  };

  const handleBack = () => {
    if (hasUnsavedChanges) {
      setShowDiscardModal(true);
    } else {
      onCancel();
    }
  };

  return (
    <div className="max-w-2xl mx-auto px-4 sm:px-8 py-8 space-y-8 animate-fade-in">
      
      {/* Top Navigation */}
      <div className="flex items-center justify-between">
        <button
          onClick={handleBack}
          className="flex items-center gap-2 text-stone-600 hover:text-stone-900 font-medium text-sm transition-colors cursor-pointer"
        >
          <ArrowLeft className="w-4 h-4" />
          <span>{isEditing ? 'Cancel Editing' : 'Cancel New Project'}</span>
        </button>
      </div>

      {/* Main Form */}
      <form onSubmit={handleSubmit} className="bg-white rounded-3xl p-6 sm:p-8 border border-stone-200 shadow-2xs space-y-6">
        <div>
          <h1 className="text-2xl font-serif-display font-bold text-stone-900">
            {isEditing ? 'Edit Project' : 'New Craft Project'}
          </h1>
          <p className="text-xs text-stone-500 mt-1">Enter details to organize and track your project</p>
        </div>

        {/* Project Name */}
        <div>
          <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-2">
            Project Name *
          </label>
          <input
            type="text"
            placeholder="e.g. Everyday Cardigan, Winter Scarf..."
            value={name}
            onChange={e => {
              setName(e.target.value);
              if (nameError) setNameError(false);
            }}
            className={`w-full px-4 py-3 bg-stone-50 border rounded-2xl text-sm focus:outline-none focus:ring-2 ${
              nameError ? 'border-rose-400 ring-rose-200' : 'border-stone-200 focus:ring-rose-800'
            }`}
          />
          {nameError && (
            <p className="text-xs text-rose-600 mt-1 flex items-center gap-1">
              <AlertCircle className="w-3.5 h-3.5" /> Project name is required
            </p>
          )}
        </div>

        {/* Craft & Project Type Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-2">
              Craft Type
            </label>
            <select
              value={craft}
              onChange={e => setCraft(e.target.value as Craft)}
              className="w-full px-4 py-3 bg-stone-50 border border-stone-200 rounded-2xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-800 cursor-pointer font-medium text-stone-800"
            >
              {Object.entries(CraftLabels).map(([key, label]) => (
                <option key={key} value={key}>{label}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-2">
              Project Category
            </label>
            <select
              value={projectType}
              onChange={e => setProjectType(e.target.value as ProjectType)}
              className="w-full px-4 py-3 bg-stone-50 border border-stone-200 rounded-2xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-800 cursor-pointer font-medium text-stone-800"
            >
              {Object.entries(ProjectTypeLabels).map(([key, label]) => (
                <option key={key} value={key}>{label}</option>
              ))}
            </select>
          </div>
        </div>

        {/* Status */}
        <div>
          <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-2">
            Status
          </label>
          <select
            value={status}
            onChange={e => setStatus(e.target.value as ProjectStatus)}
            className="w-full px-4 py-3 bg-stone-50 border border-stone-200 rounded-2xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-800 cursor-pointer font-medium text-stone-800"
          >
            {Object.entries(ProjectStatusLabels).map(([key, label]) => (
              <option key={key} value={key}>{label}</option>
            ))}
          </select>
        </div>

        {/* Notes */}
        <div>
          <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-2">
            Project Notes & Specs
          </label>
          <textarea
            rows={4}
            placeholder="Record needles, yarn details, size modifications..."
            value={notes}
            onChange={e => setNotes(e.target.value)}
            className="w-full px-4 py-3 bg-stone-50 border border-stone-200 rounded-2xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-800"
          />
        </div>

        {/* Submit Bar */}
        <div className="pt-4 flex items-center justify-end gap-3 border-t border-stone-100">
          <button
            type="button"
            onClick={handleBack}
            className="px-5 py-2.5 text-stone-600 hover:bg-stone-100 rounded-2xl font-medium text-sm transition-colors cursor-pointer"
          >
            Cancel
          </button>
          <button
            type="submit"
            className="flex items-center gap-2 px-6 py-2.5 bg-rose-800 hover:bg-rose-900 text-white rounded-2xl font-semibold text-sm shadow-md transition-all cursor-pointer"
          >
            <Save className="w-4 h-4" />
            <span>{isEditing ? 'Save Changes' : 'Create Project'}</span>
          </button>
        </div>
      </form>

      {/* Discard Modal */}
      {showDiscardModal && (
        <div className="fixed inset-0 bg-stone-900/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl p-6 max-w-md w-full space-y-4 shadow-xl">
            <h3 className="text-lg font-serif-display font-bold text-stone-900">Discard Unsaved Changes?</h3>
            <p className="text-xs text-stone-600 leading-relaxed">
              You have unsaved changes in this project form. If you leave now, these edits will be lost.
            </p>

            <div className="pt-3 flex items-center justify-end gap-2">
              <button
                onClick={() => setShowDiscardModal(false)}
                className="px-4 py-2 text-xs font-medium text-stone-600 hover:bg-stone-100 rounded-xl cursor-pointer"
              >
                Keep Editing
              </button>
              <button
                onClick={onCancel}
                className="px-4 py-2 bg-rose-700 hover:bg-rose-800 text-white rounded-xl text-xs font-medium cursor-pointer"
              >
                Discard Changes
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
};
