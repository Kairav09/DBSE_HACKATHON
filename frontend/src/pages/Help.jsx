import { useState } from 'react';

const FAQS = [
  {
    q: 'Why was my booking rejected with “already booked for the selected time window”?',
    a: 'That is the double-booking protection working. Every booking runs a transactional overlap check on the server: if the same slot already has a RESERVED or OCCUPIED reservation that overlaps your start–end window, the API returns 409 Conflict. Pick another slot, or a window that does not overlap — the slot map marks slots booked inside your selected window with BOOKED*.',
  },
  {
    q: 'How is my fee calculated?',
    a: 'Fee = billable hours × the location\u2019s hourly rate. Billable hours are your booked duration rounded up to the next whole hour, with a minimum of 1 hour. The rate is snapshotted onto the reservation when you book, so a later price change never alters an existing booking.',
  },
  {
    q: 'What is surge pricing — why is my total higher than hours × rate?',
    a: 'When the predicted occupancy for a location at your start hour is high, a surge multiplier is applied to the base fee: ×1.25 when predicted occupancy is at least 70%, and ×1.50 at 85% or more (labelled “High demand surge” / “Peak demand surge”). Below 70% there is no surge. The booking panel shows the full quote — base, surge with its reason, any promo discount, and the total — before you confirm, and the server recomputes it all itself.',
  },
  {
    q: 'How do promo codes work?',
    a: 'Type a code in the booking panel and press Apply: the quote endpoint checks it and shows the discount line before you book. Codes are either a percentage off the surged price (try WELCOME10 for 10% off) or a flat amount off (try FLAT50 for ₹50 off). A code stops applying once it expires, is deactivated by an admin, or reaches its maximum number of uses — the panel tells you why a code did not apply.',
  },
  {
    q: 'The window I want is fully booked. What is the waitlist?',
    a: 'Join the waitlist from the booking panel for that exact window. If a booking covering it is cancelled, the earliest matching waitlist entry is offered the freed slot and the driver gets a notification. The offer is held for about 15 minutes: claim it from My Waitlist (or the notification) to turn it into a reservation. Unclaimed offers expire and the next entry in line is promoted automatically.',
  },
  {
    q: 'Is the payment real money?',
    a: 'No — payments in this app are simulated, and the UI says so. “Pay now” marks the booking\u2019s payment as PAID with a generated transaction reference (SIMULATED_CARD), purely to demonstrate the payment flow and states (PENDING → PAID, or REFUNDED if a paid booking is cancelled). No card is charged and no payment gateway is involved.',
  },
  {
    q: 'When can I review a location?',
    a: 'After a booking is COMPLETED (you entered and exited), My Reservations offers “Write a review” for it — one review per booking, 1–5 stars plus an optional comment. Reviews appear on the location page, and the location\u2019s average rating shows on its card. Submitting a second review for the same booking is rejected.',
  },
  {
    q: 'What happens if I never show up for a booking?',
    a: 'A RESERVED booking whose start time passes by more than 30 minutes without an entry is automatically marked NO_SHOW by a background sweep, and you get a notification. A no-show is final — book a new slot if you still need parking.',
  },
  {
    q: 'When can I cancel a reservation?',
    a: 'Only a RESERVED booking can be cancelled — that is, before you record entry. Once a reservation is OCCUPIED (you have entered) it can no longer be cancelled; record Exit instead. COMPLETED and CANCELLED reservations are final.',
  },
  {
    q: 'What do Entry and Exit actually do?',
    a: 'Entry moves a RESERVED reservation to OCCUPIED and stamps the entry time; the bay turns red on the live map. Exit moves an OCCUPIED reservation to COMPLETED, stamps the exit time, and frees the bay. In this MVP these buttons stand in for the gate operator\u2019s check-in — there is no camera or plate recognition.',
  },
  {
    q: 'Where does the occupancy prediction come from?',
    a: 'A Python service analyses the reservation history for a location and predicts occupancy for each hour, including the peak hour. The browser only ever talks to the Spring Boot backend; the backend calls the Python service. If Python is unreachable, the backend falls back to the same calculation in Java, so the panel keeps working.',
  },
  {
    q: 'I am a judge — how do I try everything quickly?',
    a: 'Use the demo accounts on the sign-in page (one click fills them): user1@example.com / user123 for the driver flow, and admin@smartparking.local / admin123 for the dashboard, analytics and management. The fastest backend setup is the dev profile described in the README — it seeds all demo data automatically.',
  },
  {
    q: 'Why can\u2019t the admin account book a slot?',
    a: 'By design: booking and vehicle endpoints are USER-only on the backend, and the API enforces roles with 403 responses. Operators manage supply (locations, slots) and oversight (dashboard, analytics); drivers do the booking.',
  },
];

function FaqItem({ item, open, onToggle }) {
  return (
    <div className={`faq-item ${open ? 'is-open' : ''}`}>
      <button className="faq-q" onClick={onToggle} aria-expanded={open}>
        <span>{item.q}</span>
        <svg className="faq-chevron" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M6 9l6 6 6-6" />
        </svg>
      </button>
      <div className="faq-a">
        <div className="faq-a-inner"><p>{item.a}</p></div>
      </div>
    </div>
  );
}

export default function Help() {
  const [openIdx, setOpenIdx] = useState(0);

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h2>Help &amp; how it works</h2>
          <p className="muted">The booking lifecycle, the fee rule, and answers to common questions.</p>
        </div>
      </div>

      <section className="panel">
        <h3>The life of a booking</h3>
        <div className="lifecycle" role="img" aria-label="Slot lifecycle: Available to Reserved to Occupied to Available, with Cancelled as an exit from Reserved">
          <span className="lc-step lc-available">AVAILABLE</span>
          <span className="lc-arrow">→</span>
          <span className="lc-step lc-reserved">RESERVED</span>
          <span className="lc-arrow">→</span>
          <span className="lc-step lc-occupied">OCCUPIED</span>
          <span className="lc-arrow">→</span>
          <span className="lc-step lc-available">AVAILABLE</span>
        </div>
        <p className="muted small lifecycle-note">
          A slot&apos;s status is derived from its reservations — it is never stored. From RESERVED, a booking
          can instead be <strong className="ink">CANCELLED</strong>, which frees the slot immediately.
          OCCUPIED ends as COMPLETED after Exit.
        </p>
      </section>

      <section className="panel">
        <h3>The fee rule, with an example</h3>
        <p className="muted">
          Billable hours (duration rounded up, minimum 1) × the location&apos;s hourly rate, fixed at booking time.
        </p>
        <div className="fee-example">
          <span>10:00 → 12:00 at KL University Main Gate Parking</span>
          <strong>2 hrs × ₹30 = ₹60</strong>
        </div>
        <p className="muted small">Book 10:00 → 11:20 and you still pay for 2 billable hours — partial hours round up. At busy hours a surge multiplier can apply on top, and promo codes take money back off — the booking panel&apos;s quote shows every line before you confirm (see the FAQ below).</p>
      </section>

      <section className="panel">
        <h3>Frequently asked questions</h3>
        <div className="faq-list">
          {FAQS.map((item, i) => (
            <FaqItem
              key={item.q}
              item={item}
              open={openIdx === i}
              onToggle={() => setOpenIdx(openIdx === i ? -1 : i)}
            />
          ))}
        </div>
      </section>
    </div>
  );
}
