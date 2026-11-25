import { render, screen } from '@testing-library/react-native';
import { Text } from '../text';
import { ListSection } from '../list-section';

describe('list section', () => {
  it('shows the header as a heading and the footer as text', async () => {
    await render(
      <ListSection title="Community" footer="Hold a row to delete it">
        <Text>Ana</Text>
      </ListSection>,
    );

    expect(screen.getByRole('heading', { name: 'Community' })).toBeOnTheScreen();
    expect(screen.getByText('Hold a row to delete it')).toBeOnTheScreen();
    expect(screen.getByText('Ana')).toBeOnTheScreen();
  });

  it('renders without header or footer', async () => {
    await render(
      <ListSection>
        <Text>Ana</Text>
      </ListSection>,
    );

    expect(screen.queryByRole('heading')).not.toBeOnTheScreen();
    expect(screen.getByText('Ana')).toBeOnTheScreen();
  });

  it('groups its rows in a rounded card with a hairline border', async () => {
    await render(
      <ListSection>
        <Text>Ana</Text>
      </ListSection>,
    );

    const classes = String(screen.getByTestId('list-section-card').props.className).split(/\s+/);
    expect(classes).toEqual(expect.arrayContaining(['bg-card', 'rounded-lg', 'border-hairline', 'overflow-hidden']));
  });

  it('separates consecutive rows with a hairline but not the first', async () => {
    await render(
      <ListSection>
        <Text>Ana</Text>
        <Text>Luis</Text>
      </ListSection>,
    );

    const cells = screen.getAllByTestId('list-section-cell');
    expect(String(cells[0].props.className)).not.toContain('border-t-hairline');
    expect(String(cells[1].props.className)).toContain('border-t-hairline');
  });

  it('ignores empty children', async () => {
    await render(
      <ListSection>
        {null}
        <Text>Ana</Text>
        {false}
      </ListSection>,
    );

    expect(screen.getAllByTestId('list-section-cell')).toHaveLength(1);
  });
});
