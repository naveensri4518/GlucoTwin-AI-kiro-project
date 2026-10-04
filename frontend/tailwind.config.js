/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // GlucoTwin graphite palette
        surface: {
          DEFAULT: '#1a1d23',
          raised: '#21252e',
          border: '#2e3340',
        },
        accent: {
          DEFAULT: '#3b82f6',
          hover: '#2563eb',
        },
        risk: {
          low: '#22c55e',
          moderate: '#f59e0b',
          high: '#f97316',
          critical: '#ef4444',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'monospace'],
      },
    },
  },
  plugins: [],
}
