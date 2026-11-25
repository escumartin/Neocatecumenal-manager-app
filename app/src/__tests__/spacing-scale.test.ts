const fs = jest.requireActual('fs');
const path = jest.requireActual('path');
const tailwindConfig = jest.requireActual('../../tailwind.config.js');
const scale = Object.keys(tailwindConfig.theme.extend.spacing);
const sourceRoot: string = path.resolve('src');

function sources(directory: string): string[] {
  return fs.readdirSync(directory).flatMap((entry: string) => {
    const file: string = path.join(directory, entry);
    if (fs.statSync(file).isDirectory()) {
      return entry === '__tests__' || entry === 'support' ? [] : sources(file);
    }
    return file.endsWith('.tsx') ? [file] : [];
  });
}

describe('spacing scale', () => {
  it('uses only gap steps that exist in the design scale', () => {
    const offenders = sources(sourceRoot).flatMap((file) =>
      [...fs.readFileSync(file, 'utf8').matchAll(/\bgap-(\d+)\b/g)]
        .filter((match) => !scale.includes(match[1]))
        .map((match) => `${file}: ${match[0]}`),
    );

    expect(offenders).toEqual([]);
  });
});
