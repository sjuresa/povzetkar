import type { ContentSourceCreate, ContentSourceDTO } from '../../api/generated/model';

/**
 * A source chosen in the topic form: an existing source (has an id) or a Quick Add entry (a draft that the
 * backend finds or creates when the topic is saved — POV-219). The key identifies it in the list.
 */
export interface SelectedSource {
  key: string;
  name: string;
  platform: string;
  /** The platform identifier — for Quick Add the channel ID from the check — to spot the same channel twice. */
  identifier: string;
  id?: number;
  draft?: ContentSourceCreate;
}

export function fromExistingSource(source: ContentSourceDTO): SelectedSource {
  return {
    key: `id:${source.id}`,
    name: source.name || source.sourceIdentifier || '',
    platform: source.platform ?? '',
    identifier: source.sourceIdentifier ?? '',
    id: source.id,
  };
}

export function fromDraft(draft: ContentSourceCreate): SelectedSource {
  const identifier = draft.sourceIdentifier.trim();
  const normalized: ContentSourceCreate = { ...draft, sourceIdentifier: identifier };
  return {
    key: `new:${draft.platform}:${identifier}`,
    name: draft.name || identifier,
    platform: draft.platform,
    identifier,
    draft: normalized,
  };
}

/** Whether the selection already holds this source — by key, or the same channel entered again via Quick Add. */
export function isAlreadySelected(selected: SelectedSource[], candidate: SelectedSource): boolean {
  return selected.some((s) =>
    s.key === candidate.key || (s.identifier !== '' && s.platform === candidate.platform && s.identifier === candidate.identifier));
}

/** Splits the selection into the payload fields of TopicCreate / TopicPatch. */
export function toTopicSources(selected: SelectedSource[]): { sourceIds: number[]; newSources: ContentSourceCreate[] } {
  return {
    sourceIds: selected.flatMap((s) => (s.id !== undefined ? [s.id] : [])),
    newSources: selected.flatMap((s) => (s.draft ? [s.draft] : [])),
  };
}
