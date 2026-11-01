export default function LogoMark({ size = 30 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true">
      <rect width="32" height="32" rx="7" fill="#f2c230" />
      <path d="M11 24V8h6.2a5.4 5.4 0 0 1 0 10.8H14V24h-3zm3-8.4h3a2.6 2.6 0 0 0 0-5.2h-3v5.2z" fill="#16191d" />
    </svg>
  );
}
