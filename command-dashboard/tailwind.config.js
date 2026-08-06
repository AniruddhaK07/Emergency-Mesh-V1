/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        critical: "#ef4444", // Red-500 for single accent color
      }
    },
  },
  plugins: [],
}
