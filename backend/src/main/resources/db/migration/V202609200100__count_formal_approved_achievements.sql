-- Passing the second-round formal review completes the indicator. The third
-- round remains available for later publication/grant supplement materials.
UPDATE achievement
SET counts_to_indicator = 1
WHERE status IN (
  'WAIT_PUBLICATION',
  'WAIT_GRANT',
  'SUPPLEMENT_INITIAL',
  'SUPPLEMENT_FINAL',
  'SUPPLEMENT_RETURNED'
);
