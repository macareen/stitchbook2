import React, { useState } from 'react';
import {
  NavigationDestination,
  Project,
  Guide,
  DefinitionRevision,
  ExecutionState,
  GuideDraft
} from './types';
import {
  getProjects,
  getProjectById,
  getDraftForGuide,
  getLatestRevision,
  getActiveExecutionForGuide,
  getGuidesForProject,
  saveExecution
} from './services/db';
import { ExecutionEngine } from './domain/executionEngine';
import { Navigation } from './components/Navigation';
import { HomeScreen } from './feature/home/HomeScreen';
import { ProjectsScreen } from './feature/projects/ProjectsScreen';
import { ProjectDetailScreen } from './feature/projects/ProjectDetailScreen';
import { ProjectFormScreen } from './feature/projects/ProjectFormScreen';
import { GuideFocusScreen } from './feature/focus/GuideFocusScreen';
import { DraftEditorScreen } from './feature/draft/DraftEditorScreen';
import { StashScreen } from './feature/stash/StashScreen';
import { LibraryScreen } from './feature/library/LibraryScreen';
import { SettingsScreen } from './feature/settings/SettingsScreen';

export const App: React.FC = () => {
  const [currentDestination, setCurrentDestination] = useState<NavigationDestination>('HOME');

  // Sub-view overlay states
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(null);
  const [isEditingProject, setIsEditingProject] = useState<boolean>(false);
  const [editingProject, setEditingProject] = useState<Project | null>(null);

  // Active Focus Mode state
  const [activeFocusSession, setActiveFocusSession] = useState<{
    project: Project;
    guide: Guide;
    revision: DefinitionRevision;
    execution: ExecutionState;
  } | null>(null);

  // Active Draft Editor state
  const [activeDraftSession, setActiveDraftSession] = useState<{
    project: Project;
    guide: Guide;
    draft: GuideDraft;
  } | null>(null);

  // Find any global active execution to show floating banner
  let globalActiveFocusInfo: {
    guideName: string;
    open: () => void;
  } | null = null;

  if (!activeFocusSession) {
    const projects = getProjects();
    for (const proj of projects) {
      const guides = getGuidesForProject(proj.id);
      for (const guide of guides) {
        const exec = getActiveExecutionForGuide(guide.id);
        const rev = getLatestRevision(guide.id);
        if (exec && rev) {
          globalActiveFocusInfo = {
            guideName: guide.name,
            open: () => {
              setActiveFocusSession({
                project: proj,
                guide,
                revision: rev,
                execution: exec
              });
            }
          };
          break;
        }
      }
      if (globalActiveFocusInfo) break;
    }
  }

  // Navigation handlers
  const handleNavigate = (dest: NavigationDestination) => {
    setCurrentDestination(dest);
    setSelectedProjectId(null);
    setIsEditingProject(false);
    setEditingProject(null);
  };

  const handleOpenProject = (projectId: string) => {
    setSelectedProjectId(projectId);
    setIsEditingProject(false);
  };

  const handleStartNewProject = () => {
    setEditingProject(null);
    setIsEditingProject(true);
  };

  const handleEditProject = (project: Project) => {
    setEditingProject(project);
    setIsEditingProject(true);
  };

  const handleOpenFocusMode = (
    guide: Guide,
    revision: DefinitionRevision,
    execution: ExecutionState,
    project?: Project
  ) => {
    const parentProject = project || (selectedProjectId ? getProjectById(selectedProjectId) : null);
    if (!parentProject) return;

    setActiveFocusSession({
      project: parentProject,
      guide,
      revision,
      execution
    });
  };

  const handleEditDraft = (guide: Guide, project?: Project) => {
    const parentProject = project || (selectedProjectId ? getProjectById(selectedProjectId) : null);
    if (!parentProject) return;

    const draft = getDraftForGuide(guide.id);
    if (!draft) return;

    setActiveDraftSession({
      project: parentProject,
      guide,
      draft
    });
  };

  // Render Overlay Views (Focus Mode / Draft Editor)
  if (activeFocusSession) {
    return (
      <GuideFocusScreen
        project={activeFocusSession.project}
        guide={activeFocusSession.guide}
        revision={activeFocusSession.revision}
        initialExecution={activeFocusSession.execution}
        onClose={() => setActiveFocusSession(null)}
      />
    );
  }

  if (activeDraftSession) {
    return (
      <DraftEditorScreen
        project={activeDraftSession.project}
        guide={activeDraftSession.guide}
        initialDraft={activeDraftSession.draft}
        onClose={() => setActiveDraftSession(null)}
        onPublished={(revisionId) => {
          setActiveDraftSession(null);
          // Automatically launch focus mode for published revision
          const rev = getLatestRevision(activeDraftSession.guide.id);
          if (rev) {
            const engine = ExecutionEngine.forDefinition(rev.definition);
            const exec = engine.newExecution('exec-' + Math.random().toString(36).substring(2, 9));
            saveExecution(exec);
            handleOpenFocusMode(activeDraftSession.guide, rev, exec, activeDraftSession.project);
          }
        }}
      />
    );
  }

  // Render Main Destination / Sub-screen
  const renderMainContent = () => {
    // Project Form
    if (isEditingProject) {
      return (
        <ProjectFormScreen
          initialProject={editingProject}
          onSaved={(saved) => {
            setIsEditingProject(false);
            setSelectedProjectId(saved.id);
          }}
          onCancel={() => setIsEditingProject(false)}
        />
      );
    }

    // Project Detail
    if (selectedProjectId) {
      const project = getProjectById(selectedProjectId);
      if (!project) {
        setSelectedProjectId(null);
        return null;
      }
      return (
        <ProjectDetailScreen
          project={project}
          onBack={() => setSelectedProjectId(null)}
          onEditProject={() => handleEditProject(project)}
          onProjectDeleted={() => setSelectedProjectId(null)}
          onOpenFocusMode={(guide, rev, exec) => handleOpenFocusMode(guide, rev, exec, project)}
          onEditDraft={(guide) => handleEditDraft(guide, project)}
        />
      );
    }

    // Top Level Destinations
    switch (currentDestination) {
      case 'HOME':
        return (
          <HomeScreen
            onNavigate={handleNavigate}
            onOpenProject={handleOpenProject}
            onAddProject={handleStartNewProject}
            onOpenFocusMode={(guide, rev, exec, proj) => handleOpenFocusMode(guide, rev, exec, proj)}
          />
        );
      case 'PROJECTS':
        return (
          <ProjectsScreen
            projects={getProjects()}
            onAddProject={handleStartNewProject}
            onOpenProject={handleOpenProject}
          />
        );
      case 'STASH':
        return <StashScreen />;
      case 'LIBRARY':
        return <LibraryScreen />;
      case 'SETTINGS':
        return <SettingsScreen />;
      default:
        return null;
    }
  };

  return (
    <div className="min-h-screen bg-stone-50 text-stone-900 flex flex-col pb-20 md:pb-8 selection:bg-rose-200">
      <Navigation
        currentDestination={currentDestination}
        onNavigate={handleNavigate}
        activeExecutionGuideName={globalActiveFocusInfo?.guideName}
        onOpenActiveFocus={globalActiveFocusInfo?.open}
      />

      <main className="flex-1">
        {renderMainContent()}
      </main>
    </div>
  );
};

export default App;
