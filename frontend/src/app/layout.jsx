import Providers from '@/shared/layout/providers';
import Header from '@/shared/layout/header/header';
import Footer from '@/shared/layout/footer/footer';
import './globals.css';

export const metadata = {
  title: 'Reserva - бронирование столиков, мест и кортов',
  description: 'Платформа бронирования для ресторанов, коворкингов и спортивных заведений',
};

export default function RootLayout({ children }) {
  return (
    <html lang="ru">
      <body>
        <Providers>
          <Header />
          <main className="container page">{children}</main>
          <Footer />
        </Providers>
      </body>
    </html>
  );
}
