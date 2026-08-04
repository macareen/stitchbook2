import {
  Project,
  Craft,
  ProjectType,
  ProjectStatus,
  Guide,
  GuideDraft,
  DefinitionRevision,
  ExecutionState,
  ExecutionStatus,
  DraftNodeType,
  DraftNode,
  GuideNode,
  GuideDefinition,
  StashItem,
  StashCategory,
  LibraryItem
} from '../types';

const STORAGE_KEYS = {
  PROJECTS: 'stitchbook_projects_v1',
  GUIDES: 'stitchbook_guides_v1',
  DRAFTS: 'stitchbook_drafts_v1',
  REVISIONS: 'stitchbook_revisions_v1',
  EXECUTIONS: 'stitchbook_executions_v1',
  STASH: 'stitchbook_stash_v1',
  LIBRARY: 'stitchbook_library_v1'
};

function getItem<T>(key: string, defaultValue: T): T {
  try {
    const data = localStorage.getItem(key);
    return data ? JSON.parse(data) : defaultValue;
  } catch (e) {
    console.error(`Failed to load ${key} from localStorage`, e);
    return defaultValue;
  }
}

function setItem<T>(key: string, value: T): void {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch (e) {
    console.error(`Failed to save ${key} to localStorage`, e);
  }
}

// SEED INITIAL DATA IF EMPTY
export function initializeDatabase(): void {
  const existingProjects = getItem<Project[]>(STORAGE_KEYS.PROJECTS, []);
  if (existingProjects.length > 0) return;

  const now = Date.now();

  // Seed Project 1
  const proj1: Project = {
    id: 'proj-cardigan',
    name: 'Everyday Cardigan',
    craft: Craft.KNITTING,
    projectType: ProjectType.CARDIGAN,
    status: ProjectStatus.ACTIVE,
    notes: 'Adjusted sleeve length by 1 inch. Using 4.0mm circular needles for collar and 4.5mm for body.',
    createdAt: now - 86400000 * 5,
    updatedAt: now - 3600000
  };

  // Seed Project 2
  const proj2: Project = {
    id: 'proj-blanket',
    name: 'Chevron Throw Blanket',
    craft: Craft.CROCHET,
    projectType: ProjectType.BLANKET,
    status: ProjectStatus.ACTIVE,
    notes: 'Using worsted weight yarn in Sage, Cream, and Terracotta tones. 5.0mm hook.',
    createdAt: now - 86400000 * 12,
    updatedAt: now - 86400000 * 2
  };

  // Seed Project 3
  const proj3: Project = {
    id: 'proj-beanie',
    name: 'Alpine Beanie',
    craft: Craft.TUNISIAN_CROCHET,
    projectType: ProjectType.HAT,
    status: ProjectStatus.PLANNED,
    notes: 'Tunisian honeycomb stitch cuff with simple stitch body.',
    createdAt: now - 86400000 * 20,
    updatedAt: now - 86400000 * 10
  };

  setItem(STORAGE_KEYS.PROJECTS, [proj1, proj2, proj3]);

  // Seed Guide for Project 1
  const guide1: Guide = {
    id: 'guide-cardigan-body',
    projectId: 'proj-cardigan',
    name: 'Body & Textured Lace Panel',
    notes: 'Follow ribbing instructions carefully before transitioning to the 3-repeat textured lace.',
    createdAt: now - 86400000 * 4,
    updatedAt: now - 3600000 * 2
  };

  const guide2: Guide = {
    id: 'guide-cardigan-sleeves',
    projectId: 'proj-cardigan',
    name: 'Sleeves & Cuff (Draft)',
    notes: null,
    createdAt: now - 86400000 * 3,
    updatedAt: now - 86400000 * 3
  };

  setItem(STORAGE_KEYS.GUIDES, [guide1, guide2]);

  // Nodes for Guide 1 Definition
  const nodesGuide1: GuideNode[] = [
    {
      id: 'sec-body',
      type: 'SECTION',
      title: 'Body Setup & Ribbing',
      children: ['rng-ribbing', 'rep-lace']
    },
    {
      id: 'rng-ribbing',
      type: 'RANGE',
      unitLabel: 'row',
      startInclusive: 1,
      endInclusive: 4,
      children: ['inst-rib-1', 'inst-rib-2']
    },
    {
      id: 'inst-rib-1',
      type: 'INSTRUCTION',
      instructionText: 'Cast on 120 stitches loosely using long-tail cast-on.'
    },
    {
      id: 'inst-rib-2',
      type: 'INSTRUCTION',
      instructionText: 'Knit 2, Purl 2 ribbing across to end of row.'
    },
    {
      id: 'rep-lace',
      type: 'REPEAT',
      count: 3,
      label: 'Lace Motif',
      children: ['inst-lace-1', 'inst-lace-2']
    },
    {
      id: 'inst-lace-1',
      type: 'INSTRUCTION',
      instructionText: 'Yarn over, slip 1, knit 1, pass slipped stitch over, knit to last 2 stitches, knit 2 together.'
    },
    {
      id: 'inst-lace-2',
      type: 'INSTRUCTION',
      instructionText: 'Purl across all stitches on wrong side.'
    }
  ];

  const def1: GuideDefinition = {
    guideId: 'guide-cardigan-body',
    revisionId: 'rev-cardigan-body-1',
    rootNodeIds: ['sec-body'],
    nodes: nodesGuide1
  };

  const rev1: DefinitionRevision = {
    id: 'rev-cardigan-body-1',
    guideId: 'guide-cardigan-body',
    revisionNumber: 1,
    createdAt: now - 86400000 * 4,
    definition: def1
  };

  setItem(STORAGE_KEYS.REVISIONS, [rev1]);

  // Seed Draft 1
  const draftNodes1: DraftNode[] = [
    {
      id: 'sec-body',
      type: DraftNodeType.SECTION,
      title: 'Body Setup & Ribbing',
      children: ['rng-ribbing', 'rep-lace']
    },
    {
      id: 'rng-ribbing',
      type: DraftNodeType.RANGE,
      rangeUnitLabel: 'row',
      rangeStartInclusive: 1,
      rangeEndInclusive: 4,
      children: ['inst-rib-1', 'inst-rib-2']
    },
    {
      id: 'inst-rib-1',
      type: DraftNodeType.INSTRUCTION,
      instructionText: 'Cast on 120 stitches loosely using long-tail cast-on.'
    },
    {
      id: 'inst-rib-2',
      type: DraftNodeType.INSTRUCTION,
      instructionText: 'Knit 2, Purl 2 ribbing across to end of row.'
    },
    {
      id: 'rep-lace',
      type: DraftNodeType.REPEAT,
      repeatCount: 3,
      repeatLabel: 'Lace Motif',
      children: ['inst-lace-1', 'inst-lace-2']
    },
    {
      id: 'inst-lace-1',
      type: DraftNodeType.INSTRUCTION,
      instructionText: 'Yarn over, slip 1, knit 1, pass slipped stitch over, knit to last 2 stitches, knit 2 together.'
    },
    {
      id: 'inst-lace-2',
      type: DraftNodeType.INSTRUCTION,
      instructionText: 'Purl across all stitches on wrong side.'
    }
  ];

  const draft1: GuideDraft = {
    id: 'draft-cardigan-body',
    guideId: 'guide-cardigan-body',
    baseRevisionId: 'rev-cardigan-body-1',
    createdAt: now - 86400000 * 4,
    updatedAt: now - 3600000 * 2,
    version: 1,
    rootNodeIds: ['sec-body'],
    nodes: draftNodes1
  };

  const draft2: GuideDraft = {
    id: 'draft-cardigan-sleeves',
    guideId: 'guide-cardigan-sleeves',
    baseRevisionId: null,
    createdAt: now - 86400000 * 3,
    updatedAt: now - 86400000 * 3,
    version: 1,
    rootNodeIds: ['inst-sleeve-caston'],
    nodes: [
      {
        id: 'inst-sleeve-caston',
        type: DraftNodeType.INSTRUCTION,
        instructionText: 'Cast on 44 stitches on double pointed needles.'
      }
    ]
  };

  setItem(STORAGE_KEYS.DRAFTS, [draft1, draft2]);

  // Execution for Guide 1
  const activeExecution: ExecutionState = {
    executionId: 'exec-cardigan-body-1',
    guideId: 'guide-cardigan-body',
    definitionRevisionId: 'rev-cardigan-body-1',
    currentAddress: {
      definitionRevisionId: 'rev-cardigan-body-1',
      instructionNodeId: 'inst-lace-1',
      ancestryFrames: [
        { type: 'REPEAT', containerNodeId: 'rep-lace', iteration: 2 }
      ]
    },
    completedAddresses: [
      {
        definitionRevisionId: 'rev-cardigan-body-1',
        instructionNodeId: 'inst-rib-1',
        ancestryFrames: [{ type: 'RANGE', containerNodeId: 'rng-ribbing', value: 1 }]
      },
      {
        definitionRevisionId: 'rev-cardigan-body-1',
        instructionNodeId: 'inst-rib-2',
        ancestryFrames: [{ type: 'RANGE', containerNodeId: 'rng-ribbing', value: 1 }]
      },
      {
        definitionRevisionId: 'rev-cardigan-body-1',
        instructionNodeId: 'inst-rib-1',
        ancestryFrames: [{ type: 'RANGE', containerNodeId: 'rng-ribbing', value: 2 }]
      },
      {
        definitionRevisionId: 'rev-cardigan-body-1',
        instructionNodeId: 'inst-lace-1',
        ancestryFrames: [{ type: 'REPEAT', containerNodeId: 'rep-lace', iteration: 1 }]
      },
      {
        definitionRevisionId: 'rev-cardigan-body-1',
        instructionNodeId: 'inst-lace-2',
        ancestryFrames: [{ type: 'REPEAT', containerNodeId: 'rep-lace', iteration: 1 }]
      }
    ],
    status: ExecutionStatus.ACTIVE,
    createdAt: now - 86400000 * 2,
    updatedAt: now - 3600000,
    version: 5
  };

  setItem(STORAGE_KEYS.EXECUTIONS, [activeExecution]);

  // Seed Stash Items
  const stash: StashItem[] = [
    {
      id: 'stash-1',
      name: 'Cascadia Merino Wool',
      category: StashCategory.YARN,
      brand: 'Cascade Yarns',
      colorway: 'Forest Moss',
      dyeLot: '4021',
      weightCategory: 'Worsted',
      fiberContent: '100% Superwash Merino Wool',
      quantity: 4,
      unitLabel: 'skeins',
      yardagePerUnit: 220,
      notes: 'Reserved for Everyday Cardigan.',
      createdAt: now - 86400000 * 15,
      updatedAt: now - 86400000 * 5
    },
    {
      id: 'stash-2',
      name: 'Soft Alpaca Heather',
      category: StashCategory.YARN,
      brand: 'Knit Picks',
      colorway: 'Terracotta',
      dyeLot: '1092',
      weightCategory: 'Fingering',
      fiberContent: '70% Alpaca, 30% Silk',
      quantity: 6,
      unitLabel: 'skeins',
      yardagePerUnit: 400,
      notes: 'Super soft blend for shawls or light sweaters.',
      createdAt: now - 86400000 * 25,
      updatedAt: now - 86400000 * 10
    },
    {
      id: 'stash-3',
      name: 'ChiaoGoo Red Lace Circulars 4.0mm / US 6',
      category: StashCategory.NEEDLES_HOOKS,
      brand: 'ChiaoGoo',
      quantity: 1,
      unitLabel: 'pair',
      notes: '32 inch stainless steel cable.',
      createdAt: now - 86400000 * 30,
      updatedAt: now - 86400000 * 30
    },
    {
      id: 'stash-4',
      name: 'Clover Ergonomic Crochet Hook 5.0mm',
      category: StashCategory.NEEDLES_HOOKS,
      brand: 'Clover',
      quantity: 1,
      unitLabel: 'pc',
      notes: 'Comfort grip handle.',
      createdAt: now - 86400000 * 30,
      updatedAt: now - 86400000 * 30
    },
    {
      id: 'stash-5',
      name: 'Locking Stitch Markers Set',
      category: StashCategory.NOTIONS,
      brand: 'Clover',
      quantity: 30,
      unitLabel: 'pcs',
      notes: 'Colorful locking stitch markers.',
      createdAt: now - 86400000 * 40,
      updatedAt: now - 86400000 * 40
    }
  ];

  setItem(STORAGE_KEYS.STASH, stash);

  // Seed Library Items
  const library: LibraryItem[] = [
    {
      id: 'lib-1',
      title: 'Seamless Top-Down Raglan Construction Guide',
      craft: Craft.KNITTING,
      author: 'Stitchbook Craft Library',
      sourceUrl: 'https://stitchbook.local/guides/raglan',
      tags: ['sweater', 'raglan', 'technique'],
      notes: 'Calculations for raglan increases and neck shaping.',
      bookmarked: true,
      createdAt: now - 86400000 * 10,
      updatedAt: now - 86400000 * 2
    },
    {
      id: 'lib-2',
      title: 'Tunisian Honeycomb & Ribbed Cuff Handbook',
      craft: Craft.TUNISIAN_CROCHET,
      author: 'Stitchbook Craft Library',
      tags: ['tunisian', 'stitches', 'hats'],
      notes: 'Alternating forward pass and return pass rhythm.',
      bookmarked: true,
      createdAt: now - 86400000 * 15,
      updatedAt: now - 86400000 * 5
    },
    {
      id: 'lib-3',
      title: 'Classic Ripple & Chevron Crochet Charts',
      craft: Craft.CROCHET,
      author: 'Fibre Craft Collective',
      tags: ['blanket', 'chevron', 'crochet'],
      notes: 'Includes color transition recommendations.',
      bookmarked: false,
      createdAt: now - 86400000 * 20,
      updatedAt: now - 86400000 * 8
    }
  ];

  setItem(STORAGE_KEYS.LIBRARY, library);
}

// REPOSITORY OPERATIONS

// PROJECTS
export function getProjects(): Project[] {
  initializeDatabase();
  const projects = getItem<Project[]>(STORAGE_KEYS.PROJECTS, []);
  // Order by Active, Planned, Paused, Completed, Abandoned, then updated_at DESC
  const statusPriority: Record<ProjectStatus, number> = {
    [ProjectStatus.ACTIVE]: 1,
    [ProjectStatus.PLANNED]: 2,
    [ProjectStatus.PAUSED]: 3,
    [ProjectStatus.COMPLETED]: 4,
    [ProjectStatus.ABANDONED]: 5
  };

  return [...projects].sort((a, b) => {
    const prioA = statusPriority[a.status] || 99;
    const prioB = statusPriority[b.status] || 99;
    if (prioA !== prioB) return prioA - prioB;
    return b.updatedAt - a.updatedAt;
  });
}

export function getProjectById(id: string): Project | undefined {
  return getProjects().find(p => p.id === id);
}

export function saveProject(project: Omit<Project, 'id' | 'createdAt' | 'updatedAt'> & { id?: string }): Project {
  const projects = getItem<Project[]>(STORAGE_KEYS.PROJECTS, []);
  const now = Date.now();

  if (project.id) {
    const index = projects.findIndex(p => p.id === project.id);
    if (index !== -1) {
      const updated: Project = {
        ...projects[index],
        ...project,
        updatedAt: now
      };
      projects[index] = updated;
      setItem(STORAGE_KEYS.PROJECTS, projects);
      return updated;
    }
  }

  const newProject: Project = {
    id: 'proj-' + Math.random().toString(36).substring(2, 10),
    name: project.name,
    craft: project.craft,
    projectType: project.projectType,
    status: project.status,
    notes: project.notes,
    createdAt: now,
    updatedAt: now
  };

  projects.unshift(newProject);
  setItem(STORAGE_KEYS.PROJECTS, projects);
  return newProject;
}

export function deleteProject(id: string): void {
  let projects = getItem<Project[]>(STORAGE_KEYS.PROJECTS, []);
  projects = projects.filter(p => p.id !== id);
  setItem(STORAGE_KEYS.PROJECTS, projects);

  // Cascade delete guides
  const guides = getItem<Guide[]>(STORAGE_KEYS.GUIDES, []).filter(g => g.projectId === id);
  guides.forEach(g => deleteGuide(g.id));
}

// GUIDES & DRAFTS
export function getGuidesForProject(projectId: string): Guide[] {
  initializeDatabase();
  return getItem<Guide[]>(STORAGE_KEYS.GUIDES, []).filter(g => g.projectId === projectId);
}

export function getGuideById(guideId: string): Guide | undefined {
  return getItem<Guide[]>(STORAGE_KEYS.GUIDES, []).find(g => g.id === guideId);
}

export function createGuide(projectId: string, name: string): Guide {
  const guides = getItem<Guide[]>(STORAGE_KEYS.GUIDES, []);
  const now = Date.now();
  const guideId = 'guide-' + Math.random().toString(36).substring(2, 10);

  const newGuide: Guide = {
    id: guideId,
    projectId,
    name,
    notes: null,
    createdAt: now,
    updatedAt: now
  };

  guides.push(newGuide);
  setItem(STORAGE_KEYS.GUIDES, guides);

  // Create empty draft
  const drafts = getItem<GuideDraft[]>(STORAGE_KEYS.DRAFTS, []);
  const draftId = 'draft-' + Math.random().toString(36).substring(2, 10);
  const newDraft: GuideDraft = {
    id: draftId,
    guideId,
    baseRevisionId: null,
    createdAt: now,
    updatedAt: now,
    version: 1,
    rootNodeIds: [],
    nodes: []
  };

  drafts.push(newDraft);
  setItem(STORAGE_KEYS.DRAFTS, drafts);

  return newGuide;
}

export function deleteGuide(guideId: string): void {
  const guides = getItem<Guide[]>(STORAGE_KEYS.GUIDES, []).filter(g => g.id !== guideId);
  setItem(STORAGE_KEYS.GUIDES, guides);

  const drafts = getItem<GuideDraft[]>(STORAGE_KEYS.DRAFTS, []).filter(d => d.guideId !== guideId);
  setItem(STORAGE_KEYS.DRAFTS, drafts);

  const revisions = getItem<DefinitionRevision[]>(STORAGE_KEYS.REVISIONS, []).filter(r => r.guideId !== guideId);
  setItem(STORAGE_KEYS.REVISIONS, revisions);

  const executions = getItem<ExecutionState[]>(STORAGE_KEYS.EXECUTIONS, []).filter(e => e.guideId !== guideId);
  setItem(STORAGE_KEYS.EXECUTIONS, executions);
}

export function getDraftForGuide(guideId: string): GuideDraft | undefined {
  initializeDatabase();
  return getItem<GuideDraft[]>(STORAGE_KEYS.DRAFTS, []).find(d => d.guideId === guideId);
}

export function saveDraft(draft: GuideDraft): GuideDraft {
  const drafts = getItem<GuideDraft[]>(STORAGE_KEYS.DRAFTS, []);
  const index = drafts.findIndex(d => d.id === draft.id);
  const now = Date.now();

  const updated: GuideDraft = {
    ...draft,
    updatedAt: now,
    version: draft.version + 1
  };

  if (index !== -1) {
    drafts[index] = updated;
  } else {
    drafts.push(updated);
  }

  setItem(STORAGE_KEYS.DRAFTS, drafts);

  // Also touch guide updated time
  const guides = getItem<Guide[]>(STORAGE_KEYS.GUIDES, []);
  const gIndex = guides.findIndex(g => g.id === draft.guideId);
  if (gIndex !== -1) {
    guides[gIndex].updatedAt = now;
    setItem(STORAGE_KEYS.GUIDES, guides);
  }

  return updated;
}

// REVISIONS
export function getLatestRevision(guideId: string): DefinitionRevision | undefined {
  initializeDatabase();
  const revisions = getItem<DefinitionRevision[]>(STORAGE_KEYS.REVISIONS, [])
    .filter(r => r.guideId === guideId)
    .sort((a, b) => b.revisionNumber - a.revisionNumber);

  return revisions[0];
}

export function getRevisionById(revisionId: string): DefinitionRevision | undefined {
  return getItem<DefinitionRevision[]>(STORAGE_KEYS.REVISIONS, []).find(r => r.id === revisionId);
}

export function publishDraftAsRevision(guideId: string): DefinitionRevision {
  const draft = getDraftForGuide(guideId);
  if (!draft || draft.nodes.length === 0) {
    throw new Error("Cannot publish an empty draft");
  }

  const existingRevisions = getItem<DefinitionRevision[]>(STORAGE_KEYS.REVISIONS, []).filter(r => r.guideId === guideId);
  const nextRevNum = existingRevisions.length > 0 ? Math.max(...existingRevisions.map(r => r.revisionNumber)) + 1 : 1;

  // Convert draft nodes to GuideNodes
  const guideNodes: GuideNode[] = draft.nodes.map(dn => {
    switch (dn.type) {
      case DraftNodeType.SECTION:
        return {
          id: dn.id,
          type: 'SECTION',
          title: dn.title || 'Untitled Section',
          children: dn.children || []
        };
      case DraftNodeType.RANGE:
        return {
          id: dn.id,
          type: 'RANGE',
          unitLabel: dn.rangeUnitLabel || 'row',
          startInclusive: dn.rangeStartInclusive ?? 1,
          endInclusive: dn.rangeEndInclusive ?? 1,
          children: dn.children || []
        };
      case DraftNodeType.REPEAT:
        return {
          id: dn.id,
          type: 'REPEAT',
          count: dn.repeatCount ?? 1,
          label: dn.repeatLabel,
          children: dn.children || []
        };
      case DraftNodeType.INSTRUCTION:
        return {
          id: dn.id,
          type: 'INSTRUCTION',
          instructionText: dn.instructionText || ''
        };
    }
  });

  const revId = 'rev-' + Math.random().toString(36).substring(2, 10);
  const now = Date.now();

  const definition: GuideDefinition = {
    guideId,
    revisionId: revId,
    rootNodeIds: draft.rootNodeIds,
    nodes: guideNodes
  };

  const newRevision: DefinitionRevision = {
    id: revId,
    guideId,
    revisionNumber: nextRevNum,
    createdAt: now,
    definition
  };

  const revisions = getItem<DefinitionRevision[]>(STORAGE_KEYS.REVISIONS, []);
  revisions.push(newRevision);
  setItem(STORAGE_KEYS.REVISIONS, revisions);

  // Update draft base revision ID
  draft.baseRevisionId = revId;
  saveDraft(draft);

  return newRevision;
}

// EXECUTIONS
export function getActiveExecutionForGuide(guideId: string): ExecutionState | undefined {
  initializeDatabase();
  const executions = getItem<ExecutionState[]>(STORAGE_KEYS.EXECUTIONS, []);
  return executions.find(e => e.guideId === guideId && e.status === ExecutionStatus.ACTIVE);
}

export function saveExecution(execution: ExecutionState): void {
  const executions = getItem<ExecutionState[]>(STORAGE_KEYS.EXECUTIONS, []);
  const index = executions.findIndex(e => e.executionId === execution.executionId);

  if (index !== -1) {
    executions[index] = execution;
  } else {
    executions.push(execution);
  }

  setItem(STORAGE_KEYS.EXECUTIONS, executions);
}

// STASH
export function getStashItems(): StashItem[] {
  initializeDatabase();
  return getItem<StashItem[]>(STORAGE_KEYS.STASH, []).sort((a, b) => b.updatedAt - a.updatedAt);
}

export function saveStashItem(item: Omit<StashItem, 'id' | 'createdAt' | 'updatedAt'> & { id?: string }): StashItem {
  const stash = getItem<StashItem[]>(STORAGE_KEYS.STASH, []);
  const now = Date.now();

  if (item.id) {
    const index = stash.findIndex(s => s.id === item.id);
    if (index !== -1) {
      const updated: StashItem = {
        ...stash[index],
        ...item,
        updatedAt: now
      };
      stash[index] = updated;
      setItem(STORAGE_KEYS.STASH, stash);
      return updated;
    }
  }

  const newItem: StashItem = {
    id: 'stash-' + Math.random().toString(36).substring(2, 10),
    name: item.name,
    category: item.category,
    brand: item.brand,
    colorway: item.colorway,
    dyeLot: item.dyeLot,
    weightCategory: item.weightCategory,
    fiberContent: item.fiberContent,
    quantity: item.quantity,
    unitLabel: item.unitLabel,
    yardagePerUnit: item.yardagePerUnit,
    notes: item.notes,
    createdAt: now,
    updatedAt: now
  };

  stash.unshift(newItem);
  setItem(STORAGE_KEYS.STASH, stash);
  return newItem;
}

export function deleteStashItem(id: string): void {
  const stash = getItem<StashItem[]>(STORAGE_KEYS.STASH, []).filter(s => s.id !== id);
  setItem(STORAGE_KEYS.STASH, stash);
}

// LIBRARY
export function getLibraryItems(): LibraryItem[] {
  initializeDatabase();
  return getItem<LibraryItem[]>(STORAGE_KEYS.LIBRARY, []).sort((a, b) => b.updatedAt - a.updatedAt);
}

export function saveLibraryItem(item: Omit<LibraryItem, 'id' | 'createdAt' | 'updatedAt'> & { id?: string }): LibraryItem {
  const library = getItem<LibraryItem[]>(STORAGE_KEYS.LIBRARY, []);
  const now = Date.now();

  if (item.id) {
    const index = library.findIndex(l => l.id === item.id);
    if (index !== -1) {
      const updated: LibraryItem = {
        ...library[index],
        ...item,
        updatedAt: now
      };
      library[index] = updated;
      setItem(STORAGE_KEYS.LIBRARY, library);
      return updated;
    }
  }

  const newItem: LibraryItem = {
    id: 'lib-' + Math.random().toString(36).substring(2, 10),
    title: item.title,
    craft: item.craft,
    author: item.author,
    sourceUrl: item.sourceUrl,
    tags: item.tags || [],
    notes: item.notes,
    bookmarked: item.bookmarked ?? false,
    createdAt: now,
    updatedAt: now
  };

  library.unshift(newItem);
  setItem(STORAGE_KEYS.LIBRARY, library);
  return newItem;
}

export function deleteLibraryItem(id: string): void {
  const library = getItem<LibraryItem[]>(STORAGE_KEYS.LIBRARY, []).filter(l => l.id !== id);
  setItem(STORAGE_KEYS.LIBRARY, library);
}

// PORTABLE DATA EXPORT & IMPORT
export function exportPortableDataJson(): string {
  initializeDatabase();
  const exportData = {
    version: 1,
    exportedAt: new Date().toISOString(),
    projects: getItem(STORAGE_KEYS.PROJECTS, []),
    guides: getItem(STORAGE_KEYS.GUIDES, []),
    drafts: getItem(STORAGE_KEYS.DRAFTS, []),
    revisions: getItem(STORAGE_KEYS.REVISIONS, []),
    executions: getItem(STORAGE_KEYS.EXECUTIONS, []),
    stash: getItem(STORAGE_KEYS.STASH, []),
    library: getItem(STORAGE_KEYS.LIBRARY, [])
  };

  return JSON.stringify(exportData, null, 2);
}

export function importPortableDataJson(jsonString: string): boolean {
  try {
    const data = JSON.parse(jsonString);
    if (!data || typeof data !== 'object') return false;

    if (data.projects) setItem(STORAGE_KEYS.PROJECTS, data.projects);
    if (data.guides) setItem(STORAGE_KEYS.GUIDES, data.guides);
    if (data.drafts) setItem(STORAGE_KEYS.DRAFTS, data.drafts);
    if (data.revisions) setItem(STORAGE_KEYS.REVISIONS, data.revisions);
    if (data.executions) setItem(STORAGE_KEYS.EXECUTIONS, data.executions);
    if (data.stash) setItem(STORAGE_KEYS.STASH, data.stash);
    if (data.library) setItem(STORAGE_KEYS.LIBRARY, data.library);

    return true;
  } catch (e) {
    console.error("Failed to import JSON data", e);
    return false;
  }
}

export function resetAllData(): void {
  Object.values(STORAGE_KEYS).forEach(key => localStorage.removeItem(key));
  initializeDatabase();
}
