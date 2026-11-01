/**
 * The kinds of place a stop-over can be picked by, in the order the dialog shows them: what a
 * driver needs first, then where they might like to go. The names are the API's.
 */
export type PlaceKindName =
  | 'fuel'
  | 'food'
  | 'cafe'
  | 'toilets'
  | 'dairy'
  | 'groceries'
  | 'beach'
  | 'park'
  | 'lookout'
  | 'tourist'
  | 'zoo'
  | 'mall'

export interface KindOfPlace {
  name: PlaceKindName
  label: string
}

export const KINDS_OF_PLACE: KindOfPlace[] = [
  { name: 'fuel', label: 'Petrol' },
  { name: 'food', label: 'Food' },
  { name: 'cafe', label: 'Cafe' },
  { name: 'toilets', label: 'Toilets' },
  { name: 'dairy', label: 'Dairy' },
  { name: 'groceries', label: 'Groceries' },
  { name: 'beach', label: 'Beach' },
  { name: 'park', label: 'Park' },
  { name: 'lookout', label: 'Lookout' },
  { name: 'tourist', label: 'Tourist spot' },
  { name: 'zoo', label: 'Zoo' },
  { name: 'mall', label: 'Mall' },
]
