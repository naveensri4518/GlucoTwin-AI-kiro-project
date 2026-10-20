/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // Vercel-inspired monochrome & precision clinical palette
        bg: {
          DEFAULT: '#000000',
          sidebar: '#000000',
          card: '#0A0A0A',
          elevated: '#111111',
          subtle: '#050505',
        },
        card: {
          DEFAULT: '#0A0A0A',
          elevated: '#111111',
          subtle: '#050505',
        },
        elevated: '#111111',
        subtle: '#050505',
        sidebar: '#000000',

        // Silver & neutral borders
        border: {
          DEFAULT: '#222222',
          border: '#222222',
          subtle: '#171717',
          hover: '#333333',
        },

        // Clean restrained accent (Vercel electric blue)
        accent: {
          DEFAULT: '#0070F3',
          hover: '#0060DF',
          muted: 'rgba(0, 112, 243, 0.1)',
          border: 'rgba(0, 112, 243, 0.3)',
        },

        // Precision semantic status colors
        status: {
          success: '#10B981', // Clean emerald
          warning: '#F59E0B', // Amber
          danger: '#EF4444',  // Crimson
          info: '#3B82F6',    // Blue
        },

        // High contrast readable text tokens
        text: {
          primary: '#EDEDED',
          secondary: '#888888',
          muted: '#555555',
        },

        // Legacy compatibility mappings
        surface: {
          DEFAULT: '#000000',
          raised: '#0A0A0A',
          card: '#0A0A0A',
          elevated: '#111111',
          border: '#222222',
        },
        risk: {
          low: '#10B981',
          moderate: '#F59E0B',
          high: '#EF4444',
          critical: '#DC2626',
        },
      },
      fontFamily: {
        sans: [
          'Inter',
          '-apple-system',
          'BlinkMacSystemFont',
          'Segoe UI',
          'Roboto',
          'sans-serif',
        ],
        mono: [
          'Geist Mono',
          'JetBrains Mono',
          'ui-monospace',
          'SFMono-Regular',
          'Menlo',
          'Monaco',
          'Consolas',
          'monospace',
        ],
      },
      boxShadow: {
        'panel': '0 0 0 1px #222222, 0 4px 14px 0 rgba(0, 0, 0, 0.4)',
        'elevated': '0 0 0 1px #2E2E2E, 0 8px 30px rgba(0, 0, 0, 0.7)',
        'glow-accent': '0 0 20px -5px rgba(0, 112, 243, 0.25)',
      },
    },
  },
  plugins: [],
}
