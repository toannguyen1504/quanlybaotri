CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE labor_rates
ADD CONSTRAINT labor_rates_no_overlapping_active_periods
EXCLUDE USING gist (
    COALESCE(technician_id, '00000000-0000-0000-0000-000000000000'::uuid) WITH =,
    tstzrange(effective_from, COALESCE(effective_to, 'infinity'::timestamptz), '[)') WITH &&
)
WHERE (active);
