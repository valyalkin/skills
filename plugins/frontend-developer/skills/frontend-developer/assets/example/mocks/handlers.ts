import { http, HttpResponse } from 'msw';

export const handlers = [
  http.get('http://api.test/invoices', () =>
    HttpResponse.json([
      {
        id: '1',
        name: 'Acme',
        email: 'a@acme.test',
        amount: 2500,
        date: '2026-01-01',
        status: 'paid',
      },
    ]),
  ),
  http.get('http://api.test/invoices/count', () =>
    HttpResponse.json({ total: 13 }),
  ),
  http.get('http://api.test/invoices/1', () =>
    HttpResponse.json({
      id: '1',
      customer_id: 'c1',
      amount: 2500,
      status: 'paid',
    }),
  ),
  http.post(
    'http://api.test/invoices',
    () => new HttpResponse(null, { status: 204 }),
  ),
];
