import { createContext, useContext, useState, useEffect, type ReactNode } from 'react';

interface BusinessState {
  businessId: string | null;
  businessName: string | null;
}

interface BusinessCtx extends BusinessState {
  setBusiness: (id: string, name: string) => void;
  clearBusiness: () => void;
}

const KEY = 'marketing_business';

const BusinessContext = createContext<BusinessCtx | null>(null);

function load(): BusinessState {
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) return JSON.parse(raw) as BusinessState;
  } catch { /* ignore */ }
  return { businessId: null, businessName: null };
}

export function BusinessProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<BusinessState>(load);

  useEffect(() => {
    localStorage.setItem(KEY, JSON.stringify(state));
  }, [state]);

  const setBusiness = (id: string, name: string) =>
    setState({ businessId: id, businessName: name });

  const clearBusiness = () =>
    setState({ businessId: null, businessName: null });

  return (
    <BusinessContext.Provider value={{ ...state, setBusiness, clearBusiness }}>
      {children}
    </BusinessContext.Provider>
  );
}

export function useBusiness(): BusinessCtx {
  const ctx = useContext(BusinessContext);
  if (!ctx) throw new Error('useBusiness must be inside BusinessProvider');
  return ctx;
}
