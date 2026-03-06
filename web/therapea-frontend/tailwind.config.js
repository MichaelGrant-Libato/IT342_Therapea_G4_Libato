/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', 'sans-serif'],
      },
      colors: {
        primary: '#8BA888',     
        secondary: '#A78BFA',   
        background: '#F9FAFB',  
        alert: '#F87171',       
        dark: '#333333',        
      },
    },
  },
  plugins: [],
}