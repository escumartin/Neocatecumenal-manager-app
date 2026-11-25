const tailwindConfig = jest.requireActual('../../../../tailwind.config.js');

type Styled = { props: { className?: unknown } };

function pixels(value: string): number {
  return Number.parseInt(value, 10);
}

function classesOf(element: Styled): string[] {
  return String(element.props.className ?? '').split(/\s+/);
}

export function fontSizeOf(element: Styled): number {
  const token = classesOf(element)
    .map((name) => name.replace(/^text-/, ''))
    .filter((name) => name in tailwindConfig.theme.extend.fontSize)
    .pop();
  return token ? pixels(tailwindConfig.theme.extend.fontSize[token][0]) : 0;
}

export function minHeightOf(element: Styled): number {
  const step = classesOf(element)
    .find((name) => name.startsWith('min-h-'))
    ?.replace('min-h-', '');
  return step ? pixels(tailwindConfig.theme.extend.spacing[step]) : 0;
}
