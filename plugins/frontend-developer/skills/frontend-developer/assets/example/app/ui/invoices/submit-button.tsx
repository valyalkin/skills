'use client';

import { useFormStatus } from 'react-dom';
import { Button } from '@/app/ui/button';

export default function SubmitButton({ children }: { children: string }) {
  const { pending } = useFormStatus();

  return (
    <Button type="submit" disabled={pending} aria-disabled={pending}>
      {pending ? 'Saving…' : children}
    </Button>
  );
}
