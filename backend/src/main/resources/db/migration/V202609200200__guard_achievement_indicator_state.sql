-- Recognition starts after the second-round formal review. Repair any legacy or
-- manually edited rows before enforcing the invariant at the database boundary.
UPDATE achievement
SET counts_to_indicator = 0
WHERE counts_to_indicator = 1
  AND status NOT IN (
    'WAIT_PUBLICATION',
    'WAIT_GRANT',
    'SUPPLEMENT_INITIAL',
    'SUPPLEMENT_FINAL',
    'SUPPLEMENT_RETURNED',
    'EFFECTIVE'
  );

ALTER TABLE achievement
  ADD CONSTRAINT chk_achievement_indicator_state
  CHECK (
    counts_to_indicator = 0
    OR status IN (
      'WAIT_PUBLICATION',
      'WAIT_GRANT',
      'SUPPLEMENT_INITIAL',
      'SUPPLEMENT_FINAL',
      'SUPPLEMENT_RETURNED',
      'EFFECTIVE'
    )
  );
