import { controllerName, namesKey, parseNames } from '../names';

describe('controller names', () => {
  it('keys by the sorted set of controllers', () => {
    expect(namesKey([17, 81])).toBe('ui.names.17-81');
    expect(namesKey([81, 17])).toBe('ui.names.17-81');
  });

  it('parses stored names and drops junk', () => {
    expect(parseNames('{"17":"Front","81":" Rear ","x":"y","5":3}')).toEqual({ 17: 'Front', 81: 'Rear' });
    expect(parseNames('not json')).toEqual({});
    expect(parseNames(null)).toEqual({});
  });

  it('falls back to the ID label', () => {
    expect(controllerName(17, false, { 81: 'Rear' })).toBe('ID 17');
    expect(controllerName(81, true, { 81: 'Rear' })).toBe('Rear');
  });
});
