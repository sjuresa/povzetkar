UPDATE topics
SET enabled = TRUE
WHERE name = 'Science & Discovery'
  AND user_id = (SELECT id FROM users WHERE email = 'demo@povzetkar.local');
