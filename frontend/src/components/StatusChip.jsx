export default function StatusChip({ status }) {
  return <span className={`chip chip-${status.toLowerCase()}`}>{status}</span>;
}
