/** Read-only 5-star display. `value` is 0–5 (fractions rounded to nearest star). */
export default function Stars({ value = 0, size = 14 }) {
  const filled = Math.round(Number(value) || 0);
  return (
    <span className="stars" role="img" aria-label={`Rated ${Number(value || 0).toFixed(1)} out of 5`}>
      {[1, 2, 3, 4, 5].map((i) => (
        <svg
          key={i}
          width={size}
          height={size}
          viewBox="0 0 24 24"
          className={i <= filled ? 'star is-filled' : 'star'}
          fill={i <= filled ? 'currentColor' : 'none'}
          stroke="currentColor"
          strokeWidth="1.7"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <path d="M12 2.8l2.85 5.9 6.45.9-4.7 4.5 1.15 6.4L12 17.5l-5.75 3-1.15-6.4-4.7-4.5 6.45-.9z" />
        </svg>
      ))}
    </span>
  );
}
