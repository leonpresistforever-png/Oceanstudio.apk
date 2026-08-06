/**
 * Ocean.studio Default UI Scaffold
 * Designer sub-agents: extend this file — do not replace token system.
 * @see agent/design-skill.md
 */
import { useState } from 'react';
import { Sparkles, ArrowRight, Check, Search } from 'lucide-react';

/* ─── Design tokens (mirror Ocean.studio CSS variables) ─── */
const tokens = {
  bgPrimary: 'var(--bg-primary, #fafaf9)',
  bgSecondary: 'var(--bg-secondary, #f5f5f4)',
  bgTertiary: 'var(--bg-tertiary, #ffffff)',
  textPrimary: 'var(--text-primary, #1c1917)',
  textSecondary: 'var(--text-secondary, #57534e)',
  textTertiary: 'var(--text-tertiary, #a8a29e)',
  border: 'var(--border, #e7e5e4)',
  borderSubtle: 'var(--border-subtle, #f5f5f4)',
  accent: 'var(--accent, #0ea5e9)',
  accentHover: 'var(--accent-hover, #0284c7)',
  radiusSm: 'var(--radius-sm, 6px)',
  radiusMd: 'var(--radius-md, 10px)',
  shadow: 'var(--shadow-card, 0 1px 3px rgba(28, 25, 23, 0.08))',
  transition: 'var(--transition, 150ms ease)',
};

/* ─── Primitives ─── */

type ButtonVariant = 'primary' | 'ghost' | 'danger';

function Button({
  children,
  variant = 'primary',
  onClick,
  disabled,
  icon,
}: {
  children: React.ReactNode;
  variant?: ButtonVariant;
  onClick?: () => void;
  disabled?: boolean;
  icon?: React.ReactNode;
}) {
  const styles: Record<ButtonVariant, React.CSSProperties> = {
    primary: {
      background: tokens.accent,
      color: '#fff',
      border: 'none',
    },
    ghost: {
      background: 'transparent',
      color: tokens.textPrimary,
      border: `1px solid ${tokens.border}`,
    },
    danger: {
      background: '#fef2f2',
      color: '#dc2626',
      border: '1px solid #fecaca',
    },
  };

  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: '8px',
        padding: '10px 18px',
        fontSize: '0.875rem',
        fontWeight: 500,
        borderRadius: tokens.radiusSm,
        cursor: disabled ? 'not-allowed' : 'pointer',
        opacity: disabled ? 0.6 : 1,
        transition: tokens.transition,
        ...styles[variant],
      }}
    >
      {icon}
      {children}
    </button>
  );
}

function Card({
  children,
  title,
  subtitle,
}: {
  children: React.ReactNode;
  title?: string;
  subtitle?: string;
}) {
  return (
    <section
      style={{
        background: tokens.bgTertiary,
        border: `1px solid ${tokens.borderSubtle}`,
        borderRadius: tokens.radiusMd,
        boxShadow: tokens.shadow,
        padding: '24px',
      }}
    >
      {(title || subtitle) && (
        <header style={{ marginBottom: '16px' }}>
          {title && (
            <h2 style={{ margin: 0, fontSize: '1.125rem', fontWeight: 600, color: tokens.textPrimary }}>
              {title}
            </h2>
          )}
          {subtitle && (
            <p style={{ margin: '4px 0 0', fontSize: '0.875rem', color: tokens.textSecondary }}>
              {subtitle}
            </p>
          )}
        </header>
      )}
      {children}
    </section>
  );
}

function Badge({ children, tone = 'default' }: { children: React.ReactNode; tone?: 'default' | 'success' }) {
  const bg = tone === 'success' ? '#ecfdf5' : tokens.bgSecondary;
  const color = tone === 'success' ? '#059669' : tokens.textSecondary;
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: '4px',
        padding: '4px 10px',
        fontSize: '0.75rem',
        fontWeight: 500,
        borderRadius: '999px',
        background: bg,
        color,
      }}
    >
      {children}
    </span>
  );
}

function Input({
  placeholder,
  value,
  onChange,
  icon,
}: {
  placeholder?: string;
  value: string;
  onChange: (v: string) => void;
  icon?: React.ReactNode;
}) {
  return (
    <div style={{ position: 'relative', width: '100%' }}>
      {icon && (
        <span style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', color: tokens.textTertiary }}>
          {icon}
        </span>
      )}
      <input
        type="text"
        placeholder={placeholder}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        style={{
          width: '100%',
          padding: icon ? '10px 12px 10px 40px' : '10px 12px',
          fontSize: '0.875rem',
          border: `1px solid ${tokens.border}`,
          borderRadius: tokens.radiusSm,
          background: tokens.bgPrimary,
          color: tokens.textPrimary,
          outline: 'none',
        }}
      />
    </div>
  );
}

/* ─── Example screen — replace content, keep structure ─── */

export default function DefaultUIScaffold() {
  const [query, setQuery] = useState('');

  const features = [
    { title: 'Clean hierarchy', desc: 'One primary action, clear sections' },
    { title: 'Token-driven', desc: 'CSS variables for theme consistency' },
    { title: 'Accessible', desc: 'Semantic HTML and focus-friendly controls' },
  ];

  return (
    <div
      style={{
        minHeight: '100vh',
        background: tokens.bgPrimary,
        color: tokens.textPrimary,
        fontFamily: 'system-ui, -apple-system, sans-serif',
      }}
    >
      {/* Hero */}
      <header
        style={{
          padding: '48px 24px',
          maxWidth: '960px',
          margin: '0 auto',
          textAlign: 'center',
        }}
      >
        <Badge tone="success">
          <Sparkles size={12} /> Designer scaffold
        </Badge>
        <h1
          style={{
            margin: '16px 0 8px',
            fontSize: 'clamp(1.75rem, 4vw, 2.5rem)',
            fontWeight: 700,
            letterSpacing: '-0.02em',
          }}
        >
          Build interfaces that feel intentional
        </h1>
        <p style={{ margin: '0 auto 24px', maxWidth: '520px', color: tokens.textSecondary, lineHeight: 1.6 }}>
          Start from this scaffold. Extend Card, Button, and Input primitives. Match Ocean.studio tokens.
        </p>
        <div style={{ display: 'flex', gap: '12px', justifyContent: 'center', flexWrap: 'wrap' }}>
          <Button icon={<ArrowRight size={16} />}>Get started</Button>
          <Button variant="ghost">View docs</Button>
        </div>
      </header>

      {/* Search + grid */}
      <main style={{ padding: '0 24px 48px', maxWidth: '960px', margin: '0 auto' }}>
        <div style={{ marginBottom: '32px', maxWidth: '400px' }}>
          <Input placeholder="Search components…" value={query} onChange={setQuery} icon={<Search size={16} />} />
        </div>

        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
            gap: '16px',
          }}
        >
          {features.map((f) => (
            <Card key={f.title} title={f.title} subtitle={f.desc}>
              <ul style={{ margin: 0, padding: 0, listStyle: 'none' }}>
                {['Spacing', 'Typography', 'States'].map((item) => (
                  <li
                    key={item}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '8px',
                      padding: '8px 0',
                      fontSize: '0.875rem',
                      color: tokens.textSecondary,
                      borderBottom: `1px solid ${tokens.borderSubtle}`,
                    }}
                  >
                    <Check size={14} color="#059669" />
                    {item}
                  </li>
                ))}
              </ul>
            </Card>
          ))}
        </div>
      </main>
    </div>
  );
}
