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
import { Plus, Search, Filter, FolderCheck, ChevronRight } from 'lucide-react';

interface ProjectsScreenProps {
  projects: Project[];
  onAddProject: () => Unit; // void
  onOpenProject: (projectId: string) => void;
}

type Unit = void;

export const ProjectsScreen: React.FC<ProjectsScreenProps> = ({
  projects,
  onAddProject,
  onOpenProject
}) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedCraft, setSelectedCraft] = useState<string>('ALL');
  const [selectedStatus, setSelectedStatus] = useState<string>('ALL');

  const filteredProjects = projects.filter(p => {
    const matchesSearch = p.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      (p.notes && p.notes.toLowerCase().includes(searchTerm.toLowerCase()));
    const matchesCraft = selectedCraft === 'ALL' || p.craft === selectedCraft;
    const matchesStatus = selectedStatus === 'ALL' || p.status === selectedStatus;

    return matchesSearch && matchesCraft && matchesStatus;
  });

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-8 py-8 space-y-8 animate-fade-in">
      
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl sm:text-3xl font-serif-display font-bold text-stone-900">Projects</h1>
          <p className="text-sm text-stone-500 mt-1">Manage your craft projects, progress, and execution guides</p>
        </div>

        <button
          onClick={onAddProject}
          className="flex items-center justify-center gap-2 px-5 py-2.5 bg-rose-800 hover:bg-rose-900 text-white rounded-2xl font-medium text-sm shadow-sm transition-all cursor-pointer self-start sm:self-auto"
        >
          <Plus className="w-4 h-4" />
          <span>New Project</span>
        </button>
      </div>

      {/* Filter and Search Bar */}
      <div className="bg-white p-4 rounded-2xl border border-stone-200 shadow-2xs space-y-3 sm:space-y-0 sm:flex sm:items-center sm:gap-4">
        <div className="relative flex-1">
          <Search className="w-4 h-4 text-stone-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            placeholder="Search projects by name or notes..."
            value={searchTerm}
            onChange={e => setSearchTerm(e.target.value)}
            className="w-full pl-10 pr-4 py-2 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-800"
          />
        </div>

        <div className="flex items-center gap-2 overflow-x-auto pb-1 sm:pb-0">
          <div className="flex items-center gap-1.5 bg-stone-50 px-3 py-1.5 rounded-xl border border-stone-200 text-xs shrink-0">
            <Filter className="w-3.5 h-3.5 text-stone-500" />
            <select
              value={selectedCraft}
              onChange={e => setSelectedCraft(e.target.value)}
              className="bg-transparent font-medium text-stone-700 focus:outline-none cursor-pointer"
            >
              <option value="ALL">All Crafts</option>
              {Object.entries(CraftLabels).map(([key, label]) => (
                <option key={key} value={key}>{label}</option>
              ))}
            </select>
          </div>

          <div className="flex items-center gap-1.5 bg-stone-50 px-3 py-1.5 rounded-xl border border-stone-200 text-xs shrink-0">
            <select
              value={selectedStatus}
              onChange={e => setSelectedStatus(e.target.value)}
              className="bg-transparent font-medium text-stone-700 focus:outline-none cursor-pointer"
            >
              <option value="ALL">All Statuses</option>
              {Object.entries(ProjectStatusLabels).map(([key, label]) => (
                <option key={key} value={key}>{label}</option>
              ))}
            </select>
          </div>
        </div>
      </div>

      {/* Projects Grid */}
      {filteredProjects.length === 0 ? (
        <div className="bg-white border border-stone-200 rounded-3xl p-12 text-center space-y-4">
          <div className="w-12 h-12 bg-stone-100 text-stone-400 rounded-2xl flex items-center justify-center mx-auto">
            <FolderCheck className="w-6 h-6" />
          </div>
          <h3 className="text-base font-semibold text-stone-800">No projects found</h3>
          <p className="text-xs text-stone-500 max-w-sm mx-auto">
            {searchTerm || selectedCraft !== 'ALL' || selectedStatus !== 'ALL'
              ? 'Try clearing your search filters to see all projects.'
              : 'Click "New Project" to create your first craft project record.'}
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {filteredProjects.map(project => (
            <ProjectCard
              key={project.id}
              project={project}
              onClick={() => onOpenProject(project.id)}
            />
          ))}
        </div>
      )}

    </div>
  );
};

interface ProjectCardProps {
  project: Project;
  onClick: () => void;
}

const ProjectCard: React.FC<ProjectCardProps> = ({ project, onClick }) => {
  let statusBg = 'bg-stone-100 text-stone-700 border-stone-200';
  if (project.status === ProjectStatus.ACTIVE) {
    statusBg = 'bg-rose-100 text-rose-900 border-rose-200';
  } else if (project.status === ProjectStatus.COMPLETED) {
    statusBg = 'bg-emerald-100 text-emerald-900 border-emerald-200';
  } else if (project.status === ProjectStatus.PLANNED) {
    statusBg = 'bg-amber-100 text-amber-900 border-amber-200';
  }

  return (
    <div
      onClick={onClick}
      className="bg-white rounded-3xl p-6 border border-stone-200 shadow-2xs hover:shadow-md hover:border-stone-300 transition-all cursor-pointer flex flex-col justify-between space-y-4 group"
    >
      <div className="space-y-3">
        <div className="flex items-start justify-between gap-2">
          <span className="text-xs font-semibold px-2.5 py-1 rounded-full bg-stone-100 text-stone-600 border border-stone-200/80">
            {CraftLabels[project.craft] || project.craft}
          </span>
          <span className={`text-xs font-semibold px-2.5 py-1 rounded-full border ${statusBg}`}>
            {ProjectStatusLabels[project.status] || project.status}
          </span>
        </div>

        <div>
          <h3 className="text-lg font-serif-display font-semibold text-stone-900 group-hover:text-rose-800 transition-colors">
            {project.name}
          </h3>
          <p className="text-xs text-stone-500 font-medium mt-0.5">
            {ProjectTypeLabels[project.projectType] || project.projectType}
          </p>
        </div>

        {project.notes && (
          <p className="text-xs text-stone-600 line-clamp-2 leading-relaxed">
            {project.notes}
          </p>
        )}
      </div>

      <div className="pt-3 border-t border-stone-100 flex items-center justify-between text-xs text-stone-500">
        <span>Updated {new Date(project.updatedAt).toLocaleDateString()}</span>
        <ChevronRight className="w-4 h-4 text-stone-400 group-hover:translate-x-1 transition-transform" />
      </div>
    </div>
  );
};
