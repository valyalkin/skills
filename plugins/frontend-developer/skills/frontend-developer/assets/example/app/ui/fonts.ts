import { Inter, Lusitana } from 'next/font/google';

/** Body font, applied once on <body> in app/layout.tsx. */
export const inter = Inter({ subsets: ['latin'] });

/** Display font for headings. */
export const lusitana = Lusitana({
  weight: ['400', '700'],
  subsets: ['latin'],
});
