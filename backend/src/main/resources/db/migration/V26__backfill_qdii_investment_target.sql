UPDATE fund
SET investment_target = 'QDII'
WHERE investment_target IS NULL
  AND fund_name ILIKE '%QDII%';
