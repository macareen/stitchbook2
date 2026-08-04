export enum Craft {
  KNITTING = 'KNITTING',
  CROCHET = 'CROCHET',
  TUNISIAN_CROCHET = 'TUNISIAN_CROCHET',
  LOOM_KNITTING = 'LOOM_KNITTING',
  OTHER = 'OTHER'
}

export const CraftLabels: Record<Craft, string> = {
  [Craft.KNITTING]: 'Knitting',
  [Craft.CROCHET]: 'Crochet',
  [Craft.TUNISIAN_CROCHET]: 'Tunisian Crochet',
  [Craft.LOOM_KNITTING]: 'Loom Knitting',
  [Craft.OTHER]: 'Other Craft'
};

export enum ProjectStatus {
  PLANNED = 'PLANNED',
  ACTIVE = 'ACTIVE',
  PAUSED = 'PAUSED',
  COMPLETED = 'COMPLETED',
  ABANDONED = 'ABANDONED'
}

export const ProjectStatusLabels: Record<ProjectStatus, string> = {
  [ProjectStatus.PLANNED]: 'Planned',
  [ProjectStatus.ACTIVE]: 'Active',
  [ProjectStatus.PAUSED]: 'Paused',
  [ProjectStatus.COMPLETED]: 'Completed',
  [ProjectStatus.ABANDONED]: 'Abandoned'
};

export enum ProjectType {
  SWEATER = 'SWEATER',
  CARDIGAN = 'CARDIGAN',
  TOP = 'TOP',
  SOCKS = 'SOCKS',
  HAT = 'HAT',
  SCARF = 'SCARF',
  SHAWL = 'SHAWL',
  BLANKET = 'BLANKET',
  BAG = 'BAG',
  AMIGURUMI = 'AMIGURUMI',
  HOMEWARE = 'HOMEWARE',
  ACCESSORY = 'ACCESSORY',
  OTHER = 'OTHER'
}

export const ProjectTypeLabels: Record<ProjectType, string> = {
  [ProjectType.SWEATER]: 'Sweater',
  [ProjectType.CARDIGAN]: 'Cardigan',
  [ProjectType.TOP]: 'Top',
  [ProjectType.SOCKS]: 'Socks',
  [ProjectType.HAT]: 'Hat',
  [ProjectType.SCARF]: 'Scarf',
  [ProjectType.SHAWL]: 'Shawl',
  [ProjectType.BLANKET]: 'Blanket',
  [ProjectType.BAG]: 'Bag',
  [ProjectType.AMIGURUMI]: 'Amigurumi',
  [ProjectType.HOMEWARE]: 'Homeware',
  [ProjectType.ACCESSORY]: 'Accessory',
  [ProjectType.OTHER]: 'Other'
};

export interface Project {
  id: string;
  name: string;
  craft: Craft;
  projectType: ProjectType;
  status: ProjectStatus;
  notes?: string | null;
  createdAt: number;
  updatedAt: number;
}

// EXECUTION ENGINE NODE TYPES
export enum DraftNodeType {
  SECTION = 'SECTION',
  RANGE = 'RANGE',
  REPEAT = 'REPEAT',
  INSTRUCTION = 'INSTRUCTION'
}

export interface DraftNode {
  id: string;
  type: DraftNodeType;
  title?: string | null;
  instructionText?: string | null;
  rangeUnitLabel?: string | null;
  rangeStartInclusive?: number | null;
  rangeEndInclusive?: number | null;
  repeatCount?: number | null;
  repeatLabel?: string | null;
  children?: string[];
}

export interface GuideDraft {
  id: string;
  guideId: string;
  baseRevisionId?: string | null;
  createdAt: number;
  updatedAt: number;
  version: number;
  rootNodeIds: string[];
  nodes: DraftNode[];
}

export interface GuideNodeBase {
  id: string;
}

export interface SectionNode extends GuideNodeBase {
  type: 'SECTION';
  title: string;
  children: string[];
}

export interface RangeNode extends GuideNodeBase {
  type: 'RANGE';
  unitLabel: string;
  startInclusive: number;
  endInclusive: number;
  children: string[];
}

export interface RepeatNode extends GuideNodeBase {
  type: 'REPEAT';
  count: number;
  label?: string | null;
  children: string[];
}

export interface InstructionNode extends GuideNodeBase {
  type: 'INSTRUCTION';
  instructionText: string;
}

export type GuideNode = SectionNode | RangeNode | RepeatNode | InstructionNode;

export interface GuideDefinition {
  guideId: string;
  revisionId: string;
  rootNodeIds: string[];
  nodes: GuideNode[];
}

export interface DefinitionRevision {
  id: string;
  guideId: string;
  revisionNumber: number;
  createdAt: number;
  definition: GuideDefinition;
}

export interface Guide {
  id: string;
  projectId: string;
  name: string;
  notes?: string | null;
  createdAt: number;
  updatedAt: number;
}

// EXECUTION ADDRESS & STATE
export type AncestryFrame = 
  | { type: 'RANGE'; containerNodeId: string; value: number }
  | { type: 'REPEAT'; containerNodeId: string; iteration: number };

export interface ExecutionAddress {
  definitionRevisionId: string;
  instructionNodeId: string;
  ancestryFrames: AncestryFrame[];
}

export enum ExecutionStatus {
  ACTIVE = 'ACTIVE',
  COMPLETED = 'COMPLETED'
}

export interface ExecutionState {
  executionId: string;
  guideId: string;
  definitionRevisionId: string;
  currentAddress: ExecutionAddress | null;
  completedAddresses: ExecutionAddress[];
  status: ExecutionStatus;
  createdAt: number;
  updatedAt: number;
  version: number;
}

export interface ExecutableOccurrence {
  address: ExecutionAddress;
  instruction: InstructionNode;
}

export interface OccurrenceRecord {
  address: ExecutionAddress;
  instruction: InstructionNode;
  nodePath: string[];
}

export enum ContainerProgressStatus {
  NOT_STARTED = 'NOT_STARTED',
  IN_PROGRESS = 'IN_PROGRESS',
  COMPLETE = 'COMPLETE'
}

export interface ContainerProgress {
  status: ContainerProgressStatus;
  completedCount: number;
  totalCount: number;
  currentRangeValue?: number | null;
  currentRepeatIteration?: number | null;
}

export enum GuideEntryAction {
  CONTINUE = 'CONTINUE',
  START = 'START',
  NOT_EXECUTABLE = 'NOT_EXECUTABLE'
}

export interface GuideListEntry {
  guide: Guide;
  action: GuideEntryAction;
  activeExecution?: ExecutionState | null;
  latestRevision?: DefinitionRevision | null;
}

// STASH TYPES
export enum StashCategory {
  YARN = 'YARN',
  NEEDLES_HOOKS = 'NEEDLES_HOOKS',
  NOTIONS = 'NOTIONS',
  MATERIALS = 'MATERIALS'
}

export interface StashItem {
  id: string;
  name: string;
  category: StashCategory;
  brand?: string;
  colorway?: string;
  dyeLot?: string;
  weightCategory?: string; // e.g., Fingering, Worsted, Bulky
  fiberContent?: string; // e.g., 100% Merino Wool
  quantity: number; // skeins, units, pairs
  unitLabel: string; // e.g., "skeins", "pairs", "pcs"
  yardagePerUnit?: number;
  notes?: string;
  createdAt: number;
  updatedAt: number;
}

// LIBRARY TYPES
export interface LibraryItem {
  id: string;
  title: string;
  craft: Craft;
  author?: string;
  sourceUrl?: string;
  tags: string[];
  notes?: string;
  bookmarked: boolean;
  createdAt: number;
  updatedAt: number;
}

// APP TOP-LEVEL DESTINATIONS
export type NavigationDestination = 'HOME' | 'PROJECTS' | 'LIBRARY' | 'STASH' | 'SETTINGS';
