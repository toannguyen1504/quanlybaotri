\set ON_ERROR_STOP on

SELECT 'customers', count(*)::text,
       md5(coalesce(string_agg(md5(row_to_json(t)::text), '' ORDER BY id::text), ''))
FROM customers t
UNION ALL
SELECT 'customer_sites', count(*)::text,
       md5(coalesce(string_agg(md5(row_to_json(t)::text), '' ORDER BY id::text), ''))
FROM customer_sites t
UNION ALL
SELECT 'regions', count(*)::text,
       md5(coalesce(string_agg(md5(row_to_json(t)::text), '' ORDER BY id::text), ''))
FROM regions t
UNION ALL
SELECT 'flyway_schema_history', count(*)::text,
       md5(coalesce(string_agg(md5(row_to_json(t)::text), '' ORDER BY installed_rank), ''))
FROM flyway_schema_history t
UNION ALL
SELECT 'orphan_customer_sites_customer', count(*)::text, md5(count(*)::text)
FROM customer_sites s
LEFT JOIN customers c ON c.id = s.customer_id
WHERE c.id IS NULL
UNION ALL
SELECT 'orphan_customer_sites_region', count(*)::text, md5(count(*)::text)
FROM customer_sites s
LEFT JOIN regions r ON r.id = s.region_id
WHERE s.region_id IS NOT NULL AND r.id IS NULL
ORDER BY 1;
