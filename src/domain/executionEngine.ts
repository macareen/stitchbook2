import {
  ExecutionAddress,
  ExecutionState,
  ExecutionStatus,
  GuideDefinition,
  GuideNode,
  InstructionNode,
  RangeNode,
  RepeatNode,
  SectionNode,
  ExecutableOccurrence,
  OccurrenceRecord,
  ContainerProgress,
  ContainerProgressStatus,
  AncestryFrame
} from '../types';

export function serializeAddress(address: ExecutionAddress): string {
  const framesStr = address.ancestryFrames
    .map(f => `${f.containerNodeId}:${f.type === 'RANGE' ? 'R=' + f.value : 'P=' + f.iteration}`)
    .join('|');
  return `${address.definitionRevisionId}::${address.instructionNodeId}::[${framesStr}]`;
}

export function areAddressesEqual(a: ExecutionAddress | null, b: ExecutionAddress | null): boolean {
  if (a === b) return true;
  if (!a || !b) return false;
  if (a.definitionRevisionId !== b.definitionRevisionId) return false;
  if (a.instructionNodeId !== b.instructionNodeId) return false;
  if (a.ancestryFrames.length !== b.ancestryFrames.length) return false;

  for (let i = 0; i < a.ancestryFrames.length; i++) {
    const fA = a.ancestryFrames[i];
    const fB = b.ancestryFrames[i];
    if (fA.type !== fB.type) return false;
    if (fA.containerNodeId !== fB.containerNodeId) return false;
    if (fA.type === 'RANGE' && fB.type === 'RANGE') {
      if (fA.value !== fB.value) return false;
    } else if (fA.type === 'REPEAT' && fB.type === 'REPEAT') {
      if (fA.iteration !== fB.iteration) return false;
    }
  }
  return true;
}

export class ValidatedGuideDefinition {
  public readonly nodesById: Map<string, GuideNode>;

  constructor(
    public readonly definition: GuideDefinition,
    nodesByIdMap: Map<string, GuideNode>
  ) {
    this.nodesById = nodesByIdMap;
  }

  public node(nodeId: string): GuideNode | undefined {
    return this.nodesById.get(nodeId);
  }
}

export class GuideDefinitionValidator {
  public static validate(definition: GuideDefinition): ValidatedGuideDefinition {
    const errors: string[] = [];
    const nodesById = new Map<string, GuideNode>();

    if (!definition.nodes || definition.nodes.length === 0) {
      errors.push("Empty definition");
    }

    for (const node of definition.nodes) {
      if (nodesById.has(node.id)) {
        errors.push(`Duplicate node ID: ${node.id}`);
      } else {
        nodesById.set(node.id, node);
      }
    }

    if (!definition.rootNodeIds || definition.rootNodeIds.length === 0) {
      errors.push("No root node IDs");
    }

    for (const rootId of definition.rootNodeIds) {
      if (!nodesById.has(rootId)) {
        errors.push(`Root node missing: ${rootId}`);
      }
    }

    // Validate container children and bounds
    for (const node of definition.nodes) {
      if (node.type === 'SECTION' || node.type === 'RANGE' || node.type === 'REPEAT') {
        if (!node.children || node.children.length === 0) {
          errors.push(`Container node ${node.id} has no children`);
        } else {
          for (const childId of node.children) {
            if (!nodesById.has(childId)) {
              errors.push(`Child node ${childId} referenced by ${node.id} does not exist`);
            }
          }
        }
      }

      if (node.type === 'RANGE') {
        if (node.startInclusive > node.endInclusive) {
          errors.push(`Invalid range bounds for ${node.id}: ${node.startInclusive} > ${node.endInclusive}`);
        }
        if (!node.unitLabel || node.unitLabel.trim() === '') {
          errors.push(`Blank range unit label for ${node.id}`);
        }
      }

      if (node.type === 'REPEAT') {
        if (node.count <= 0) {
          errors.push(`Non-positive repeat count for ${node.id}: ${node.count}`);
        }
      }
    }

    // Check for cycles
    const visiting = new Set<string>();
    const visited = new Set<string>();

    function visit(nodeId: string) {
      if (visiting.has(nodeId)) {
        errors.push(`Cycle detected at node ${nodeId}`);
        return;
      }
      if (visited.has(nodeId)) return;

      const node = nodesById.get(nodeId);
      if (!node) return;

      visiting.add(nodeId);
      if (node.type === 'SECTION' || node.type === 'RANGE' || node.type === 'REPEAT') {
        node.children.forEach(visit);
      }
      visiting.delete(nodeId);
      visited.add(nodeId);
    }

    definition.rootNodeIds.forEach(visit);

    if (errors.length > 0) {
      throw new Error(`Invalid Guide Definition: ${errors.join('; ')}`);
    }

    return new ValidatedGuideDefinition(definition, nodesById);
  }
}

export class GuideTraversal {
  constructor(private readonly guide: ValidatedGuideDefinition) {}

  public getOccurrenceRecords(): OccurrenceRecord[] {
    const records: OccurrenceRecord[] = [];

    const walk = (nodeId: string, frames: AncestryFrame[], nodePath: string[]) => {
      const node = this.guide.node(nodeId);
      if (!node) return;

      const currentPath = [...nodePath, node.id];

      switch (node.type) {
        case 'SECTION': {
          for (const childId of node.children) {
            walk(childId, frames, currentPath);
          }
          break;
        }
        case 'RANGE': {
          for (let val = node.startInclusive; val <= node.endInclusive; val++) {
            const rangeFrames: AncestryFrame[] = [
              ...frames,
              { type: 'RANGE', containerNodeId: node.id, value: val }
            ];
            for (const childId of node.children) {
              walk(childId, rangeFrames, currentPath);
            }
          }
          break;
        }
        case 'REPEAT': {
          for (let iter = 1; iter <= node.count; iter++) {
            const repeatFrames: AncestryFrame[] = [
              ...frames,
              { type: 'REPEAT', containerNodeId: node.id, iteration: iter }
            ];
            for (const childId of node.children) {
              walk(childId, repeatFrames, currentPath);
            }
          }
          break;
        }
        case 'INSTRUCTION': {
          records.push({
            address: {
              definitionRevisionId: this.guide.definition.revisionId,
              instructionNodeId: node.id,
              ancestryFrames: frames
            },
            instruction: node,
            nodePath: currentPath
          });
          break;
        }
      }
    };

    for (const rootId of this.guide.definition.rootNodeIds) {
      walk(rootId, [], []);
    }

    return records;
  }

  public occurrences(): ExecutableOccurrence[] {
    return this.getOccurrenceRecords().map(r => ({
      address: r.address,
      instruction: r.instruction
    }));
  }

  public first(): ExecutableOccurrence {
    const occs = this.occurrences();
    if (occs.length === 0) {
      throw new Error('Guide has no executable occurrences');
    }
    return occs[0];
  }

  public last(): ExecutableOccurrence {
    const occs = this.occurrences();
    if (occs.length === 0) {
      throw new Error('Guide has no executable occurrences');
    }
    return occs[occs.length - 1];
  }

  public previous(address: ExecutionAddress): ExecutableOccurrence | null {
    const occs = this.occurrences();
    let prev: ExecutableOccurrence | null = null;
    for (const occ of occs) {
      if (areAddressesEqual(occ.address, address)) {
        return prev;
      }
      prev = occ;
    }
    return null;
  }

  public next(address: ExecutionAddress): ExecutableOccurrence | null {
    const occs = this.occurrences();
    let found = false;
    for (const occ of occs) {
      if (found) return occ;
      if (areAddressesEqual(occ.address, address)) {
        found = true;
      }
    }
    return null;
  }

  public resolve(address: ExecutionAddress): ExecutableOccurrence {
    const match = this.occurrences().find(occ => areAddressesEqual(occ.address, address));
    if (!match) {
      throw new Error(`Unresolved address: ${serializeAddress(address)}`);
    }
    return match;
  }

  public ancestryNodePath(address: ExecutionAddress): string[] {
    const match = this.getOccurrenceRecords().find(r => areAddressesEqual(r.address, address));
    if (!match) {
      throw new Error(`Unresolved address for node path: ${serializeAddress(address)}`);
    }
    return match.nodePath;
  }
}

export enum NoChangeReason {
  ALREADY_COMPLETE = 'ALREADY_COMPLETE',
  ALREADY_AT_FIRST_OCCURRENCE = 'ALREADY_AT_FIRST_OCCURRENCE',
  ALREADY_AT_TARGET = 'ALREADY_AT_TARGET'
}

export type ExecutionTransitionResult = 
  | { type: 'CHANGED'; state: ExecutionState }
  | { type: 'NO_CHANGE'; state: ExecutionState; reason: NoChangeReason };

export class ExecutionEngine {
  private traversal: GuideTraversal;

  private constructor(private readonly guide: ValidatedGuideDefinition) {
    this.traversal = new GuideTraversal(guide);
  }

  public static forDefinition(definition: GuideDefinition): ExecutionEngine {
    const validated = GuideDefinitionValidator.validate(definition);
    return new ExecutionEngine(validated);
  }

  public newExecution(executionId: string): ExecutionState {
    const firstOcc = this.traversal.first();
    const now = Date.now();
    return {
      executionId,
      guideId: this.guide.definition.guideId,
      definitionRevisionId: this.guide.definition.revisionId,
      currentAddress: firstOcc.address,
      completedAddresses: [],
      status: ExecutionStatus.ACTIVE,
      createdAt: now,
      updatedAt: now,
      version: 1
    };
  }

  public complete(state: ExecutionState): ExecutionTransitionResult {
    if (state.status === ExecutionStatus.COMPLETED) {
      return { type: 'NO_CHANGE', state, reason: NoChangeReason.ALREADY_COMPLETE };
    }

    if (!state.currentAddress) {
      return { type: 'NO_CHANGE', state, reason: NoChangeReason.ALREADY_COMPLETE };
    }

    const currentAddr = state.currentAddress;
    const completedSet = [...state.completedAddresses];
    if (!completedSet.some(a => areAddressesEqual(a, currentAddr))) {
      completedSet.push(currentAddr);
    }

    const nextAddr = this.nextIncompleteAddress(currentAddr, completedSet);
    const updatedState: ExecutionState = {
      ...state,
      currentAddress: nextAddr,
      completedAddresses: completedSet,
      status: nextAddr === null ? ExecutionStatus.COMPLETED : ExecutionStatus.ACTIVE,
      updatedAt: Date.now(),
      version: state.version + 1
    };

    return { type: 'CHANGED', state: updatedState };
  }

  public previous(state: ExecutionState): ExecutionTransitionResult {
    let prevAddr: ExecutionAddress | null = null;
    if (state.status === ExecutionStatus.ACTIVE && state.currentAddress) {
      const prevOcc = this.traversal.previous(state.currentAddress);
      prevAddr = prevOcc ? prevOcc.address : null;
    } else if (state.status === ExecutionStatus.COMPLETED) {
      prevAddr = this.traversal.last().address;
    }

    if (!prevAddr) {
      return {
        type: 'NO_CHANGE',
        state,
        reason: NoChangeReason.ALREADY_AT_FIRST_OCCURRENCE
      };
    }

    // Uncomplete the previous address if it was marked completed
    const updatedCompleted = state.completedAddresses.filter(
      a => !areAddressesEqual(a, prevAddr!)
    );

    const updatedState: ExecutionState = {
      ...state,
      currentAddress: prevAddr,
      completedAddresses: updatedCompleted,
      status: ExecutionStatus.ACTIVE,
      updatedAt: Date.now(),
      version: state.version + 1
    };

    return { type: 'CHANGED', state: updatedState };
  }

  public jump(state: ExecutionState, targetAddress: ExecutionAddress): ExecutionTransitionResult {
    this.traversal.resolve(targetAddress);

    if (state.status === ExecutionStatus.ACTIVE && areAddressesEqual(state.currentAddress, targetAddress)) {
      return { type: 'NO_CHANGE', state, reason: NoChangeReason.ALREADY_AT_TARGET };
    }

    const updatedState: ExecutionState = {
      ...state,
      currentAddress: targetAddress,
      status: ExecutionStatus.ACTIVE,
      updatedAt: Date.now(),
      version: state.version + 1
    };

    return { type: 'CHANGED', state: updatedState };
  }

  private nextIncompleteAddress(
    currentAddress: ExecutionAddress,
    completedAddresses: ExecutionAddress[]
  ): ExecutionAddress | null {
    let isAfterCurrent = false;
    let earliestIncomplete: ExecutionAddress | null = null;

    const occs = this.traversal.getOccurrenceRecords();
    for (const occ of occs) {
      const isCompleted = completedAddresses.some(a => areAddressesEqual(a, occ.address));

      if (areAddressesEqual(occ.address, currentAddress)) {
        isAfterCurrent = true;
      } else if (!isCompleted) {
        if (!earliestIncomplete) {
          earliestIncomplete = occ.address;
        }
        if (isAfterCurrent) {
          return occ.address;
        }
      }
    }

    return earliestIncomplete;
  }
}

export class DerivedProgressCalculator {
  private traversal: GuideTraversal;

  constructor(private readonly guide: ValidatedGuideDefinition) {
    this.traversal = new GuideTraversal(guide);
  }

  public progressFor(
    containerNodeId: string,
    completedAddresses: ExecutionAddress[],
    currentAddress: ExecutionAddress | null = null
  ): ContainerProgress {
    const node = this.guide.node(containerNodeId);
    if (!node || (node.type !== 'SECTION' && node.type !== 'RANGE' && node.type !== 'REPEAT')) {
      throw new Error(`Node ${containerNodeId} is not a valid container`);
    }

    const records = this.traversal.getOccurrenceRecords().filter(r => r.nodePath.includes(containerNodeId));
    const totalCount = records.length;
    let completedCount = 0;

    for (const record of records) {
      if (completedAddresses.some(a => areAddressesEqual(a, record.address))) {
        completedCount++;
      }
    }

    let status = ContainerProgressStatus.NOT_STARTED;
    if (completedCount === totalCount && totalCount > 0) {
      status = ContainerProgressStatus.COMPLETE;
    } else if (completedCount > 0) {
      status = ContainerProgressStatus.IN_PROGRESS;
    }

    let currentRangeValue: number | null = null;
    let currentRepeatIteration: number | null = null;

    if (currentAddress) {
      const frame = currentAddress.ancestryFrames.find(f => f.containerNodeId === containerNodeId);
      if (frame) {
        if (frame.type === 'RANGE') currentRangeValue = frame.value;
        if (frame.type === 'REPEAT') currentRepeatIteration = frame.iteration;
      }
    }

    return {
      status,
      completedCount,
      totalCount,
      currentRangeValue,
      currentRepeatIteration
    };
  }
}
