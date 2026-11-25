const { hairlineWidth } = require('nativewind/theme');

module.exports = {
  content: ['./src/**/*.{ts,tsx}'],
  presets: [require('nativewind/preset')],
  theme: {
    extend: {
      colors: {
        background: 'var(--background)',
        foreground: 'var(--foreground)',
        input: 'var(--input)',
        ring: 'var(--ring)',
        success: 'var(--success)',
        warning: 'var(--warning)',
        card: { DEFAULT: 'var(--card)', foreground: 'var(--card-foreground)' },
        primary: { DEFAULT: 'var(--primary)', foreground: 'var(--primary-foreground)' },
        secondary: { DEFAULT: 'var(--secondary)', foreground: 'var(--secondary-foreground)' },
        accent: { DEFAULT: 'var(--accent)', foreground: 'var(--accent-foreground)' },
        destructive: { DEFAULT: 'var(--destructive)', foreground: 'var(--destructive-foreground)' },
        muted: { DEFAULT: 'var(--muted)', foreground: 'var(--muted-foreground)' },
        border: 'var(--border)',
      },
      fontFamily: {
        sans: ['Inter_400Regular', 'System'],
        'sans-medium': ['Inter_500Medium', 'System'],
        'sans-semibold': ['Inter_600SemiBold', 'System'],
        serif: ['SourceSerif4_600SemiBold', 'serif'],
        'serif-regular': ['SourceSerif4_400Regular', 'serif'],
      },
      fontSize: {
        display: ['32px', { lineHeight: '40px' }],
        title: ['24px', { lineHeight: '32px' }],
        heading: ['20px', { lineHeight: '28px' }],
        body: ['18px', { lineHeight: '28px' }],
        small: ['16px', { lineHeight: '24px' }],
        caption: ['14px', { lineHeight: '20px' }],
      },
      spacing: {
        1: '4px',
        2: '8px',
        3: '12px',
        4: '16px',
        6: '24px',
        8: '32px',
        12: '48px',
        13: '52px',
      },
      borderRadius: {
        sm: '6px',
        md: '10px',
        lg: '14px',
        xl: '20px',
      },
      borderWidth: {
        hairline: hairlineWidth(),
        field: '1.5px',
      },
    },
  },
  plugins: [],
};
