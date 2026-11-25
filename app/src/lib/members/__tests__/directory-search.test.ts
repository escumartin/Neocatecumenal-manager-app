import { filterMembers, normalizeForSearch } from '../directory-search';
import { ana, linked, luis, marta } from '@/screens/members/support/test-support';

const everyone = [luis, ana, marta];

describe('normalizeForSearch', () => {
  it('lowercases, strips accents and trims', () => {
    expect(normalizeForSearch('  ÁLVAREZ Peña ')).toBe('alvarez pena');
  });

  it('keeps the letter ñ distinct from n only through its accent mark', () => {
    expect(normalizeForSearch('Muñoz')).toBe('munoz');
  });
});

describe('filterMembers', () => {
  it('returns everyone for an empty or blank query', () => {
    expect(filterMembers(everyone, '')).toEqual(everyone);
    expect(filterMembers(everyone, '   ')).toEqual(everyone);
  });

  it('matches without caring about accents or capitals', () => {
    expect(filterMembers(everyone, 'alvarez')).toEqual([luis]);
    expect(filterMembers(everyone, 'ÁLVAREZ')).toEqual([luis]);
    expect(filterMembers(everyone, 'PEREZ')).toEqual([ana]);
  });

  it('matches a part of the given name or of the surnames', () => {
    expect(filterMembers(everyone, 'mar')).toEqual([marta]);
    expect(filterMembers(everyone, 'erez')).toEqual([ana]);
  });

  it('requires every word of the query, in any order', () => {
    expect(filterMembers(everyone, 'perez ana')).toEqual([ana]);
    expect(filterMembers(everyone, 'ana alvarez')).toEqual([]);
  });

  it('returns nothing when no member matches', () => {
    expect(filterMembers(everyone, 'zzz')).toEqual([]);
  });

  it('keeps the order it received', () => {
    expect(filterMembers([marta, linked, ana], 'a').map((member) => member.id)).toEqual([marta.id, linked.id, ana.id]);
  });

  it('does not search the contact data', () => {
    expect(filterMembers(everyone, '600000001')).toEqual([]);
    expect(filterMembers(everyone, 'marta@example.org')).toEqual([]);
  });
});
